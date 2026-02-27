package com.familyhobbies.paymentservice.adapter;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for HelloAssoCheckoutClient using MockWebServer.
 *
 * Story: S5-004 -- HelloAsso Checkout Integration
 * Tests: 5 test methods
 */
@ExtendWith(MockitoExtension.class)
class HelloAssoCheckoutClientTest {

    private MockWebServer mockWebServer;
    private HelloAssoCheckoutClient checkoutClient;

    @Mock
    private HelloAssoTokenManager tokenManager;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/v5").toString())
                .build();

        checkoutClient = new HelloAssoCheckoutClient(webClient, tokenManager);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("should_returnCheckoutUrl_when_successfulCheckout")
    void should_returnCheckoutUrl_when_successfulCheckout() {
        // Given
        when(tokenManager.getAccessToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\": \"checkout-123\", \"redirectUrl\": \"https://checkout.helloasso.com/pay/123\"}"));

        // When
        HelloAssoCheckoutClient.HelloAssoCheckoutResponse response = checkoutClient.initiateCheckout(
                "test-org", 5000, "Test payment",
                "https://cancel.url", "https://error.url", "https://return.url");

        // Then
        assertThat(response.id()).isEqualTo("checkout-123");
        assertThat(response.redirectUrl()).isEqualTo("https://checkout.helloasso.com/pay/123");
    }

    @Test
    @DisplayName("should_sendBearerToken_when_callingHelloAsso")
    void should_sendBearerToken_when_callingHelloAsso() throws InterruptedException {
        // Given
        when(tokenManager.getAccessToken()).thenReturn("my-bearer-token-xyz");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\": \"checkout-456\", \"redirectUrl\": \"https://checkout.helloasso.com/pay/456\"}"));

        // When
        checkoutClient.initiateCheckout(
                "test-org", 5000, "Test payment",
                "https://cancel.url", "https://error.url", "https://return.url");

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer my-bearer-token-xyz");
    }

    @Test
    @DisplayName("should_sendAmountInBody_when_initiatingCheckout")
    void should_sendAmountInBody_when_initiatingCheckout() throws InterruptedException {
        // Given
        when(tokenManager.getAccessToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\": \"checkout-789\", \"redirectUrl\": \"https://checkout.helloasso.com/pay/789\"}"));

        // When
        checkoutClient.initiateCheckout(
                "test-org", 7500, "Cotisation",
                "https://cancel.url", "https://error.url", "https://return.url");

        // Then
        RecordedRequest request = mockWebServer.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"totalAmount\":7500");
        assertThat(body).contains("\"initialAmount\":7500");
    }

    @Test
    @DisplayName("should_throwExternalApiException_when_4xxError")
    void should_throwExternalApiException_when_4xxError() {
        // Given
        when(tokenManager.getAccessToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"Bad Request\"}"));

        // When / Then
        assertThatThrownBy(() -> checkoutClient.initiateCheckout(
                "test-org", 5000, "Test payment",
                "https://cancel.url", "https://error.url", "https://return.url"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("HelloAsso");
    }

    @Test
    @DisplayName("should_throwExternalApiException_when_5xxError")
    void should_throwExternalApiException_when_5xxError() {
        // Given
        when(tokenManager.getAccessToken()).thenReturn("test-token");
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(503)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\": \"Service Unavailable\"}"));

        // When / Then
        assertThatThrownBy(() -> checkoutClient.initiateCheckout(
                "test-org", 5000, "Test payment",
                "https://cancel.url", "https://error.url", "https://return.url"))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("HelloAsso");
    }
}
