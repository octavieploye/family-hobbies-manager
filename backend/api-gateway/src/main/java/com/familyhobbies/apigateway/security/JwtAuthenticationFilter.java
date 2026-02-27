package com.familyhobbies.apigateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    /**
     * Defines a public route by path prefix and optional HTTP method.
     * A null method means ALL methods are allowed without authentication.
     *
     * M-017: Replaces the previous path-only list to align with SecurityConfig's
     * method-specific permitAll() rules (e.g., GET-only for associations/activities).
     */
    record PublicRoute(String pathPrefix, HttpMethod method) {

        /**
         * Checks if the given request path and method match this public route.
         */
        boolean matches(String path, HttpMethod requestMethod) {
            if (!path.startsWith(pathPrefix)) {
                return false;
            }
            // null method means any method is public for this path
            return method == null || method.equals(requestMethod);
        }
    }

    // M-017: Public routes with HTTP method awareness, mirroring SecurityConfig's permitAll() rules.
    private final List<PublicRoute> publicRoutes = List.of(
        new PublicRoute("/api/v1/auth/", null),                    // All methods
        new PublicRoute("/api/v1/associations/search", HttpMethod.POST), // POST search only
        new PublicRoute("/api/v1/associations/", HttpMethod.GET),  // GET only
        new PublicRoute("/api/v1/activities/", HttpMethod.GET),    // GET only
        new PublicRoute("/api/v1/payments/webhook/", null),        // All methods (webhook)
        new PublicRoute("/actuator/health", null),                 // All methods
        new PublicRoute("/actuator/info", null)                    // All methods
    );

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();

        // Step 0: Skip authentication for CORS preflight requests
        if (HttpMethod.OPTIONS.equals(method)) {
            return chain.filter(exchange);
        }

        // Step 1: Skip authentication for public paths (M-017: now method-aware)
        if (isPublicPath(path, method)) {
            return chain.filter(exchange);
        }

        // Step 2: Extract Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorizedResponse(exchange, "Missing or invalid Authorization header");
        }

        // Step 3: Extract the token (everything after "Bearer ")
        String token = authHeader.substring(BEARER_PREFIX.length());

        try {
            // Step 4: Validate token and extract claims (single parse -- H-006 fix)
            var claims = jwtTokenProvider.validateToken(token);
            String userId = claims.getSubject();
            List<String> rolesList = jwtTokenProvider.getRolesFromClaims(claims);
            String roles = String.join(",", rolesList);

            // Step 5: Create Spring Security Authentication with ROLE_-prefixed authorities
            List<SimpleGrantedAuthority> authorities = rolesList.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                userId, null, authorities);

            // Step 6: Mutate request to add trusted headers for downstream services
            ServerHttpRequest mutatedRequest = request.mutate()
                .header(HEADER_USER_ID, userId)
                .header(HEADER_USER_ROLES, roles)
                .build();

            ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

            // Step 7: Continue filter chain with authentication in reactive SecurityContext
            return chain.filter(mutatedExchange)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return unauthorizedResponse(exchange, "Token expired");
        } catch (io.jsonwebtoken.JwtException e) {
            return unauthorizedResponse(exchange, "Invalid token");
        }
    }

    /**
     * M-017: Checks if the given path and HTTP method match any public route.
     * Replaces the previous path-only check to add method awareness.
     */
    private boolean isPublicPath(String path, HttpMethod method) {
        return publicRoutes.stream().anyMatch(route -> route.matches(path, method));
    }

    /**
     * M-019: Uses Jackson ObjectMapper for JSON serialization instead of String.format
     * to prevent injection via path or message values.
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("status", 401);
        errorBody.put("error", "Unauthorized");
        errorBody.put("message", message);
        errorBody.put("timestamp", Instant.now().toString());
        errorBody.put("path", exchange.getRequest().getURI().getPath());

        String body;
        try {
            body = objectMapper.writeValueAsString(errorBody);
        } catch (JsonProcessingException e) {
            // Fallback: if ObjectMapper fails, use a safe static message
            body = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication failed\"}";
        }

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
