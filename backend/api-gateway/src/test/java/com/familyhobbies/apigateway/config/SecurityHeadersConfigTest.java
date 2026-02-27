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
 *
 * M-015/M-016: Header values are now externalized via application.yml properties.
 * The helloasso.base-url property is set to sandbox URL for tests.
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
        "eureka.client.enabled=false",
        "helloasso.base-url=https://api.helloasso-sandbox.com",
        "security.headers.content-type-options=nosniff",
        "security.headers.frame-options=DENY",
        "security.headers.xss-protection=0",
        "security.headers.strict-transport-security=max-age=31536000; includeSubDomains; preload",
        "security.headers.referrer-policy=strict-origin-when-cross-origin",
        "security.headers.permissions-policy=camera=(), microphone=(), geolocation=()"
    }
)
@AutoConfigureWebTestClient
class SecurityHeadersConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("should include X-Content-Type-Options header in response")
    void should_includeXContentTypeOptions_when_responseReturned() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals("X-Content-Type-Options", "nosniff");
    }

    @Test
    @DisplayName("should include X-Frame-Options header in response")
    void should_includeXFrameOptions_when_responseReturned() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals("X-Frame-Options", "DENY");
    }

    @Test
    @DisplayName("should include X-XSS-Protection header in response")
    void should_includeXXssProtection_when_responseReturned() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals("X-XSS-Protection", "0");
    }

    @Test
    @DisplayName("should include Strict-Transport-Security header in response")
    void should_includeStrictTransportSecurity_when_responseReturned() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals(
                "Strict-Transport-Security",
                "max-age=31536000; includeSubDomains; preload");
    }

    @Test
    @DisplayName("should include Content-Security-Policy header in response")
    void should_includeContentSecurityPolicy_when_responseReturned() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().exists("Content-Security-Policy");
    }

    @Test
    @DisplayName("should include Referrer-Policy header in response")
    void should_includeReferrerPolicy_when_responseReturned() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals(
                "Referrer-Policy", "strict-origin-when-cross-origin");
    }

    @Test
    @DisplayName("should include Permissions-Policy header in response")
    void should_includePermissionsPolicy_when_responseReturned() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().valueEquals(
                "Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    }

    @Test
    @DisplayName("should include sandbox HelloAsso URL in CSP connect-src directive")
    void should_includeHelloAssoSandboxUrlInCsp_when_responseReturned() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectHeader().value("Content-Security-Policy", csp ->
                org.junit.jupiter.api.Assertions.assertTrue(
                    csp.contains("https://api.helloasso-sandbox.com"),
                    "CSP connect-src should contain the sandbox HelloAsso URL, but was: " + csp
                )
            );
    }
}
