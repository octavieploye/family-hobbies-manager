package com.familyhobbies.associationservice.config;

import org.springframework.context.annotation.Configuration;

/**
 * Resilience4j configuration marker for HelloAsso API calls.
 * <p>
 * Actual circuit breaker and retry instances are configured via application.yml
 * and auto-configured by {@code resilience4j-spring-boot3}.
 * <p>
 * Configuration:
 * <ul>
 *   <li>Circuit breaker "helloasso": 50% failure rate, 10-call sliding window, 30s wait in open state</li>
 *   <li>Retry "helloasso": 3 attempts, 500ms exponential backoff (multiplier 2)</li>
 *   <li>Time limiter "helloasso": 10s timeout</li>
 * </ul>
 */
@Configuration
public class ResilienceConfig {
    // Configuration is YAML-driven via resilience4j-spring-boot3 auto-configuration.
    // See application.yml for instance definitions.
}
