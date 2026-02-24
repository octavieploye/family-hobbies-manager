# Sprint 1 -- Test Architect Review

**Reviewer**: Amelia (Senior Test Architect)
**Date**: 2026-02-23
**Sprint**: Sprint 1 -- Security & Authentication
**Document reviewed**: `sprint-1-security.md`

---

## Summary

Sprint 1 defines 62 backend tests and 16 frontend tests across 13 test classes covering JWT token generation, user registration, authentication flows, gateway JWT filtering, downstream user context propagation, Angular auth scaffolding, Kafka domain events, and CORS configuration. The test contracts are generally well-structured with clear TDD intent, appropriate isolation levels, and comprehensive coverage of critical security paths. However, there are several architecture-level discrepancies between the tests and the reference documents (API contracts, security architecture), a handful of missing edge-case scenarios, and one structural gap in the gateway filter's header-forwarding assertion that should be addressed before implementation begins.

---

## Test Inventory

| Story | Test Class | Location | # Tests | Coverage Assessment |
|-------|-----------|----------|---------|---------------------|
| S1-001 | `JwtTokenProviderTest` | `user-service` | 8 | Good -- covers generate, validate, reject expired, reject tampered, extract claims, unique refresh tokens |
| S1-002 | `UserRegistrationTest` | `user-service` | 7 | Good -- covers happy path, duplicate email, invalid email, blank password, hashing, role assignment, refresh token persistence |
| S1-003 | `AuthFlowTest` | `user-service` | 9 | Good -- covers login (4), refresh with rotation (4), logout (1) |
| S1-004 | `JwtAuthenticationFilterTest` | `api-gateway` | 5 | Adequate -- covers valid/expired/invalid tokens, missing header, public path bypass; header forwarding assertion is weak |
| S1-005 | `UserContextFilterTest` | `common` | 5 | Good -- covers population, SecurityContext, cleanup, missing headers, multi-role parsing |
| S1-005 | `UserContextTest` | `common` | 5 | Good -- covers set/get, throw-if-not-set, clear, hasRole, isAdmin |
| S1-006 | `auth.service.spec.ts` | `frontend` | 5 | Good -- covers login, register, getAccessToken, isAuthenticated, logout |
| S1-006 | `jwt.interceptor.spec.ts` | `frontend` | 4 | Good -- covers token attachment, public path skip, refresh on 401, logout on refresh failure |
| S1-006 | `auth.guard.spec.ts` | `frontend` | 2 | Minimal -- covers allow and redirect; missing role-based guard scenarios |
| S1-006 | `auth.reducer.spec.ts` | `frontend` | 5 | Good -- covers initial state, login success, login failure, logout, init auth |
| S1-007 | `DomainEventTest` | `common` | 5 | Good -- covers event type, auto-generated ID, timestamp, deletion event, JSON serialization round-trip |
| S1-008 | `CorsConfigTest` | `api-gateway` | 2 | Adequate -- covers preflight OPTIONS and cross-origin GET; missing disallowed-origin test |
| | | **TOTAL** | **62 backend + 16 frontend = 78** | |

---

## Severity Legend

| Severity | Meaning |
|----------|---------|
| BLOCKER | Test contract is incorrect, will produce a false positive or false negative, or contradicts architecture documents in a way that will cause implementation bugs |
| WARNING | Test gap or structural issue that should be fixed before implementation but does not invalidate existing tests |
| NOTE | Observation, improvement suggestion, or minor inconsistency that can be addressed during implementation |

---

## Findings

