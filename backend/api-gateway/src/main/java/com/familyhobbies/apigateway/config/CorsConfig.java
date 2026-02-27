package com.familyhobbies.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.WebFilter;

import java.util.HashSet;
import java.util.Set;

import reactor.core.publisher.Mono;

/**
 * CORS configuration that directly sets CORS headers on responses.
 * <p>
 * This avoids using DefaultCorsProcessor which calls CorsUtils.isSameOrigin()
 * and requires a non-null URI scheme — a condition that may not hold in all
 * test and proxy environments (e.g. WebTestClient with RANDOM_PORT).
 * <p>
 * CORS values are externalized via {@link CorsProperties} (application.yml).
 */
@Configuration
public class CorsConfig {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter corsFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);

            if (origin == null || origin.isBlank()) {
                return chain.filter(exchange);
            }

            Set<String> origins = corsProperties.getAllowedOrigins() != null
                ? new HashSet<>(corsProperties.getAllowedOrigins())
                : Set.of();

            if (!origins.contains(origin) && !origins.contains("*")) {
                return chain.filter(exchange);
            }

            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                    String.valueOf(corsProperties.isAllowCredentials()));
            headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                    joinList(corsProperties.getExposedHeaders()));

            if (HttpMethod.OPTIONS.equals(request.getMethod())) {
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        joinList(corsProperties.getAllowedMethods()));
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        joinList(corsProperties.getAllowedHeaders()));
                headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE,
                        String.valueOf(corsProperties.getMaxAge()));
                response.setStatusCode(HttpStatus.OK);
                return Mono.empty();
            }

            return chain.filter(exchange);
        };
    }

    private String joinList(java.util.List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return String.join(",", items);
    }
}
