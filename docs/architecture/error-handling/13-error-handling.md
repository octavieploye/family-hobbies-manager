# 13 - Error Handling Architecture

> **Family Hobbies Manager** -- Multi-Association Management Platform
> Architecture Document Series | Document 13 of 13

---

## Table of Contents

1. [Overview](#1-overview)
2. [Architecture Decision](#2-architecture-decision)
3. [Module Structure](#3-module-structure)
4. [Exception Taxonomy](#4-exception-taxonomy)
5. [ErrorResponse DTO](#5-errorresponse-dto)
6. [ErrorCode Enum](#6-errorcode-enum)
7. [GlobalExceptionHandler](#7-globalexceptionhandler)
8. [Spring Boot Auto-Configuration](#8-spring-boot-auto-configuration)
9. [Frontend Error Handling](#9-frontend-error-handling)
10. [Test Strategy](#10-test-strategy)
11. [Usage Guidelines](#11-usage-guidelines)
12. [Revision History](#12-revision-history)

---

## 1. Overview

The error handling module is a dedicated, shared Maven module (`backend/error-handling/`) that provides
a consistent, centralized approach to error management across all microservices in the Family Hobbies
Manager platform.

### Goals

- **Consistency**: Every service returns identical error response shapes (same JSON structure,
  same HTTP status mapping). No service can deviate from the contract.
- **Discoverability**: A reviewer opening the repository immediately finds error handling as a
  distinct, self-contained module -- signaling modular architecture competency.
- **Testability**: The module has its own dedicated test suite, independent from service tests.
  44 test cases validate every exception-to-status mapping, serialization shape, and edge case.
- **Auto-configuration**: Services gain error handling by declaring a single dependency on `common`
  (which transitively includes `error-handling`). No manual bean registration required.

### Design Principles

| Principle | Application |
|----|-----|
| Single Responsibility | One module, one concern: error handling |
| Open/Closed | New exception types extend `BaseException` without modifying handler |
| DRY | All services share the same exception classes and handler |
| Fail-Fast | Exceptions carry error codes and structured details for immediate diagnosis |
| Contract-First | `ErrorResponse` defines the API error contract before any service code |

---

## 2. Architecture Decision

### Problem

Where should cross-cutting error handling code live in a microservice architecture?

### Options Considered

| Option | Description | Pros | Cons |
|----|----|----|-----|
| **A** | Everything in `backend/common/` | Simple Maven graph, single dependency | common becomes a dumping ground; every change triggers all-service rebuilds |
| **B** | Dedicated `backend/error-handling/` module | SRP at module level, dedicated test suite, instant discoverability | Adds one Maven module; slightly more complex dependency graph |
| **C** | `backend/common/` with strict sub-packages | Single module, internally organized | Requires discipline; no module boundary enforcement |
| **D** | Layered split (base in common, specifics in each service) | Each service owns domain exceptions | Code duplication risk, inconsistent error shapes |

### Decision: Option B -- Dedicated Module

**Rationale**:

1. **Portfolio signal**: When a reviewer opens the repo, `backend/error-handling/` communicates
   "this person thinks in modules" in 2 seconds.
2. **SRP at module level**: Error handling is a distinct cross-cutting concern that deserves
   its own build, test, and versioning lifecycle.
3. **Dedicated test suite**: 44 tests in `error-handling/src/test/` validate the error contract
   independently from any service. In Option C, these tests mix with event tests, DTO tests, etc.
4. **Enterprise pattern**: In SAFe environments with 50+ developers, each cross-cutting concern
   is its own artifact. This project demonstrates that pattern.

### Dependency Chain

```
error-handling  <--  common  <--  user-service
                              <--  association-service
                              <--  payment-service
                              <--  notification-service
```

Services depend on `common` only. `common` depends on `error-handling`. The transitive dependency
means services get error handling automatically -- zero extra wiring for service developers.

### Maven Configuration

```xml
<!-- backend/error-handling/pom.xml -->
<parent>
    <groupId>com.familyhobbies</groupId>
    <artifactId>family-hobbies-manager</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>

<artifactId>error-handling</artifactId>
<name>Error Handling Module</name>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

```xml
<!-- backend/common/pom.xml (excerpt) -->
<dependency>
    <groupId>com.familyhobbies</groupId>
    <artifactId>error-handling</artifactId>
    <version>${project.version}</version>
</dependency>
```

---

## 3. Module Structure

```
backend/error-handling/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/familyhobbies/errorhandling/
│   │   │   ├── exception/
│   │   │   │   ├── BaseException.java
│   │   │   │   ├── web/
│   │   │   │   │   ├── BadRequestException.java
│   │   │   │   │   ├── UnauthorizedException.java
│   │   │   │   │   ├── ForbiddenException.java
│   │   │   │   │   ├── ResourceNotFoundException.java
│   │   │   │   │   ├── ConflictException.java
│   │   │   │   │   ├── UnprocessableEntityException.java
│   │   │   │   │   └── TooManyRequestsException.java
│   │   │   │   ├── server/
│   │   │   │   │   ├── InternalServerException.java
│   │   │   │   │   ├── BadGatewayException.java
│   │   │   │   │   ├── ServiceUnavailableException.java
│   │   │   │   │   └── GatewayTimeoutException.java
│   │   │   │   └── container/
│   │   │   │       ├── ServiceDiscoveryException.java
│   │   │   │       ├── CircuitBreakerOpenException.java
│   │   │   │       ├── KafkaPublishException.java
│   │   │   │       ├── DatabaseConnectionException.java
│   │   │   │       └── ExternalApiException.java
│   │   │   ├── handler/
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── dto/
│   │   │   │   ├── ErrorResponse.java
│   │   │   │   └── ErrorCode.java
│   │   │   └── config/
│   │   │       └── ErrorHandlingAutoConfiguration.java
│   │   └── resources/
│   │       └── META-INF/
│   │           └── spring/
│   │               └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── test/
│       └── java/com/familyhobbies/errorhandling/
│           ├── exception/
│           │   ├── WebExceptionTest.java
│           │   ├── ServerExceptionTest.java
│           │   └── ContainerExceptionTest.java
│           ├── handler/
│           │   └── GlobalExceptionHandlerTest.java
│           ├── dto/
│           │   ├── ErrorResponseTest.java
│           │   └── ErrorCodeTest.java
│           └── 44 test cases total
```

---

## 4. Exception Taxonomy

All custom exceptions extend `BaseException`, which provides a consistent structure:

### 4.1 BaseException (Abstract)

```java
public abstract class BaseException extends RuntimeException {
    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final Map<String, Object> details;

    protected BaseException(String message, ErrorCode errorCode,
                           HttpStatus httpStatus) { ... }
    protected BaseException(String message, ErrorCode errorCode,
                           HttpStatus httpStatus, Throwable cause) { ... }
    protected BaseException(String message, ErrorCode errorCode,
                           HttpStatus httpStatus, Map<String, Object> details) { ... }
}
```

### 4.2 Web Exceptions (4xx -- Client Errors)

| Exception | HTTP Status | ErrorCode | When to Use |
|----|----|----|-----|
| `BadRequestException` | 400 | `BAD_REQUEST` | Validation failure, malformed JSON, missing required fields |
| `UnauthorizedException` | 401 | `UNAUTHORIZED` | Missing or invalid JWT token, expired token |
| `ForbiddenException` | 403 | `FORBIDDEN` | Valid JWT but insufficient role/permissions |
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` | Entity does not exist (user, family, association, etc.) |
| `ConflictException` | 409 | `CONFLICT` | Duplicate resource (email already registered, duplicate subscription) |
| `UnprocessableEntityException` | 422 | `UNPROCESSABLE_ENTITY` | Syntactically valid but semantically invalid (end date before start) |
| `TooManyRequestsException` | 429 | `TOO_MANY_REQUESTS` | Rate limit exceeded |

### 4.3 Server Exceptions (5xx -- Server Errors)

| Exception | HTTP Status | ErrorCode | When to Use |
|----|----|----|-----|
| `InternalServerException` | 500 | `INTERNAL_SERVER_ERROR` | Unexpected server error, unhandled exception fallback |
| `BadGatewayException` | 502 | `BAD_GATEWAY` | Downstream service returned invalid response |
| `ServiceUnavailableException` | 503 | `SERVICE_UNAVAILABLE` | Service temporarily unavailable (maintenance, overload) |
| `GatewayTimeoutException` | 504 | `GATEWAY_TIMEOUT` | Downstream service did not respond within timeout |

### 4.4 Container Exceptions (Infrastructure)

| Exception | HTTP Status | ErrorCode | When to Use |
|----|----|----|-----|
| `ServiceDiscoveryException` | 503 | `SERVICE_DISCOVERY_FAILURE` | Eureka registration or lookup failure |
| `CircuitBreakerOpenException` | 503 | `CIRCUIT_BREAKER_OPEN` | Resilience4j circuit breaker is open |
| `KafkaPublishException` | 503 | `KAFKA_PUBLISH_FAILURE` | Kafka producer send failure |
| `DatabaseConnectionException` | 503 | `DATABASE_CONNECTION_FAILURE` | PostgreSQL connection lost or pool exhausted |
| `ExternalApiException` | 502 | `EXTERNAL_API_FAILURE` | HelloAsso or other external API failure |

---

## 5. ErrorResponse DTO

The `ErrorResponse` is the standard error envelope returned by every service. All error responses
across the platform conform to this exact JSON shape.

### JSON Structure

```json
{
  "timestamp": "2025-09-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "BAD_REQUEST",
  "message": "Validation failed",
  "path": "/api/v1/families",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "details": [
    { "field": "name", "message": "must not be blank" },
    { "field": "email", "message": "must be a valid email address" }
  ]
}
```

### Field Descriptions

| Field | Type | Required | Description |
|----|----|----|-----|
| `timestamp` | ISO 8601 datetime | Yes | When the error occurred (UTC) |
| `status` | int | Yes | HTTP status code |
| `error` | string | Yes | HTTP status reason phrase |
| `errorCode` | string | Yes | Application-level error code from `ErrorCode` enum |
| `message` | string | Yes | Human-readable error description |
| `path` | string | Yes | Request URI that caused the error |
| `correlationId` | UUID | Yes | Request correlation ID from `X-Correlation-Id` header |
| `details` | array/object | No | Additional structured error details (validation errors, field-level messages) |

### Java Class

```java
@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {
    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String errorCode;
    private final String message;
    private final String path;
    private final String correlationId;
    private final Object details;

    public static ErrorResponse of(HttpStatus status, ErrorCode errorCode,
                                    String message, String path) {
        return ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(status.value())
            .error(status.getReasonPhrase())
            .errorCode(errorCode.name())
            .message(message)
            .path(path)
            .build();
    }
}
```

---

## 6. ErrorCode Enum

The `ErrorCode` enum provides application-level error codes that are more specific than HTTP status
codes. Frontend and monitoring systems use these codes for precise error identification.

```java
public enum ErrorCode {
    // Web (4xx)
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    RESOURCE_NOT_FOUND,
    CONFLICT,
    UNPROCESSABLE_ENTITY,
    TOO_MANY_REQUESTS,

    // Server (5xx)
    INTERNAL_SERVER_ERROR,
    BAD_GATEWAY,
    SERVICE_UNAVAILABLE,
    GATEWAY_TIMEOUT,

    // Container (infrastructure)
    SERVICE_DISCOVERY_FAILURE,
    CIRCUIT_BREAKER_OPEN,
    KAFKA_PUBLISH_FAILURE,
    DATABASE_CONNECTION_FAILURE,
    EXTERNAL_API_FAILURE
}
```

**Invariant**: All enum values must be unique. This is enforced by a dedicated test case
(`ErrorCodeTest.errorCodeValuesShouldBeUnique`).

---

## 7. GlobalExceptionHandler

The `GlobalExceptionHandler` is a `@RestControllerAdvice` that catches all exceptions thrown by
any controller in any service and maps them to the correct `ErrorResponse`.

### Handler Methods (18 total)

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // --- Web exceptions (7) ---
    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleBadRequest(BadRequestException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleUnauthorized(UnauthorizedException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleForbidden(ForbiddenException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleConflict(ConflictException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(UnprocessableEntityException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleUnprocessable(UnprocessableEntityException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ErrorResponse handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest req) { ... }

    // --- Server exceptions (4) ---
    @ExceptionHandler(InternalServerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleInternalServer(InternalServerException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(BadGatewayException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleBadGateway(BadGatewayException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(ServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleServiceUnavailable(ServiceUnavailableException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(GatewayTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public ErrorResponse handleGatewayTimeout(GatewayTimeoutException ex, HttpServletRequest req) { ... }

    // --- Container exceptions (5) ---
    @ExceptionHandler(ServiceDiscoveryException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleServiceDiscovery(ServiceDiscoveryException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(CircuitBreakerOpenException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleCircuitBreaker(CircuitBreakerOpenException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(KafkaPublishException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleKafkaPublish(KafkaPublishException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(DatabaseConnectionException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleDatabaseConnection(DatabaseConnectionException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(ExternalApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handleExternalApi(ExternalApiException ex, HttpServletRequest req) { ... }

    // --- Spring framework exceptions (2) ---
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) { ... }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGenericException(Exception ex, HttpServletRequest req) { ... }
}
```

### Logging Strategy

| Exception Category | Log Level | Details Logged |
|----|----|----|
| Web (4xx) | `WARN` | Message, path, correlation ID |
| Server (5xx) | `ERROR` | Message, path, correlation ID, full stack trace |
| Container | `ERROR` | Message, path, correlation ID, full stack trace, cause chain |
| Generic fallback | `ERROR` | Full exception with stack trace (unexpected errors) |

---

## 8. Spring Boot Auto-Configuration

The error handling module uses Spring Boot's auto-configuration mechanism so that any service
including it on the classpath automatically gains the `GlobalExceptionHandler`.

```java
@AutoConfiguration
@ComponentScan(basePackages = "com.familyhobbies.errorhandling")
public class ErrorHandlingAutoConfiguration {
    // Auto-configures GlobalExceptionHandler and all exception classes
}
```

**Registration file**: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.familyhobbies.errorhandling.config.ErrorHandlingAutoConfiguration
```

**Result**: Any Spring Boot service that has `error-handling` on its classpath (directly or
transitively via `common`) automatically registers the `GlobalExceptionHandler`. No `@Import`
or `@ComponentScan` needed in individual services.

---

## 9. Frontend Error Handling

The Angular frontend mirrors the backend error handling structure, providing consistent error
processing, French user-facing messages, and RGAA accessibility compliance.

### 9.1 Frontend Structure

```
frontend/src/app/core/error-handling/
├── models/
│   ├── api-error.model.ts           ← TypeScript interface matching ErrorResponse JSON
│   └── error-code.enum.ts           ← Enum mirroring backend ErrorCode values
├── handlers/
│   ├── http-error.handler.ts        ← Maps HTTP errors to user-facing French messages
│   └── global-error.handler.ts      ← Angular ErrorHandler override for unhandled errors
└── interceptors/
    └── error.interceptor.ts         ← HTTP interceptor with RGAA LiveAnnouncer
```

### 9.2 ApiError Model

```typescript
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  errorCode: string;
  message: string;
  path: string;
  correlationId?: string;
  details?: FieldError[] | Record<string, unknown>;
}

export interface FieldError {
  field: string;
  message: string;
}
```

### 9.3 ErrorCode Enum (Frontend)

```typescript
export enum ErrorCode {
  BAD_REQUEST = 'BAD_REQUEST',
  UNAUTHORIZED = 'UNAUTHORIZED',
  FORBIDDEN = 'FORBIDDEN',
  RESOURCE_NOT_FOUND = 'RESOURCE_NOT_FOUND',
  CONFLICT = 'CONFLICT',
  UNPROCESSABLE_ENTITY = 'UNPROCESSABLE_ENTITY',
  TOO_MANY_REQUESTS = 'TOO_MANY_REQUESTS',
  INTERNAL_SERVER_ERROR = 'INTERNAL_SERVER_ERROR',
  BAD_GATEWAY = 'BAD_GATEWAY',
  SERVICE_UNAVAILABLE = 'SERVICE_UNAVAILABLE',
  GATEWAY_TIMEOUT = 'GATEWAY_TIMEOUT',
  SERVICE_DISCOVERY_FAILURE = 'SERVICE_DISCOVERY_FAILURE',
  CIRCUIT_BREAKER_OPEN = 'CIRCUIT_BREAKER_OPEN',
  KAFKA_PUBLISH_FAILURE = 'KAFKA_PUBLISH_FAILURE',
  DATABASE_CONNECTION_FAILURE = 'DATABASE_CONNECTION_FAILURE',
  EXTERNAL_API_FAILURE = 'EXTERNAL_API_FAILURE',
}
```

### 9.4 French Error Messages

The `http-error.handler.ts` maps each error code to a user-facing French message:

| ErrorCode | French Message |
|----|-----|
| `BAD_REQUEST` | "Les donnees envoyees sont invalides." |
| `UNAUTHORIZED` | "Vous devez vous connecter pour acceder a cette ressource." |
| `FORBIDDEN` | "Vous n'avez pas les droits necessaires." |
| `RESOURCE_NOT_FOUND` | "La ressource demandee est introuvable." |
| `CONFLICT` | "Un conflit est survenu. Cette ressource existe deja." |
| `UNPROCESSABLE_ENTITY` | "Les donnees sont valides mais ne peuvent pas etre traitees." |
| `TOO_MANY_REQUESTS` | "Trop de requetes. Veuillez patienter." |
| `INTERNAL_SERVER_ERROR` | "Une erreur interne est survenue. Veuillez reessayer." |
| `SERVICE_UNAVAILABLE` | "Le service est temporairement indisponible." |

### 9.5 Error Interceptor with RGAA Compliance

The `error.interceptor.ts` uses Angular CDK's `LiveAnnouncer` to announce errors to screen
readers, ensuring RGAA accessibility compliance:

```typescript
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const apiError = parseHttpError(error);
      const userMessage = getFrenchErrorMessage(apiError.errorCode);

      // RGAA: announce error to screen readers
      liveAnnouncer.announce(userMessage, 'assertive');

      // Show snackbar/toast for user feedback
      snackBar.open(userMessage, 'Fermer', { duration: 5000 });

      return throwError(() => apiError);
    })
  );
};
```

### 9.6 Global Error Handler

The `global-error.handler.ts` overrides Angular's default `ErrorHandler` to catch unhandled
errors (uncaught promises, RxJS errors) and route them through the same error processing pipeline.

---

## 10. Test Strategy

The error handling module has a dedicated test suite with 44 test cases across 6 test files.
Tests are written first (TDD) and define the exact contract.

### 10.1 Test Files

| Test File | Category | Test Cases | What It Validates |
|----|----|----|----|
| `WebExceptionTest` | Unit | 7 | Each web exception carries correct HTTP status, error code, message, cause chain |
| `ServerExceptionTest` | Unit | 4 | Each server exception carries correct HTTP status, error code, message, cause chain |
| `ContainerExceptionTest` | Unit | 5 | Each container exception carries correct HTTP status, error code, message, cause chain |
| `GlobalExceptionHandlerTest` | Unit | 18 | Every exception type maps to correct HTTP status and ErrorResponse body |
| `ErrorResponseTest` | Unit | 6 | JSON serialization shape, factory methods, builder, timestamp auto-set |
| `ErrorCodeTest` | Unit | 4 | All enum values unique, no duplicates, correct count (16 values) |

### 10.2 Key Test Scenarios

**GlobalExceptionHandlerTest** (most critical -- 18 test cases):

```java
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @InjectMocks
    private GlobalExceptionHandler handler;

    @Mock
    private HttpServletRequest request;

    @Test
    void handleBadRequest_returns400WithCorrectErrorCode() { ... }

    @Test
    void handleUnauthorized_returns401WithCorrectErrorCode() { ... }

    @Test
    void handleResourceNotFound_returns404WithCorrectErrorCode() { ... }

    @Test
    void handleConflict_returns409WithCorrectErrorCode() { ... }

    @Test
    void handleValidationException_returns400WithFieldErrors() { ... }

    @Test
    void handleGenericException_returns500WithGenericMessage() { ... }

    // ... 12 more test cases
}
```

**ErrorCodeTest** (enum integrity):

```java
@Test
void allErrorCodeValuesShouldBeUnique() {
    Set<String> names = Arrays.stream(ErrorCode.values())
        .map(Enum::name)
        .collect(Collectors.toSet());
    assertEquals(ErrorCode.values().length, names.size(),
        "Duplicate ErrorCode values detected");
}

@Test
void shouldHaveExactly16ErrorCodes() {
    assertEquals(16, ErrorCode.values().length);
}
```

### 10.3 Running Tests

```bash
cd backend/error-handling
mvn test
```

All 44 tests must pass before any service code is written. The error handling module is the
first green bar in the project's test suite.

---

## 11. Usage Guidelines

### 11.1 Throwing Exceptions in Service Code

```java
// In any service class
public User findById(Long id) {
    return userRepository.findById(id)
        .orElseThrow(() -> new ResourceNotFoundException(
            "User not found with id: " + id));
}

public User register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.getEmail())) {
        throw new ConflictException("Email already registered: " + request.getEmail());
    }
    // ...
}
```

### 11.2 Throwing Exceptions with Details

```java
throw new BadRequestException("Validation failed",
    Map.of("field", "email", "message", "must be a valid email address"));
```

### 11.3 Wrapping Infrastructure Exceptions

```java
try {
    kafkaTemplate.send(topic, event).get();
} catch (Exception e) {
    throw new KafkaPublishException("Failed to publish event to topic: " + topic, e);
}

try {
    return helloAssoClient.searchOrganizations(query);
} catch (WebClientResponseException e) {
    throw new ExternalApiException("HelloAsso API call failed: " + e.getMessage(), e);
}
```

### 11.4 Do NOT Catch BaseException Subtypes in Controllers

The `GlobalExceptionHandler` handles all exceptions. Controllers should never have
try/catch blocks for `BaseException` subtypes -- let them propagate.

```java
// WRONG
@PostMapping
public ResponseEntity<UserResponse> register(@RequestBody RegisterRequest request) {
    try {
        return ResponseEntity.ok(userService.register(request));
    } catch (ConflictException e) {
        return ResponseEntity.status(409).body(null); // DON'T DO THIS
    }
}

// CORRECT
@PostMapping
public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userService.register(request));
    // ConflictException from service propagates to GlobalExceptionHandler
}
```

---

## 12. Revision History

| Date | Version | Author | Changes |
|----|----|----|-----|
| 2026-02-23 | 1.0 | Architecture Team | Initial version -- full error handling module specification |
