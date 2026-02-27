package com.familyhobbies.apigateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for SecurityHeadersConfig on the API Gateway.
 *
 * Verifies that all 7 HTTP security headers are present on every response,
 * including responses to public endpoints (no authentication required).
 *
 * Uses a public endpoint (/actuator/health) so that no JWT token is needed.
 * Eureka is disabled for test isolation.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "cors.allowed-origins[0]=http://localhost:4200",
        "cors.allowed-methods[0]=GET",
        "cors.allowed-headers[0]=Authorization",
        "cors.exposed-headers[0]=X-Total-Count",
        "cors.allow-credentials=true",
        "cors.max-age=3600",
        "eureka.client.enabled=false"
    }
)
@AutoConfigureWebTestClient
class SecurityHeadersConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("should include X-Content-Type-Options header in response")
    void should_includeXContentTypeOptions() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals("X-Content-Type-Options", "nosniff");
    }

    @Test
    @DisplayName("should include X-Frame-Options header in response")
    void should_includeXFrameOptions() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals("X-Frame-Options", "DENY");
    }

    @Test
    @DisplayName("should include X-XSS-Protection header in response")
    void should_includeXXssProtection() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals("X-XSS-Protection", "0");
    }

    @Test
    @DisplayName("should include Strict-Transport-Security header in response")
    void should_includeStrictTransportSecurity() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals(
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");
    }

    @Test
    @DisplayName("should include Content-Security-Policy header in response")
    void should_includeContentSecurityPolicy() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().exists("Content-Security-Policy");
    }

    @Test
    @DisplayName("should include Referrer-Policy header in response")
    void should_includeReferrerPolicy() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals(
                "Referrer-Policy", "strict-origin-when-cross-origin");
    }

    @Test
    @DisplayName("should include Permissions-Policy header in response")
    void should_includePermissionsPolicy() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals(
                "Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    }
}
