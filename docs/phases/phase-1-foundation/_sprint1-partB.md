> **DEPRECATED** — This file is superseded by `sprint-1-security.md` (the single authoritative Sprint 1 document).
> Do NOT use this file for implementation. It contains outdated JJWT versions, wrong Liquibase formats, and deprecated API patterns.
> Kept for historical reference only.

---

### Story S1-004: Implement Gateway JWT Authentication Filter

**Points**: 5 | **Priority**: P0 | **Epic**: Security

#### Context

The API Gateway is the trust boundary. It validates JWT tokens on every non-public request,
extracts user identity (userId + roles), and forwards them as trusted headers (`X-User-Id`,
`X-User-Roles`) to downstream services. Downstream services never re-validate the JWT --
they trust the gateway unconditionally.

The API Gateway uses **Spring WebFlux** (reactive) because Spring Cloud Gateway is built on
Project Reactor. All security classes in the gateway must use reactive types: `Mono`,
`ServerWebExchange`, `WebFilter`, `ServerHttpSecurity`. Do **not** use servlet types
(`OncePerRequestFilter`, `HttpServletRequest`, `HttpSecurity`) in the gateway.

#### Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|---------------|---------------|
| 1 | Create JwtAuthenticationFilter | `backend/api-gateway/src/main/java/com/familyhobbies/apigateway/security/JwtAuthenticationFilter.java` | WebFilter that validates JWT and adds forwarding headers | Compiles |
| 2 | Create Gateway SecurityConfig | `backend/api-gateway/src/main/java/com/familyhobbies/apigateway/config/SecurityConfig.java` | WebFlux security with permit/auth rules | Compiles |
| 3 | Create SecurityHeadersConfig | `backend/api-gateway/src/main/java/com/familyhobbies/apigateway/config/SecurityHeadersConfig.java` | Response headers filter (X-Content-Type-Options, etc.) | Compiles |
| 4 | Update gateway application.yml | `backend/api-gateway/src/main/resources/application.yml` | Add jwt.secret and public-paths config | Config loads |

---

#### Task 1 Detail: Create JwtAuthenticationFilter

**What**: A reactive `WebFilter` that intercepts every request passing through the API Gateway.
For non-public paths, it extracts the `Bearer` token from the `Authorization` header, validates
the JWT signature and expiry via `JwtTokenProvider`, extracts user identity (`sub` claim) and
roles, and mutates the downstream request to include `X-User-Id` and `X-User-Roles` headers.
For public paths, it passes the request through without validation.

**Where**: `backend/api-gateway/src/main/java/com/familyhobbies/apigateway/security/JwtAuthenticationFilter.java`

**Why**: The gateway is the single point of JWT validation. Without this filter, any request
(even from unauthenticated users) would reach downstream services. This filter enforces
authentication and injects trusted identity headers that downstream services rely on. Returning
structured JSON error bodies (not just status codes) ensures the Angular frontend can display
meaningful error messages.

**Content**:

```java
package com.familyhobbies.apigateway.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final JwtTokenProvider jwtTokenProvider;
    private final List<String> publicPaths = List.of(
        "/api/v1/auth/",
        "/actuator/health",
        "/actuator/info",
        "/api/v1/payments/webhook/"
    );

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Skip authentication for public paths
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        // Extract Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            // Validate token and extract claims
            var claims = jwtTokenProvider.validateToken(token);
            String userId = claims.getSubject();
            String roles = String.join(",", jwtTokenProvider.getRolesFromToken(token));

            // Mutate request to add trusted headers for downstream services
            ServerHttpRequest mutatedRequest = request.mutate()
                .header(HEADER_USER_ID, userId)
                .header(HEADER_USER_ROLES, roles)
                .build();

            ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

            return chain.filter(mutatedExchange);

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return unauthorizedResponse(exchange, "Token expired");
        } catch (io.jsonwebtoken.JwtException e) {
            return unauthorizedResponse(exchange, "Invalid token");
        }
    }

    private boolean isPublicPath(String path) {
        return publicPaths.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        String body = String.format(
            "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\",\"timestamp\":\"%s\",\"path\":\"%s\"}",
            message,
            java.time.Instant.now().toString(),
            exchange.getRequest().getURI().getPath()
        );

        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
```

