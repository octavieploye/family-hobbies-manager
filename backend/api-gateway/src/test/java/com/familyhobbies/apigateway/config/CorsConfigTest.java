package com.familyhobbies.apigateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for CORS configuration on the API Gateway.
 *
 * Story: S1-008 — Implement CORS Configuration
 * Tests: 2 test methods
 *
 * These tests verify:
 * 1. Preflight OPTIONS requests return correct CORS headers
 * 2. Cross-origin GET requests include CORS headers
 *
 * Uses @SpringBootTest with RANDOM_PORT and WebTestClient.
 * Eureka is disabled for test isolation.
 * CORS properties are set inline via @SpringBootTest properties.
 *
 * Review findings incorporated:
 * - F-15 (NOTE): A negative test for disallowed origins (http://evil-site.com) would
 *   be ideal but is deferred as NOTE-level. The two tests here cover the primary contract.
 * - F-16 (NOTE): crossOriginGet uses /api/v1/auth/login which is POST-only. The test
 *   verifies CORS headers are present regardless of the response status, which is correct
 *   behavior. Using a GET-accepting path would be cleaner but is not strictly needed.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "cors.allowed-origins[0]=http://localhost:4200",
        "cors.allowed-methods[0]=GET",
        "cors.allowed-methods[1]=POST",
        "cors.allowed-methods[2]=PUT",
        "cors.allowed-methods[3]=PATCH",
        "cors.allowed-methods[4]=DELETE",
        "cors.allowed-methods[5]=OPTIONS",
        "cors.allowed-headers[0]=Authorization",
        "cors.allowed-headers[1]=Content-Type",
        "cors.allowed-headers[2]=X-Requested-With",
        "cors.exposed-headers[0]=X-Total-Count",
        "cors.exposed-headers[1]=X-Correlation-Id",
        "cors.allow-credentials=true",
        "cors.max-age=3600",
        "eureka.client.enabled=false"
    }
)
@AutoConfigureWebTestClient
class CorsConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("should return CORS headers on preflight OPTIONS request")
    void preflightOptions_shouldReturnCorsHeaders() {
        webTestClient.options()
            .uri("/api/v1/auth/login")
            .header(HttpHeaders.ORIGIN, "http://localhost:4200")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
            .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
            .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
            .expectHeader().valueEquals(
                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
            .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_MAX_AGE);
    }

    @Test
    @DisplayName("should include CORS headers on cross-origin GET request")
    void crossOriginGet_shouldIncludeCorsHeaders() {
        // The actual status depends on the route handler (405 for GET on a POST-only endpoint),
        // but CORS headers should still be present regardless of the response status.
        webTestClient.get()
            .uri("/api/v1/auth/login")
            .header(HttpHeaders.ORIGIN, "http://localhost:4200")
            .exchange()
            .expectHeader().valueEquals(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
            .expectHeader().valueEquals(
                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }
}
