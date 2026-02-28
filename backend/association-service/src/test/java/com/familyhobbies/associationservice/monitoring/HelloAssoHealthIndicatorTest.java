package com.familyhobbies.associationservice.monitoring;

import com.familyhobbies.common.config.HelloAssoProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link HelloAssoHealthIndicator}.
 * Uses MockWebServer to simulate HelloAsso API responses.
 */
@DisplayName("HelloAssoHealthIndicator")
class HelloAssoHealthIndicatorTest {

    private MockWebServer mockWebServer;
    private HelloAssoHealthIndicator indicator;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        HelloAssoProperties properties = new HelloAssoProperties();
        properties.setBaseUrl(mockWebServer.url("/v5").toString());
        properties.setClientId("test");
        properties.setClientSecret("test");
        properties.setTokenUrl(mockWebServer.url("/oauth2/token").toString());

        indicator = new HelloAssoHealthIndicator(
                WebClient.builder(), properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("should_return_up_when_api_returns_200")
    void should_return_up_when_api_returns_200() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"data\":[]}")
                .addHeader("Content-Type", "application/json"));

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("baseUrl");
        assertThat(health.getDetails()).containsEntry("statusCode", 200);
    }

    @Test
    @DisplayName("should_return_up_when_api_returns_401_because_api_is_reachable")
    void should_return_up_when_api_returns_401() {
        // Given -- 401 means API is reachable, just unauthorized
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"unauthorized\"}")
                .addHeader("Content-Type", "application/json"));

        // When
        Health health = indicator.health();

        // Then -- API is reachable, so UP
        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("should_return_down_when_api_returns_500")
    void should_return_down_when_api_returns_500() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    @DisplayName("should_return_down_when_api_unreachable")
    void should_return_down_when_api_unreachable() throws IOException {
        // Given -- shut down server
        mockWebServer.shutdown();

        HelloAssoProperties properties = new HelloAssoProperties();
        properties.setBaseUrl("http://localhost:1/v5");
        properties.setClientId("test");
        properties.setClientSecret("test");
        properties.setTokenUrl("http://localhost:1/oauth2/token");

        HelloAssoHealthIndicator deadIndicator =
                new HelloAssoHealthIndicator(WebClient.builder(), properties);

        // When
        Health health = deadIndicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }

    @Test
    @DisplayName("should_include_base_url_in_health_details")
    void should_include_base_url_in_health_details() {
        // Given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{}"));

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getDetails().get("baseUrl").toString())
                .contains("/v5");
    }
}