**Verify**:

```bash
cd backend && mvn compile -pl api-gateway -q
# Expected: compiles without error
```

---

#### Task 2 Detail: Create Gateway SecurityConfig

**What**: Spring WebFlux security configuration that defines which endpoints are public
(no authentication), which require ADMIN role, and which require any authenticated user.
Disables CSRF (stateless API), form login, and HTTP basic. Configures CORS for the Angular
dev server and production origin. Registers the `JwtAuthenticationFilter` before the
authentication filter in the reactive security chain.

**Where**: `backend/api-gateway/src/main/java/com/familyhobbies/apigateway/config/SecurityConfig.java`

**Why**: Without this configuration, Spring Security would apply default behavior (form login,
session-based auth), which is incompatible with a stateless JWT API gateway. The authorization
rules enforce the endpoint matrix from the security architecture at the gateway level, providing
the first line of defense before requests reach downstream services.

**Content**:

```java
package com.familyhobbies.apigateway.config;

import com.familyhobbies.apigateway.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            // Disable CSRF -- stateless JWT-based API, no session cookies
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            // CORS -- allow Angular dev server and production origins
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Disable form login and HTTP basic -- JWT only
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

            // Authorization rules
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints -- no authentication required
                .pathMatchers("/api/v1/auth/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/associations/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/activities/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/associations/search").permitAll()
                .pathMatchers("/api/v1/payments/webhook/**").permitAll()
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()

                // Admin-only endpoints
                .pathMatchers("/api/v1/users/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.POST, "/api/v1/associations/sync").hasRole("ADMIN")
                .pathMatchers("/api/v1/notifications/templates/**").hasRole("ADMIN")
                .pathMatchers("/actuator/metrics", "/actuator/prometheus").hasRole("ADMIN")
                .pathMatchers("/api/v1/rgpd/audit-log").hasRole("ADMIN")

                // Association manager endpoints
                .pathMatchers("/api/v1/associations/*/subscribers").hasAnyRole("ASSOCIATION", "ADMIN")
                .pathMatchers("/api/v1/attendance/report").hasAnyRole("ASSOCIATION", "ADMIN")
                .pathMatchers("/api/v1/payments/association/**").hasAnyRole("ASSOCIATION", "ADMIN")

                // All other endpoints require authentication (any role)
                .anyExchange().authenticated()
            )

            // Add JWT filter before the authentication filter
            .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:4200",       // Angular dev server
            "https://familyhobbies.fr"     // Production origin
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("X-Total-Count", "X-Correlation-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

**Verify**:

```bash
cd backend && mvn compile -pl api-gateway -q
# Expected: compiles without error
```

---

#### Task 3 Detail: Create SecurityHeadersConfig

**What**: A reactive `WebFilter` bean that injects security response headers on every HTTP
response leaving the gateway. Protects against clickjacking, MIME-type sniffing, XSS,
protocol downgrade attacks, and restricts browser APIs.

**Where**: `backend/api-gateway/src/main/java/com/familyhobbies/apigateway/config/SecurityHeadersConfig.java`

**Why**: Security headers are a low-cost, high-impact defense-in-depth measure. They instruct
browsers to enforce security policies that prevent common web attacks. The gateway is the
correct place to set these because it is the single exit point for all responses to the client.
Setting them here means every response -- from every downstream service -- gets the same
consistent set of security headers without each service needing to configure them individually.

**Content**:

```java
package com.familyhobbies.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@Configuration
public class SecurityHeadersConfig {

