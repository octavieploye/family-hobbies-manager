package com.familyhobbies.associationservice.adapter;

import com.familyhobbies.common.config.HelloAssoProperties;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link HelloAssoTokenManager}.
 * Uses MockWebServer to simulate the HelloAsso OAuth2 token endpoint.
 *
 * Story: S5-001 -- HelloAssoTokenManager
 * Tests: 10 test methods
 */
class HelloAssoTokenManagerTest {

    private MockWebServer mockWebServer;
    private HelloAssoTokenManager tokenManager;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        HelloAssoProperties properties = new HelloAssoProperties();
        properties.setBaseUrl(baseUrl);
        properties.setClientId("test-client-id");
        properties.setClientSecret("test-client-secret");
        properties.setTokenUrl(baseUrl + "oauth2/token");
        properties.setConnectTimeout(5000);
        properties.setReadTimeout(10000);

        WebClient.Builder builder = WebClient.builder();
        tokenManager = new HelloAssoTokenManager(properties, builder);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ── Token Acquisition ──────────────────────────────────────────────

    @Test
    @DisplayName("should_returnAccessToken_when_tokenEndpointReturnsValidResponse")
    void should_returnAccessToken_when_tokenEndpointReturnsValidResponse() {
        mockWebServer.enqueue(new MockResponse()
            .setBody(tokenResponseJson("test-token-123", 3600))
            .addHeader("Content-Type", "application/json"));

        String token = tokenManager.getValidToken();

        assertThat(token).isEqualTo("test-token-123");
    }

    @Test
    @DisplayName("should_sendFormUrlEncodedRequest_when_fetchingToken")
    void should_sendFormUrlEncodedRequest_when_fetchingToken() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse()
            .setBody(tokenResponseJson("test-token", 3600))
            .addHeader("Content-Type", "application/json"));

        tokenManager.getValidToken();

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Content-Type"))
            .contains("application/x-www-form-urlencoded");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("grant_type=client_credentials");
        assertThat(body).contains("client_id=test-client-id");
        assertThat(body).contains("client_secret=test-client-secret");
    }

    // ── Token Caching ──────────────────────────────────────────────────

    @Test
    @DisplayName("should_returnCachedToken_when_tokenStillValid")
    void should_returnCachedToken_when_tokenStillValid() {
        mockWebServer.enqueue(new MockResponse()
            .setBody(tokenResponseJson("cached-token", 3600))
            .addHeader("Content-Type", "application/json"));

        String first = tokenManager.getValidToken();
        String second = tokenManager.getValidToken();

        assertThat(first).isEqualTo("cached-token");
        assertThat(second).isEqualTo("cached-token");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should_makeSingleApiCall_when_calledMultipleTimes")
    void should_makeSingleApiCall_when_calledMultipleTimes() {
        mockWebServer.enqueue(new MockResponse()
            .setBody(tokenResponseJson("single-call-token", 7200))
            .addHeader("Content-Type", "application/json"));

        for (int i = 0; i < 5; i++) {
            tokenManager.getValidToken();
        }

        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    // ── Token Refresh ──────────────────────────────────────────────────

    @Test
    @DisplayName("should_fetchNewToken_when_tokenExpiresWithin60Seconds")
    void should_fetchNewToken_when_tokenExpiresWithin60Seconds() {
        // First token expires in 30 seconds (within the 60s buffer)
        mockWebServer.enqueue(new MockResponse()
            .setBody(tokenResponseJson("expiring-token", 30))
            .addHeader("Content-Type", "application/json"));
        // Second token with longer expiry
        mockWebServer.enqueue(new MockResponse()
            .setBody(tokenResponseJson("fresh-token", 3600))
            .addHeader("Content-Type", "application/json"));

        String first = tokenManager.getValidToken();
        // First call got a token that expires in 30s, which is within the 60s buffer
        // So the next call should fetch a new token
        String second = tokenManager.getValidToken();

        assertThat(first).isEqualTo("expiring-token");
        assertThat(second).isEqualTo("fresh-token");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("should_invalidateCache_when_forceRefreshCalled")
    void should_invalidateCache_when_forceRefreshCalled() {
        mockWebServer.enqueue(new MockResponse()
            .setBody(tokenResponseJson("original-token", 3600))
            .addHeader("Content-Type", "application/json"));
        mockWebServer.enqueue(new MockResponse()
            .setBody(tokenResponseJson("refreshed-token", 3600))
            .addHeader("Content-Type", "application/json"));

        String first = tokenManager.getValidToken();
        tokenManager.forceRefresh();
        String second = tokenManager.getValidToken();

        assertThat(first).isEqualTo("original-token");
        assertThat(second).isEqualTo("refreshed-token");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    // ── Error Handling ─────────────────────────────────────────────────

    @Test
    @DisplayName("should_throwExternalApiException_when_tokenEndpointReturns401")
    void should_throwExternalApiException_when_tokenEndpointReturns401() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody("{\"error\":\"unauthorized\"}")
            .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> tokenManager.getValidToken())
            .isInstanceOf(ExternalApiException.class)
            .hasMessageContaining("Failed to obtain HelloAsso token");
    }

    @Test
    @DisplayName("should_throwExternalApiException_when_tokenEndpointReturns500")
    void should_throwExternalApiException_when_tokenEndpointReturns500() {
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("{\"error\":\"server_error\"}")
            .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> tokenManager.getValidToken())
            .isInstanceOf(ExternalApiException.class)
            .hasMessageContaining("Failed to obtain HelloAsso token");
    }

    @Test
    @DisplayName("should_throwExternalApiException_when_responseBodyMissing")
    void should_throwExternalApiException_when_responseBodyMissing() {
        mockWebServer.enqueue(new MockResponse()
            .setBody("{}")
            .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> tokenManager.getValidToken())
            .isInstanceOf(ExternalApiException.class)
            .hasMessageContaining("empty or missing access_token");
    }

    @Test
    @DisplayName("should_throwExternalApiException_when_networkFailure")
    void should_throwExternalApiException_when_networkFailure() throws IOException {
        mockWebServer.shutdown();

        assertThatThrownBy(() -> tokenManager.getValidToken())
            .isInstanceOf(ExternalApiException.class)
            .hasMessageContaining("Failed to obtain HelloAsso token");
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private String tokenResponseJson(String accessToken, long expiresIn) {
        return String.format("""
            {
              "access_token": "%s",
              "token_type": "bearer",
              "expires_in": %d
            }
            """, accessToken, expiresIn);
    }
}
