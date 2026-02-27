package com.familyhobbies.apigateway.config;

import com.familyhobbies.apigateway.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    // Note: Routes for association-service, payment-service, and notification-service
    // are added to application.yml in their respective sprints (Sprint 2, 5, 6).
    // SecurityConfig rules are defined ahead of time so they are enforced immediately
    // when the routes are activated.
    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            .cors(ServerHttpSecurity.CorsSpec::disable)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
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
            .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }
}