| # | Severity | Story | Test Class | Finding | Recommendation |
|---|----------|-------|-----------|---------|----------------|
| F-01 | BLOCKER | S1-004 | `JwtAuthenticationFilterTest` | `validToken_shouldForwardRequestWithUserHeaders` cannot actually verify that X-User-Id and X-User-Roles headers are added to the mutated exchange. The test comment explicitly acknowledges this: "Since MockServerWebExchange does not support mutate() returning another MockServerWebExchange, we verify via the token provider calls." The test name promises header forwarding verification but delivers only method-call verification. This is a false positive -- the filter could call `validateToken` and `getRolesFromToken` without actually mutating the exchange headers, and this test would still pass. | Rename the test to `validToken_shouldCallValidateAndExtractRoles` to match what it actually verifies, **and** add a separate integration test (using `WebTestClient` with `@SpringBootTest`) that sends a real request through the filter chain and inspects the forwarded headers on a downstream mock endpoint. Alternatively, capture the `ServerWebExchange` passed to `chain.filter()` by accepting it in the lambda and inspecting its `getRequest().getHeaders()`. |
| F-02 | BLOCKER | S1-002, S1-003 | `UserRegistrationTest`, `AuthFlowTest` | API contracts document (`03-api-contracts.md`, section 7.1) specifies that `POST /api/v1/auth/register` returns a **201 Created** with a **user profile** response body (`id`, `email`, `firstName`, `lastName`, `phone`, `role`, `status`, `emailVerified`, `createdAt`) plus a `Location` header. However, the tests expect an `AuthResponse` body (`accessToken`, `refreshToken`, `tokenType`, `expiresIn`). This is a contract discrepancy -- either the API contracts document or the tests are wrong. | Decide on one contract and update the other. The sprint-1 approach (returning AuthResponse from register) is more practical for frontend UX (user is immediately logged in), but the API contracts document must be updated to match. Add a note in the test file referencing the decision. |
| F-03 | WARNING | S1-003 | `AuthFlowTest` | `logout_shouldRevokeAllRefreshTokens` sends `X-User-Id` as a raw header without JWT authentication. In the real architecture, the logout endpoint requires authentication (`Auth: FAMILY, ASSOCIATION, ADMIN` per API contracts). The test bypasses this by directly setting the header, which is valid for integration testing (no gateway), but the test does not document why this works. More critically, the API contracts say logout expects a `refreshToken` in the request body, but the test sends no body -- only the `X-User-Id` header. | Either (a) update the test to send a request body with the refresh token matching the API contract, or (b) document that the sprint-1 implementation uses a different logout contract (revoke all tokens for user ID from header). Also add a comment explaining that the X-User-Id header simulates what the gateway would inject. |
| F-04 | WARNING | S1-001 | `JwtTokenProviderTest` | The test instantiates `JwtTokenProvider` with a single constructor argument `new JwtTokenProvider(TEST_SECRET)`, but the implementation (shown in the sprint doc) requires additional configuration for access token validity and refresh token validity. The constructor should accept or default these values. If the implementation uses `@Value` annotations or `@ConfigurationProperties`, the test's direct instantiation approach will fail. | Ensure the `JwtTokenProvider` class supports direct instantiation for unit testing (e.g., constructor with defaults or a test-friendly constructor). Document the expected constructor signature in the test's class-level Javadoc. |
| F-05 | WARNING | Cross-doc | All | Refresh token validity is **7 days** in `05-security-architecture.md` (line 36, line 623, line 1582) and in `sprint-1-security.md` (line 298: `REFRESH_TOKEN_VALIDITY_MS = 604_800_000 // 7 days`). However, `03-api-contracts.md` (line 436) says refresh tokens are "valid for **30 days**". The tests do not explicitly assert the refresh token duration (they only assert `expiresAt` is in the future). | Update `03-api-contracts.md` to say 7 days to match the security architecture and implementation. Consider adding a test in `UserRegistrationTest` or `AuthFlowTest` that asserts the refresh token expiry is approximately 7 days from now (within a tolerance window). |
| F-06 | WARNING | S1-002 | `UserRegistrationTest` | No test for password complexity validation. The API contracts (line 363) specify: "Min 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char." The test `register_blankPassword_shouldReturn400` only tests a blank password. A password like `"weakpass"` (no uppercase, no digit, no special) would not be caught by this test. | Add tests: `register_shortPassword_shouldReturn400` (e.g., `"Sh@1"`), `register_noUppercase_shouldReturn400` (e.g., `"secure@1pass"`), and `register_noSpecialChar_shouldReturn400` (e.g., `"SecurePass1"`). At minimum, add one test with a password that satisfies length but fails complexity. |
| F-07 | WARNING | S1-006 | `auth.guard.spec.ts` | Only 2 tests for the auth guard. Missing scenario: role-based access. The guard only checks `isAuthenticated()`, but the architecture mentions role-based guards (FAMILY, ASSOCIATION, ADMIN). If role-based guarding is deferred to a later sprint, document that explicitly. | Add a note that role-based route guarding is deferred. If it is in scope for Sprint 1, add tests for `shouldDenyUserWithInsufficientRole` and `shouldAllowUserWithCorrectRole`. |
| F-08 | WARNING | S1-004 | `JwtAuthenticationFilterTest` | No test for a request with `Authorization` header that does not start with `"Bearer "`. For example, `Authorization: Basic dXNlcjpwYXNz` or `Authorization: Token abc`. The filter should treat these as missing/invalid tokens. | Add test: `nonBearerAuthScheme_shouldReturn401` with `Authorization: Basic dXNlcjpwYXNz`. |
| F-09 | NOTE | S1-001 | `JwtTokenProviderTest` | `shouldRejectTamperedToken` changes only the last character of the signature. While this works for most JWT libraries, it is worth noting that base64url padding means some character swaps might produce a different valid base64 decoding that still fails signature validation. The test is correct but could be more explicit about what constitutes "tampering." | No change needed. Test is valid as-is. |
| F-10 | NOTE | S1-003 | `AuthFlowTest` | `login_inactiveUser_shouldReturn401` sets user to `INACTIVE` status. The API contracts (line 445) say inactive/suspended accounts should return **403 Forbidden**, not 401. This is a semantic difference: 401 means "you are not who you say you are" (identity failure), 403 means "you are who you say you are but you cannot do this" (authorization failure). | Align the expected status code with the API contract. If 401 is the intentional design choice (to avoid leaking account status), document the rationale. |
| F-11 | NOTE | S1-005 | `UserContextFilterTest` | `shouldPopulateUserContextFromHeaders` uses a custom `FilterChain` lambda instead of `MockFilterChain`. This is fine and actually preferable for capturing internal state, but it is inconsistent with the `@BeforeEach` which creates a `MockFilterChain` (unused in most tests). | Remove the unused `MockFilterChain` import and field, or use it consistently across tests where state capture is not needed. |
| F-12 | NOTE | S1-006 | `auth.service.spec.ts` | The test `logout_shouldClearLocalStorage` verifies `routerSpy.navigate` was called with `['/auth/login']`, but does not verify that the backend logout endpoint (`POST /api/v1/auth/logout`) is called. If the frontend should also call the backend to revoke refresh tokens, this test is incomplete. | If frontend logout should call the backend, add an `httpMock.expectOne('/api/v1/auth/logout')` assertion. If it is client-side only (clear localStorage), document that decision. |
| F-13 | NOTE | S1-006 | `jwt.interceptor.spec.ts` | `shouldAttemptRefreshOn401` depends on timing. After the first request returns 401, the interceptor calls `authServiceSpy.refreshToken()`, which returns a mocked `of(mockRefreshResponse)`. Then the interceptor retries. In Angular, this works because the interceptor uses `catchError` + `switchMap`. But the test does not verify that `authServiceSpy.storeTokens` is called with the new tokens after a successful refresh. | Add assertion: `expect(authServiceSpy.storeTokens).toHaveBeenCalledWith(mockRefreshResponse)`. |
| F-14 | NOTE | S1-007 | `DomainEventTest` | No test for `UserDeletedEvent` JSON serialization round-trip. Only `UserRegisteredEvent` has a serialization test (`eventsShouldSerializeToJson`). | Add a parallel `userDeletedEvent_shouldSerializeToJson` test for completeness. |
| F-15 | NOTE | S1-008 | `CorsConfigTest` | No negative test for a disallowed origin. A request from `http://evil-site.com` should not receive `Access-Control-Allow-Origin`. | Add test: `disallowedOrigin_shouldNotReturnCorsHeaders` with `Origin: http://evil-site.com`, asserting that `Access-Control-Allow-Origin` header is absent or does not match. |
| F-16 | NOTE | S1-008 | `CorsConfigTest` | `crossOriginGet_shouldIncludeCorsHeaders` sends a GET to `/api/v1/auth/login` which is a POST-only endpoint. The test comment acknowledges this ("The actual status depends on the route handler"). The test asserts CORS headers are present regardless of status, which is correct behavior. However, it would be cleaner to use a path that accepts GET. | No change strictly needed, but consider using `/api/v1/auth/register` or a health endpoint if available for clarity. |