    @Bean
    public WebFilter securityHeadersFilter() {
        return (exchange, chain) -> {
            exchange.getResponse().getHeaders().add(
                "X-Content-Type-Options", "nosniff");
            exchange.getResponse().getHeaders().add(
                "X-Frame-Options", "DENY");
            exchange.getResponse().getHeaders().add(
                "X-XSS-Protection", "0");
            exchange.getResponse().getHeaders().add(
                "Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");
            exchange.getResponse().getHeaders().add(
                "Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data:; font-src 'self'; connect-src 'self' https://api.helloasso.com; "
                + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
            exchange.getResponse().getHeaders().add(
                "Referrer-Policy", "strict-origin-when-cross-origin");
            exchange.getResponse().getHeaders().add(
                "Permissions-Policy", "camera=(), microphone=(), geolocation=()");

            return chain.filter(exchange);
        };
    }
}
```

**Verify**:

```bash
cd backend && mvn compile -pl api-gateway -q
# Expected: compiles without error
```

---

#### Task 4 Detail: Update Gateway application.yml

**What**: Add the `jwt.secret` property to the existing API Gateway application.yml. The secret
is read from the `JWT_SECRET` environment variable with a development-only default fallback
that satisfies the HMAC-SHA256 minimum key length requirement (256 bits = 32 bytes).

**Where**: `backend/api-gateway/src/main/resources/application.yml`

**Why**: The `JwtTokenProvider` component (used by `JwtAuthenticationFilter`) reads `jwt.secret`
via `@Value("${jwt.secret}")`. Without this property, Spring Boot will fail to start with an
unresolvable placeholder error. The default value is only for local development -- production
deployments must set the `JWT_SECRET` environment variable to a cryptographically random key.

**Content**:

Add the following block to the **existing** gateway `application.yml` (below the `eureka` section):

```yaml
jwt:
  secret: ${JWT_SECRET:default-dev-secret-that-is-at-least-256-bits-long-for-hmac-sha256}
```

The full `application.yml` after this change:

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        # --- user-service routes (Sprint 0) ---
        - id: auth-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/auth/**

        - id: user-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**

        - id: family-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/families/**

        - id: rgpd-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/rgpd/**

        # --- association-service routes added in Sprint 2 ---
        # --- payment-service routes added in Sprint 5 ---
        # --- notification-service routes added in Sprint 6 ---

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

jwt:
  secret: ${JWT_SECRET:default-dev-secret-that-is-at-least-256-bits-long-for-hmac-sha256}
```

**Verify**:

```bash
cd backend/api-gateway && mvn spring-boot:run
# Expected: starts on :8080 without "Could not resolve placeholder 'jwt.secret'" error
# Press Ctrl+C to stop
```

---

#### Failing Tests (TDD Contract)

**File**: `backend/api-gateway/src/test/java/com/familyhobbies/apigateway/security/JwtAuthenticationFilterTest.java`

**What**: Five tests that verify the `JwtAuthenticationFilter` behavior: valid tokens add
forwarding headers, expired tokens return 401, invalid tokens return 401, missing Authorization
header on a protected path returns 401, and public paths bypass authentication entirely.

**Why**: These tests define the security contract of the gateway. They must be written before
the implementation and must all pass after implementation is complete. They use `WebTestClient`
because the gateway is reactive.

**How it works**: The test creates a minimal Spring Boot context with the `JwtAuthenticationFilter`,
a `JwtTokenProvider`, and a mock downstream handler. It uses `WebTestClient` to send HTTP
requests through the filter chain and asserts on response status codes and headers.

```java
package com.familyhobbies.apigateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "default-dev-secret-that-is-at-least-256-bits-long-for-hmac-sha256";
    private static final SecretKey SIGNING_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

    private WebTestClient webTestClient;
    private HeaderCapturingHandler downstreamHandler;

    @BeforeEach
    void setUp() {
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(SECRET);
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenProvider);

        downstreamHandler = new HeaderCapturingHandler();

        var httpHandler = WebHttpHandlerBuilder
            .webHandler(downstreamHandler)
            .filter(jwtFilter)
            .build();

        webTestClient = WebTestClient.bindToServer()
            .baseUrl("http://localhost")
            .build();

        // Use bindToWebHandler for unit-testing the filter directly
        webTestClient = WebTestClient.bindToWebHandler(httpHandler)
            .build();
    }

    @Test
    void validToken_shouldForwardRequestWithUserHeaders() {
        String token = createValidToken("42", List.of("FAMILY"));

        webTestClient.get()
            .uri("/api/v1/families/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange()
            .expectStatus().isOk();

        assertEquals("42", downstreamHandler.getCapturedHeader("X-User-Id"));
        assertEquals("FAMILY", downstreamHandler.getCapturedHeader("X-User-Roles"));
    }

    @Test
    void expiredToken_shouldReturn401() {
        String token = createExpiredToken("42", List.of("FAMILY"));

        webTestClient.get()
            .uri("/api/v1/families/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .value(body -> {
                assertNotNull(body);
                assert body.contains("Token expired");
            });
    }

    @Test
    void invalidToken_shouldReturn401() {
        webTestClient.get()
            .uri("/api/v1/families/1")
            .header(HttpHeaders.AUTHORIZATION, "Bearer this.is.garbage")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .value(body -> {
                assertNotNull(body);
                assert body.contains("Invalid token");
            });
    }

    @Test
    void missingAuthorizationHeader_shouldReturn401ForProtectedPath() {
        webTestClient.get()
            .uri("/api/v1/families/1")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody(String.class)
            .value(body -> {
                assertNotNull(body);
                assert body.contains("Missing or invalid Authorization header");
            });
    }

    @Test
    void publicPath_shouldBypassAuthentication() {
        webTestClient.get()
            .uri("/api/v1/auth/login")
            .exchange()
            .expectStatus().isOk();
    }

    // --- Helper methods ---

    private String createValidToken(String userId, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3_600_000); // 1 hour from now

        return Jwts.builder()
            .setSubject(userId)
            .claim("roles", roles)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(SIGNING_KEY, SignatureAlgorithm.HS256)
            .compact();
    }

    private String createExpiredToken(String userId, List<String> roles) {
        Date past = new Date(System.currentTimeMillis() - 7_200_000); // 2 hours ago
        Date expired = new Date(System.currentTimeMillis() - 3_600_000); // 1 hour ago

        return Jwts.builder()
            .setSubject(userId)
            .claim("roles", roles)
            .setIssuedAt(past)
            .setExpiration(expired)
            .signWith(SIGNING_KEY, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * A WebHandler that captures incoming request headers so tests can assert
     * that the JwtAuthenticationFilter correctly injected X-User-Id and X-User-Roles.
     */
    private static class HeaderCapturingHandler implements WebHandler {

        private volatile ServerWebExchange capturedExchange;

        @Override
        public Mono<Void> handle(ServerWebExchange exchange) {
            this.capturedExchange = exchange;
            exchange.getResponse().setStatusCode(HttpStatus.OK);
            return exchange.getResponse().setComplete();
        }

        public String getCapturedHeader(String headerName) {
            if (capturedExchange == null) {
                return null;
            }
            return capturedExchange.getRequest().getHeaders().getFirst(headerName);
        }
    }
}
```

**Run**:

```bash
cd backend && mvn test -pl api-gateway -Dtest=JwtAuthenticationFilterTest
# Expected before implementation: 5 tests FAIL (compile errors — JwtTokenProvider does not exist yet)
# Expected after implementation: 5 tests PASS
```

---

### Story S1-005: Implement Downstream UserContext Filter

**Points**: 3 | **Priority**: P0 | **Epic**: Security

#### Context

Downstream services (user-service, association-service, payment-service, notification-service)
trust the gateway. They read `X-User-Id` and `X-User-Roles` headers injected by the gateway
and populate a ThreadLocal `UserContext` for the duration of the request. The `UserContextFilter`
also sets the Spring `SecurityContextHolder` so that `@PreAuthorize` annotations and `hasRole()`
checks work correctly.

This code lives in the **common** library so every downstream service gets it automatically
by depending on `common`. Downstream services are **servlet-based** (Spring MVC), not reactive.
The filter extends `OncePerRequestFilter`, not `WebFilter`.

#### Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|---------------|---------------|
| 1 | Create UserContext class | `backend/common/src/main/java/com/familyhobbies/common/security/UserContext.java` | ThreadLocal holder for userId + roles | Compiles |
| 2 | Create UserContextFilter | `backend/common/src/main/java/com/familyhobbies/common/security/UserContextFilter.java` | Servlet filter extracting gateway headers | Compiles |
| 3 | Update user-service SecurityConfig | `backend/user-service/src/main/java/com/familyhobbies/userservice/config/SecurityConfig.java` | Register UserContextFilter in filter chain | Compiles |

---

#### Task 1 Detail: Create UserContext Class

**What**: An immutable holder class backed by a `ThreadLocal` that stores the current user's
`userId` (Long) and `roles` (List of Strings) for the duration of a single HTTP request. Provides
static methods `set()`, `get()`, and `clear()` to manage the ThreadLocal lifecycle, plus helper
methods `hasRole(String)` and `isAdmin()` for convenient role checks in service-layer code.

**Where**: `backend/common/src/main/java/com/familyhobbies/common/security/UserContext.java`

**Why**: Service-layer code (controllers, services, repositories) needs access to the current
user's identity without passing userId as a method parameter through every layer. The ThreadLocal
pattern provides request-scoped access. The `get()` method throws `IllegalStateException` if
called before `set()`, which catches configuration errors early (e.g., if the filter is not
registered).

**Content**:

```java
package com.familyhobbies.common.security;

import java.util.List;

public final class UserContext {

    private static final ThreadLocal<UserContext> CURRENT = new ThreadLocal<>();

    private final Long userId;
    private final List<String> roles;

    public UserContext(Long userId, List<String> roles) {
        this.userId = userId;
        this.roles = roles;
    }

    public static void set(UserContext context) {
        CURRENT.set(context);
    }

    public static UserContext get() {
        UserContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException("UserContext not set -- is UserContextFilter registered?");
        }
        return ctx;
    }

    public static void clear() {
        CURRENT.remove();
    }

    public Long getUserId() {
        return userId;
    }

    public List<String> getRoles() {
        return roles;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
```

**Verify**:

```bash
cd backend && mvn compile -pl common -q
# Expected: compiles without error
```

---

#### Task 2 Detail: Create UserContextFilter

**What**: A servlet filter that extends `OncePerRequestFilter` (executed once per HTTP request).
It reads `X-User-Id` and `X-User-Roles` headers from the incoming request. If both are present,
it creates a `UsernamePasswordAuthenticationToken` with the parsed roles as `SimpleGrantedAuthority`
objects (prefixed with `ROLE_` so Spring Security's `hasRole()` works) and sets it on the
`SecurityContextHolder`. It also populates the `UserContext` ThreadLocal. In the `finally` block,
it always clears both `SecurityContextHolder` and `UserContext` to prevent memory leaks and
cross-request contamination.

**Where**: `backend/common/src/main/java/com/familyhobbies/common/security/UserContextFilter.java`

**Why**: Without this filter, downstream services would have empty SecurityContextHolder and
no UserContext. Spring Security's `@PreAuthorize("hasRole('ADMIN')")` annotations would always
deny access. Service-layer code calling `UserContext.get()` would throw `IllegalStateException`.
This filter is the bridge between the gateway's forwarded headers and the downstream service's
security model.

**Content**:

```java
package com.familyhobbies.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Component
public class UserContextFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(HEADER_USER_ID);
        String roles = request.getHeader(HEADER_USER_ROLES);

        if (userId != null && roles != null) {
            // Parse roles into Spring Security authorities
            List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                .map(String::trim)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

            // Set Spring SecurityContext so @PreAuthorize and hasRole() work
            var authentication = new UsernamePasswordAuthenticationToken(
                Long.valueOf(userId),   // principal = userId
                null,                   // credentials (not needed)
                authorities
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Set ThreadLocal for business logic access
            UserContext.set(new UserContext(Long.valueOf(userId), parseRoles(roles)));
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear ThreadLocal to prevent memory leaks
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(roles.split(","))
            .map(String::trim)
            .toList();
    }
}
```

**Verify**:

```bash
cd backend && mvn compile -pl common -q
# Expected: compiles without error
```

---

#### Task 3 Detail: Update user-service SecurityConfig

**What**: Inject the `UserContextFilter` into the user-service `SecurityConfig` and register it
in the Spring Security filter chain using `addFilterBefore()` so it executes before the
`UsernamePasswordAuthenticationFilter`. This ensures gateway headers are parsed into the
SecurityContext before any authorization decisions are made.

**Where**: `backend/user-service/src/main/java/com/familyhobbies/userservice/config/SecurityConfig.java`

**Why**: The `UserContextFilter` is defined in the common library, but it must be explicitly
wired into each service's filter chain. Without `addFilterBefore()`, the filter would be a
Spring component but would not participate in the Spring Security filter chain -- meaning
`@PreAuthorize` annotations would not have access to the user's roles.

**Content**:

```java
package com.familyhobbies.userservice.config;

import com.familyhobbies.common.security.UserContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserContextFilter userContextFilter;

    public SecurityConfig(UserContextFilter userContextFilter) {
        this.userContextFilter = userContextFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            // Stateless -- no sessions
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF disabled -- behind gateway, no cookies
            .csrf(csrf -> csrf.disable())

            // No form login or HTTP basic
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())

            // Authorization rules (defense-in-depth, gateway also enforces)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .anyRequest().authenticated()
            )

            // Extract user context from gateway headers
            .addFilterBefore(userContextFilter, UsernamePasswordAuthenticationFilter.class)

            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
```

**Verify**:

```bash
cd backend && mvn compile -pl user-service -q
# Expected: compiles without error
```

---

#### Failing Tests (TDD Contract)

**Test File 1**: `backend/common/src/test/java/com/familyhobbies/common/security/UserContextTest.java`

**What**: Four unit tests that verify the `UserContext` ThreadLocal lifecycle and role-checking
behavior.

**Why**: The `UserContext` is used by every downstream service for authorization decisions.
These tests verify that `set()`/`get()`/`clear()` manage the ThreadLocal correctly and that
`hasRole()` matches roles accurately. The `get_withoutSet_shouldThrowIllegalState` test is
critical -- it verifies that calling `get()` without a prior `set()` fails loudly rather
than returning `null`, which would cause `NullPointerException` in service code.

```java
package com.familyhobbies.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContextTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void set_and_get_shouldReturnSameContext() {
        UserContext context = new UserContext(1L, List.of("FAMILY"));
        UserContext.set(context);

        UserContext retrieved = UserContext.get();

        assertEquals(1L, retrieved.getUserId());
        assertEquals(List.of("FAMILY"), retrieved.getRoles());
    }

    @Test
    void get_withoutSet_shouldThrowIllegalState() {
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            UserContext::get
        );

        assertTrue(exception.getMessage().contains("UserContext not set"));
    }

    @Test
    void clear_shouldRemoveContext() {
        UserContext.set(new UserContext(1L, List.of("FAMILY")));
        UserContext.clear();

        assertThrows(IllegalStateException.class, UserContext::get);
    }

    @Test
    void hasRole_shouldMatchCorrectly() {
        UserContext context = new UserContext(1L, List.of("ADMIN", "FAMILY"));
        UserContext.set(context);

        UserContext retrieved = UserContext.get();

        assertTrue(retrieved.hasRole("ADMIN"));
        assertTrue(retrieved.hasRole("FAMILY"));
        assertFalse(retrieved.hasRole("ASSOCIATION"));
        assertTrue(retrieved.isAdmin());
    }
}
```

**Run**:

```bash
cd backend && mvn test -pl common -Dtest=UserContextTest
# Expected before implementation: 4 tests FAIL (compile errors — UserContext does not exist yet)
# Expected after implementation: 4 tests PASS
```

---

**Test File 2**: `backend/common/src/test/java/com/familyhobbies/common/security/UserContextFilterTest.java`

**What**: Five unit tests that verify the `UserContextFilter` correctly populates the
`SecurityContextHolder` and `UserContext` from gateway headers, handles multiple roles,
clears both contexts after the request completes, and does nothing when headers are missing.

**Why**: This filter is the security bridge between the gateway and every downstream service.
If it fails to set the SecurityContext, all `@PreAuthorize` annotations break. If it fails
to clear the contexts, requests from one user could see another user's data (a critical
security vulnerability). These tests use `MockHttpServletRequest`, `MockHttpServletResponse`,
and `MockFilterChain` from Spring Test to simulate the servlet container without starting
a full server.

```java
package com.familyhobbies.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserContextFilterTest {

    private UserContextFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new UserContextFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    @Test
    void shouldPopulateSecurityContextFromHeaders() throws ServletException, IOException {
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-User-Roles", "FAMILY");

        // We need a filter chain that captures the security context during execution
        FilterChain capturingChain = (req, res) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(auth, "Authentication should be set during filter chain execution");
            assertEquals(1L, auth.getPrincipal());
            assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_FAMILY")));
        };

        filter.doFilterInternal(request, response, capturingChain);
    }

    @Test
    void shouldPopulateUserContextFromHeaders() throws ServletException, IOException {
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-User-Roles", "FAMILY");

        FilterChain capturingChain = (req, res) -> {
            UserContext ctx = UserContext.get();
            assertNotNull(ctx, "UserContext should be set during filter chain execution");
            assertEquals(1L, ctx.getUserId());
            assertTrue(ctx.hasRole("FAMILY"));
        };

        filter.doFilterInternal(request, response, capturingChain);
    }

    @Test
    void shouldHandleMultipleRoles() throws ServletException, IOException {
        request.addHeader("X-User-Id", "5");
        request.addHeader("X-User-Roles", "FAMILY,ADMIN");

        FilterChain capturingChain = (req, res) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(auth, "Authentication should be set during filter chain execution");
            assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_FAMILY")),
                "Should contain ROLE_FAMILY authority");
            assertTrue(auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")),
                "Should contain ROLE_ADMIN authority");
            assertEquals(2, auth.getAuthorities().size(),
                "Should have exactly 2 authorities");

            UserContext ctx = UserContext.get();
            assertTrue(ctx.hasRole("FAMILY"));
            assertTrue(ctx.hasRole("ADMIN"));
            assertTrue(ctx.isAdmin());
        };

        filter.doFilterInternal(request, response, capturingChain);
    }

    @Test
    void shouldClearContextAfterRequest() throws ServletException, IOException {
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-User-Roles", "FAMILY");

        filter.doFilterInternal(request, response, filterChain);

        // After the filter completes, both contexts should be cleared
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
            "SecurityContext should be cleared after filter chain completes");
        assertThrows(IllegalStateException.class, UserContext::get,
            "UserContext should be cleared after filter chain completes");
    }

    @Test
    void missingHeaders_shouldNotSetContext() throws ServletException, IOException {
        // No X-User-Id or X-User-Roles headers set

        FilterChain capturingChain = (req, res) -> {
            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext should not be set when headers are missing");
            assertThrows(IllegalStateException.class, UserContext::get,
                "UserContext should not be set when headers are missing");
        };

        filter.doFilterInternal(request, response, capturingChain);
    }
}
```

**Run**:

```bash
cd backend && mvn test -pl common -Dtest=UserContextFilterTest
# Expected before implementation: 5 tests FAIL (compile errors — UserContextFilter does not exist yet)
# Expected after implementation: 5 tests PASS
```

---
