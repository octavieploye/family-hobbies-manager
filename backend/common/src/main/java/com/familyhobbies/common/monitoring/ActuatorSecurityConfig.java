package com.familyhobbies.common.monitoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for actuator endpoints.
 *
 * <p>Public (no auth required):
 * <ul>
 *   <li>{@code /actuator/health} -- liveness probe</li>
 *   <li>{@code /actuator/health/**} -- readiness probe groups</li>
 * </ul>
 *
 * <p>Authenticated (require ADMIN role):
 * <ul>
 *   <li>{@code /actuator/info}</li>
 *   <li>{@code /actuator/metrics}</li>
 *   <li>{@code /actuator/metrics/**}</li>
 *   <li>{@code /actuator/prometheus}</li>
 * </ul>
 *
 * <p>Ordered with {@link Order}(1) to take precedence over the main security
 * filter chain for actuator paths only. Only activates in servlet (non-reactive)
 * environments.
 */
@Configuration
@Order(1)
@ConditionalOnClass(name = "org.springframework.security.config.annotation.web.builders.HttpSecurity")
public class ActuatorSecurityConfig {

    @Bean
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/info").hasRole("ADMIN")
                .requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN")
                .requestMatchers("/actuator/prometheus").hasRole("ADMIN")
                .anyRequest().hasRole("ADMIN")
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