---

## Missing Test Scenarios

| # | Story | Missing Scenario | Why It Matters | Priority |
|---|-------|-----------------|----------------|----------|
| M-01 | S1-001 | JWT with `iss` (issuer) claim validation | API contracts (line 191) show `"iss": "family-hobbies-manager"` in JWT payload. No test verifies this claim is set or validated. If a JWT from another system is accepted, it is a security hole. | WARNING |
| M-02 | S1-001 | JWT with `familyId` claim | API contracts (line 188) show `"familyId": 1` in JWT payload. No test verifies this claim is present. Downstream services may rely on it. | NOTE (if familyId is deferred to family management sprint) |
| M-03 | S1-002 | Registration with null/missing firstName or lastName | Only blank password and invalid email are tested as validation failures. Missing required fields should also return 400. | WARNING |
| M-04 | S1-003 | Login request with invalid email format | `login_unknownEmail_shouldReturn401` uses a valid email format that does not exist. No test sends a malformed email to the login endpoint. | NOTE |
| M-05 | S1-003 | Concurrent refresh token usage (replay attack) | If two refresh requests use the same token simultaneously, the second should fail. No test covers this race condition. | NOTE (hard to test without async setup) |
| M-06 | S1-004 | Gateway SecurityConfig bean verification | No test verifies that `SecurityConfig` creates the correct `SecurityWebFilterChain` bean with the expected filter ordering. A misconfigured filter order could bypass JWT validation. | WARNING |
| M-07 | S1-004 | Gateway response body on 401 | Tests check HTTP status is 401 but do not verify the response body contains the expected error message ("Token expired", "Invalid token", "Missing or invalid Authorization header"). The test comments mention expected messages but assertions only check status code. | WARNING |
| M-08 | S1-005 | UserContextFilter exception in chain | If the downstream filter chain throws an exception, does UserContextFilter still clear the ThreadLocal? No test covers the exception-during-processing path. Should use a chain that throws and verify cleanup happens in a `finally` block. | WARNING |
| M-09 | S1-006 | Angular `auth.effects.spec.ts` | NgRx Effects are not tested. The sprint defines actions and reducer but no Effects tests. Effects handle the actual HTTP calls (login/register side effects) and are where most async bugs occur. | WARNING (if Effects are in scope for S1-006) |
| M-10 | S1-008 | CORS with wildcard origin in production | No test verifies that `*` cannot be used as an allowed origin when `allowCredentials=true` (browsers reject this combination). | NOTE |

