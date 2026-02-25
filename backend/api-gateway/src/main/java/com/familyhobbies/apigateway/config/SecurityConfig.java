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

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            .cors(ServerHttpSecurity.CorsSpec::disable)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/api/v1/auth/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/associations/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/activities/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/associations/search").permitAll()
                .pathMatchers("/api/v1/payments/webhook/**").permitAll()
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()
                .pathMatchers("/api/v1/users/**").hasRole("ADMIN")
                .anyExchange().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build();
    }
}
