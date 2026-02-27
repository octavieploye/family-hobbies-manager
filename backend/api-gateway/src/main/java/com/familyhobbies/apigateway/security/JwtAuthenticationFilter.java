package com.familyhobbies.apigateway.security;

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
import java.util.List;

@Component
public class JwtAuthenticationFilter implements WebFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final JwtTokenProvider jwtTokenProvider;

    // H-007: Public paths must mirror SecurityConfig's permitAll() rules.
    // Note: Some SecurityConfig rules are HTTP-method-specific (e.g., GET-only for
    // associations/activities), but this filter does not have method awareness.
    // SecurityConfig's authorizeExchange rules still enforce role requirements for
    // non-public endpoints. When method-specific filtering is needed, this list
    // should be refactored to include HTTP method checks.
    private final List<String> publicPaths = List.of(
        "/api/v1/auth/",
        "/api/v1/associations/",
        "/api/v1/activities/",
        "/api/v1/payments/webhook/",
        "/actuator/health",
        "/actuator/info"
    );

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Step 0: Skip authentication for CORS preflight requests
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        // Step 1: Skip authentication for public paths
        if (isPublicPath(path)) {
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
            // Step 4: Validate token and extract claims (single parse — H-006 fix)
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

    private boolean isPublicPath(String path) {
        return publicPaths.stream().anyMatch(path::startsWith);
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().add("Content-Type", "application/json");

        String body = String.format(
            "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"%s\",\"timestamp\":\"%s\",\"path\":\"%s\"}",
            message,
            Instant.now().toString(),
            exchange.getRequest().getURI().getPath()
        );

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
