package com.familyhobbies.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import reactor.core.publisher.Mono;

/**
 * CORS configuration that directly sets CORS headers on responses.
 * <p>
 * This avoids using DefaultCorsProcessor which calls CorsUtils.isSameOrigin()
 * and requires a non-null URI scheme — a condition that may not hold in all
 * test and proxy environments (e.g. WebTestClient with RANDOM_PORT).
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,PATCH,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${cors.allowed-headers:Authorization,Content-Type,X-Requested-With}")
    private String allowedHeaders;

    @Value("${cors.exposed-headers:X-Total-Count,X-Correlation-Id}")
    private String exposedHeaders;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public WebFilter corsFilter() {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);

            if (origin == null || origin.isBlank()) {
                return chain.filter(exchange);
            }

            Set<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

            if (!origins.contains(origin) && !origins.contains("*")) {
                return chain.filter(exchange);
            }

            ServerHttpResponse response = exchange.getResponse();
            HttpHeaders headers = response.getHeaders();
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, String.valueOf(allowCredentials));
            headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, exposedHeaders);

            if (HttpMethod.OPTIONS.equals(request.getMethod())) {
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, allowedMethods);
                headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, allowedHeaders);
                headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, String.valueOf(maxAge));
                response.setStatusCode(HttpStatus.OK);
                return Mono.empty();
            }

            return chain.filter(exchange);
        };
    }
}
