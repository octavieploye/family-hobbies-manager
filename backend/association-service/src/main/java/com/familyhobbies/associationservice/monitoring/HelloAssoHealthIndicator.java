package com.familyhobbies.associationservice.monitoring;

import com.familyhobbies.common.config.HelloAssoProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Health indicator for the HelloAsso API v5.
 *
 * <p>Performs a lightweight GET to the HelloAsso public endpoint to verify
 * API availability. Reports UP if a 2xx/4xx response is received (API is
 * reachable), DOWN if a 5xx or network error occurs.
 *
 * <p>This indicator is registered only in association-service since it is
 * the only service that directly calls the HelloAsso API.
 *
 * <p>Timeout: 5 seconds to avoid blocking the health endpoint.
 */
@Component("helloAssoHealthIndicator")
public class HelloAssoHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoHealthIndicator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String baseUrl;

    public HelloAssoHealthIndicator(WebClient.Builder webClientBuilder,
                                     HelloAssoProperties properties) {
        this.baseUrl = properties.getBaseUrl();
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public Health health() {
        try {
            HttpStatusCode statusCode = webClient.get()
                    .uri("/public/organizations?pageSize=1")
                    .exchangeToMono(response ->
                            Mono.just(response.statusCode()))
                    .block(TIMEOUT);

            if (statusCode != null && !statusCode.is5xxServerError()) {
                return Health.up()
                        .withDetail("baseUrl", baseUrl)
                        .withDetail("statusCode", statusCode.value())
                        .build();
            }

            return Health.down()
                    .withDetail("baseUrl", baseUrl)
                    .withDetail("statusCode", statusCode != null ? statusCode.value() : "null")
                    .build();

        } catch (Exception ex) {
            log.warn("HelloAsso health check failed: {}", ex.getMessage());
            String errorMessage = ex.getMessage() != null
                    ? ex.getMessage()
                    : ex.getClass().getSimpleName();
            return Health.down()
                    .withDetail("baseUrl", baseUrl)
                    .withDetail("error", errorMessage)
                    .build();
        }
    }
}