---

## Test Pattern Compliance

| Criterion | Sprint 0 Pattern (error-handling) | Sprint 1 Pattern | Compliant? | Notes |
|-----------|----------------------------------|-------------------|------------|-------|
| **Naming convention** | `shouldDoSomething` / `conditionShouldDoSomething` | `shouldDoSomething` (S1-001, S1-005, S1-007), `method_condition_shouldResult` (S1-002, S1-003) | Partial | Two naming styles are used. Sprint 0 uses `shouldX`, some Sprint 1 tests use `method_condition_shouldResult`. Both are acceptable, but the project should pick one and be consistent. Recommend standardizing on `method_condition_shouldResult` for integration tests and `shouldDoSomething` for unit tests. |
| **Assertion style** | `assertEquals`, `assertTrue`, `assertNotNull`, `assertThrows` (JUnit 5 standard) | Same (JUnit 5 standard) | Yes | Consistent use of JUnit 5 assertions throughout. No AssertJ or Hamcrest mixing. |
| **Test isolation** | Plain JUnit 5 (no Spring context) for unit tests | Plain JUnit 5 for S1-001, S1-005 UserContextTest, S1-007. Mockito for S1-004. `@SpringBootTest` for S1-002, S1-003, S1-008. | Yes | Correct isolation levels: unit tests without Spring, integration tests with full context. Filter tests use Mockito with mock exchanges. |
| **Setup/teardown** | `@BeforeEach` for object creation | `@BeforeEach` for object creation and DB cleanup. `@AfterEach` for ThreadLocal cleanup. | Yes | Proper lifecycle management. DB cleanup correctly handles FK ordering (refresh tokens before users). |
| **Test data** | Inline test values | Inline test values with French names (Jean Dupont) | Yes | Consistent with project locale (fr-FR). |
| **Error path coverage** | Every exception type tested | Expired token, tampered token, missing header, invalid email, blank password, revoked token, inactive user, missing headers | Yes | Strong error path coverage. |
| **Mock strategy** | No mocks (direct instantiation) | Direct instantiation (S1-001), Mockito (S1-004), `@SpringBootTest` with real DB (S1-002, S1-003), Jest mocks (S1-006) | Yes | Appropriate mock strategy per test type. |
| **Frontend test style** | N/A (no frontend in Sprint 0) | Jest `describe`/`it`/`expect`, `HttpClientTestingModule`, `TestBed.runInInjectionContext()` for functional guards | N/A | Angular 17+ patterns correctly used: standalone component testing, functional interceptor/guard testing, NgRx reducer as pure function. |

---

## Verdict

**APPROVED WITH NOTES**

The test contracts are well-designed and demonstrate sound TDD principles. The 78 tests provide strong coverage of the critical security layer. However, the following items should be resolved before or during implementation:

1. **F-01 (BLOCKER)**: The gateway filter header-forwarding test must be strengthened or renamed. The current test cannot detect a filter that validates the token but forgets to mutate the exchange with forwarded headers. This is the single most critical security test in the sprint.

2. **F-02 (BLOCKER)**: The register endpoint response contract (AuthResponse vs. user profile) must be aligned between the API contracts document and the test expectations. Pick one and update the other.

3. **F-05, F-10 (WARNING)**: Cross-document discrepancies (refresh token validity 7 vs. 30 days, inactive user 401 vs. 403) must be resolved so that tests encode the correct expected behavior.

4. **F-06 (WARNING)**: Password complexity validation tests should be added before implementation, as the current tests would not catch a missing password-strength validator.

5. **M-07, M-08 (WARNING)**: Response body assertions and exception-path cleanup tests should be added to strengthen the gateway and UserContext filter contracts.

All NOTE-level findings can be addressed during implementation without blocking the sprint start.
