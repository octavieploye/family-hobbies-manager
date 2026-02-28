package com.familyhobbies.userservice.adapter;

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

class PaymentServiceClientTest {

    private MockWebServer mockWebServer;
    private PaymentServiceClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/").toString();
        client = new PaymentServiceClient(WebClient.builder(), baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("Should call DELETE endpoint successfully")
    void shouldCallDeleteEndpointSuccessfully() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        client.cleanupUserData(99L);

        RecordedRequest request = mockWebServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("DELETE");
        assertThat(request.getPath()).isEqualTo("/api/v1/internal/users/99/data");
    }

    @Test
    @DisplayName("Should throw ExternalApiException on error response")
    void shouldThrowOnErrorResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        assertThatThrownBy(() -> client.cleanupUserData(99L))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("payment-service");
    }

    @Test
    @DisplayName("Should throw ExternalApiException when service is unreachable")
    void shouldThrowWhenUnreachable() throws IOException {
        mockWebServer.shutdown(); // force unreachable

        assertThatThrownBy(() -> client.cleanupUserData(99L))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("payment-service unreachable");
    }
}
