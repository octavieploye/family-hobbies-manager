package com.familyhobbies.associationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the association-service.
 * <p>
 * Public endpoints (GET /api/v1/associations/**) are accessible without authentication.
 * Admin endpoints (POST /api/v1/admin/**) require authentication and ADMIN role
 * (enforced by {@code @PreAuthorize} on the controller).
 * <p>
 * In production, JWT validation is handled by the API gateway. This service
 * trusts the X-User-Id and X-User-Roles headers forwarded by the gateway.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/associations/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/v1/admin/**").authenticated()
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
