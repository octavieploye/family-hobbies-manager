package com.familyhobbies.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter (API Gateway).
 *
 * Story: S1-004 — Implement Gateway JWT Authentication Filter
 * Tests: 6 test methods (5 from sprint spec + 1 from review finding F-08)
 *
 * These tests verify the six critical behaviors:
 * 1. Valid token -> calls validateToken and getRolesFromClaims (renamed per F-01, H-006)
 * 2. Expired token -> 401 with "Token expired"
 * 3. Invalid token -> 401 with "Invalid token"
 * 4. Missing Authorization header on protected path -> 401
 * 5. Public path -> bypass authentication (no token needed)
 * 6. Non-Bearer auth scheme -> 401 (added per F-08)
 *
 * Uses MockServerWebExchange (Spring WebFlux test utility) and Mockito
 * to mock JwtTokenProvider.
 *
 * Review findings incorporated:
 * - F-01 (BLOCKER): Renamed first test from validToken_shouldForwardRequestWithUserHeaders
 *   to validToken_shouldCallValidateAndExtractRoles because MockServerWebExchange does not
 *   support mutate() returning another MockServerWebExchange, so we can only verify method
 *   calls, not mutated headers. Added TODO for integration test.
 * - F-08 (WARNING): Added nonBearerAuthScheme_shouldReturn401 test for Authorization: Basic.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider);
    }

    /**
     * Test 1: A valid JWT token should result in the filter calling validateToken
     * and getRolesFromToken, and NOT setting a 401 status.
     *
     * RENAMED per F-01 (BLOCKER): Was "validToken_shouldForwardRequestWithUserHeaders".
     * Since MockServerWebExchange does not support mutate() returning another
     * MockServerWebExchange, we can only verify via the token provider calls.
     *
     * TODO: Add integration test using WebTestClient with @SpringBootTest that sends
     * a real request through the filter chain and inspects the forwarded headers on
     * a downstream mock endpoint.
     */
    @Test
    @DisplayName("should call validateToken and extractRoles for valid token")
    void validToken_shouldCallValidateAndExtractRoles() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/families/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Mock JwtTokenProvider to return valid claims
        Claims claims = Jwts.claims().subject("42").build();
        when(jwtTokenProvider.validateToken("valid-token")).thenReturn(claims);
        when(jwtTokenProvider.getRolesFromClaims(claims)).thenReturn(List.of("FAMILY"));

        WebFilterChain chain = filterExchange -> Mono.empty();

        // when
        filter.filter(exchange, chain).block();

        // then — verify that validateToken and getRolesFromClaims were called (H-006: single parse)
        verify(jwtTokenProvider).validateToken("valid-token");
        verify(jwtTokenProvider).getRolesFromClaims(claims);

        // The filter should NOT have set a 401 status
        assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    // ── C-001 / C-002 fix: SecurityContext population tests ──────────────────

    /**
     * Test 2: An expired JWT token should return a 401 Unauthorized response.
     */
    @Test
    @DisplayName("should return 401 when token is expired")
    void expiredToken_shouldReturn401() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/families/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Mock JwtTokenProvider to throw ExpiredJwtException
        when(jwtTokenProvider.validateToken("expired-token"))
            .thenThrow(new ExpiredJwtException(null, null, "Token has expired"));

        // The chain should never be called
        WebFilterChain chain = filterExchange -> {
            fail("Filter chain should not be invoked for expired tokens");
            return Mono.empty();
        };

        // when
        filter.filter(exchange, chain).block();

        // then
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    /**
     * Test 3: An invalid JWT token (bad signature, malformed) should return 401.
     */
    @Test
    @DisplayName("should return 401 when token is invalid (bad signature)")
    void invalidToken_shouldReturn401() {
        // given
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/families/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Mock JwtTokenProvider to throw JwtException (bad signature)
        when(jwtTokenProvider.validateToken("invalid-token"))
            .thenThrow(new JwtException("Invalid JWT signature"));

        WebFilterChain chain = filterExchange -> {
            fail("Filter chain should not be invoked for invalid tokens");
            return Mono.empty();
        };

        // when
        filter.filter(exchange, chain).block();

        // then
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    /**
     * Test 4: A request to a protected path without an Authorization header
     * should return 401.
     */
    @Test
    @DisplayName("should return 401 when Authorization header is missing on protected path")
    void missingAuthHeader_shouldReturn401ForProtectedPath() {
        // given — no Authorization header
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/families/1")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = filterExchange -> {
            fail("Filter chain should not be invoked when Authorization header is missing");
            return Mono.empty();
        };

        // when
        filter.filter(exchange, chain).block();

        // then
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());

        // Verify JwtTokenProvider was never called
        verifyNoInteractions(jwtTokenProvider);
    }

    /**
     * Test 5: A request to a public path should bypass authentication entirely.
     */
    @Test
    @DisplayName("should bypass authentication for public paths")
    void publicPath_shouldBypassAuthentication() {
        // given — public path, no token
        MockServerHttpRequest request = MockServerHttpRequest
            .post("/api/v1/auth/login")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // Track whether chain.filter() was called
        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);
        WebFilterChain chain = filterExchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        // when
        filter.filter(exchange, chain).block();

        // then — chain was invoked (request passed through)
        assertTrue(chainCalled.get(), "Filter chain should be invoked for public paths");

        // JwtTokenProvider should never be called for public paths
        verifyNoInteractions(jwtTokenProvider);

        // No error status should be set
        assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    /**
     * Test 6 (F-08): A request with a non-Bearer auth scheme (e.g., Basic) should
     * be treated as missing/invalid and return 401.
     */
    @Test
    @DisplayName("should return 401 when Authorization uses non-Bearer scheme")
    void nonBearerAuthScheme_shouldReturn401() {
        // given — Authorization header with Basic scheme instead of Bearer
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/families/1")
            .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
            .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        WebFilterChain chain = filterExchange -> {
            fail("Filter chain should not be invoked for non-Bearer auth scheme");
            return Mono.empty();
        };

        // when
        filter.filter(exchange, chain).block();

        // then
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());

        // JwtTokenProvider should never be called for non-Bearer tokens
        verifyNoInteractions(jwtTokenProvider);
    }

    // ── C-001 / C-002 fix: SecurityContext population tests ──────────────────

    /**
     * Tests that verify the filter populates ReactiveSecurityContextHolder
     * so that Spring Security RBAC rules in SecurityConfig are enforced.
     * These tests address critical issues C-001 and C-002.
     */
    @Nested
    @DisplayName("SecurityContext population (C-001/C-002 fix)")
    class SecurityContextPopulationTests {

        /**
         * Test 7 (C-001): Valid token with single role should populate SecurityContext
         * with an Authentication containing the correct principal and ROLE_-prefixed authority.
         */
        @Test
        @DisplayName("should populate SecurityContext with Authentication for valid token (single role)")
        void validToken_shouldPopulateSecurityContextWithSingleRole() {
            // given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/families/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            Claims claims = Jwts.claims().subject("42").build();
            when(jwtTokenProvider.validateToken("valid-token")).thenReturn(claims);
            when(jwtTokenProvider.getRolesFromClaims(claims)).thenReturn(List.of("FAMILY"));

            // Capture the SecurityContext from within the reactive chain
            AtomicReference<SecurityContext> capturedContext = new AtomicReference<>();

            WebFilterChain chain = filterExchange ->
                ReactiveSecurityContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            // when
            filter.filter(exchange, chain).block();

            // then — SecurityContext should be populated
            assertNotNull(capturedContext.get(), "SecurityContext should be populated");

            Authentication auth = capturedContext.get().getAuthentication();
            assertNotNull(auth, "Authentication should not be null");
            assertTrue(auth.isAuthenticated(), "Authentication should be marked as authenticated");
            assertEquals("42", auth.getPrincipal(), "Principal should be the user ID from JWT subject");
            assertNull(auth.getCredentials(), "Credentials should be null (token already validated)");

            // Verify ROLE_FAMILY authority is present
            assertTrue(
                auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_FAMILY")),
                "Should contain ROLE_FAMILY authority"
            );
            assertEquals(1, auth.getAuthorities().size(), "Should have exactly 1 authority");
        }

        /**
         * Test 8 (C-001): Valid token with multiple roles should populate SecurityContext
         * with all ROLE_-prefixed authorities.
         */
        @Test
        @DisplayName("should populate SecurityContext with multiple ROLE_-prefixed authorities")
        void validToken_shouldPopulateSecurityContextWithMultipleRoles() {
            // given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/families/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer multi-role-token")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            Claims claims = Jwts.claims().subject("99").build();
            when(jwtTokenProvider.validateToken("multi-role-token")).thenReturn(claims);
            when(jwtTokenProvider.getRolesFromClaims(claims))
                .thenReturn(List.of("FAMILY", "ASSOCIATION", "ADMIN"));

            AtomicReference<SecurityContext> capturedContext = new AtomicReference<>();

            WebFilterChain chain = filterExchange ->
                ReactiveSecurityContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            // when
            filter.filter(exchange, chain).block();

            // then
            assertNotNull(capturedContext.get(), "SecurityContext should be populated");
            Authentication auth = capturedContext.get().getAuthentication();
            assertNotNull(auth, "Authentication should not be null");
            assertEquals("99", auth.getPrincipal(), "Principal should be user ID 99");

            // Verify all three ROLE_-prefixed authorities
            assertEquals(3, auth.getAuthorities().size(), "Should have 3 authorities");
            assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_FAMILY")),
                "Should contain ROLE_FAMILY");
            assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ASSOCIATION")),
                "Should contain ROLE_ASSOCIATION");
            assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")),
                "Should contain ROLE_ADMIN");
        }

        /**
         * Test 9 (C-002): Verifies that hasRole("ADMIN") in SecurityConfig can match
         * because the filter sets ROLE_ADMIN authority (not just "ADMIN").
         * Spring Security's hasRole("ADMIN") checks for "ROLE_ADMIN" internally.
         */
        @Test
        @DisplayName("should prefix roles with ROLE_ so hasRole() works in SecurityConfig")
        void validToken_authoritiesShouldUseRolePrefix() {
            // given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            Claims claims = Jwts.claims().subject("1").build();
            when(jwtTokenProvider.validateToken("admin-token")).thenReturn(claims);
            when(jwtTokenProvider.getRolesFromClaims(claims)).thenReturn(List.of("ADMIN"));

            AtomicReference<SecurityContext> capturedContext = new AtomicReference<>();

            WebFilterChain chain = filterExchange ->
                ReactiveSecurityContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            // when
            filter.filter(exchange, chain).block();

            // then — authority must be "ROLE_ADMIN", not "ADMIN"
            Authentication auth = capturedContext.get().getAuthentication();
            boolean hasRolePrefixedAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            assertTrue(hasRolePrefixedAdmin,
                "Authority must be ROLE_ADMIN (with prefix) for hasRole('ADMIN') to work");

            boolean hasUnprefixedAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN"));
            assertFalse(hasUnprefixedAdmin,
                "Authority must NOT be bare 'ADMIN' without ROLE_ prefix");
        }

        /**
         * Test 10: Expired token should NOT populate SecurityContext.
         */
        @Test
        @DisplayName("should not populate SecurityContext when token is expired")
        void expiredToken_shouldNotPopulateSecurityContext() {
            // given
            MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/families/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            when(jwtTokenProvider.validateToken("expired-token"))
                .thenThrow(new ExpiredJwtException(null, null, "Token has expired"));

            AtomicReference<SecurityContext> capturedContext = new AtomicReference<>();

            // Chain should NOT be called for expired tokens, but if it were,
            // SecurityContext should be empty
            WebFilterChain chain = filterExchange ->
                ReactiveSecurityContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then();

            // when
            filter.filter(exchange, chain).block();

            // then — SecurityContext should not have been captured (chain not called)
            assertNull(capturedContext.get(),
                "SecurityContext should not be populated for expired tokens");
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }

        /**
         * Test 11: Public paths should not populate SecurityContext.
         */
        @Test
        @DisplayName("should not populate SecurityContext for public paths")
        void publicPath_shouldNotPopulateSecurityContext() {
            // given
            MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/v1/auth/login")
                .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            AtomicReference<SecurityContext> capturedContext = new AtomicReference<>();

            WebFilterChain chain = filterExchange ->
                ReactiveSecurityContextHolder.getContext()
                    .doOnNext(capturedContext::set)
                    .then(Mono.empty());

            // when
            filter.filter(exchange, chain).block();

            // then — no authentication was set, so context retrieval should yield nothing
            assertNull(capturedContext.get(),
                "SecurityContext should not be populated for public paths (no token processed)");
            verifyNoInteractions(jwtTokenProvider);
        }
    }
}
