package com.familyhobbies.associationservice.batch.policy;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD contract tests for {@link HelloAssoSkipPolicy}.
 */
class HelloAssoSkipPolicyTest {

    private HelloAssoSkipPolicy skipPolicy;

    @BeforeEach
    void setUp() {
        skipPolicy = new HelloAssoSkipPolicy(10);
    }

    @Test
    @DisplayName("Should skip ExternalApiException with 429 (rate limit)")
    void shouldSkipRateLimitError() {
        ExternalApiException exception = new ExternalApiException(
                "Rate limit exceeded", "HelloAsso", 429);
        assertThat(skipPolicy.shouldSkip(exception, 0)).isTrue();
    }

    @Test
    @DisplayName("Should skip ExternalApiException with 503 (server unavailable)")
    void shouldSkipServerError() {
        ExternalApiException exception = new ExternalApiException(
                "Service unavailable", "HelloAsso", 503);
        assertThat(skipPolicy.shouldSkip(exception, 0)).isTrue();
    }

    @Test
    @DisplayName("Should NOT skip ExternalApiException with 400 (bad request)")
    void shouldNotSkipClientError() {
        ExternalApiException exception = new ExternalApiException(
                "Bad request", "HelloAsso", 400);
        assertThat(skipPolicy.shouldSkip(exception, 0)).isFalse();
    }

    @Test
    @DisplayName("Should NOT skip when skip count exceeds max")
    void shouldNotSkipWhenLimitReached() {
        ExternalApiException exception = new ExternalApiException(
                "Rate limit exceeded", "HelloAsso", 429);
        assertThat(skipPolicy.shouldSkip(exception, 10)).isFalse();
    }

    @Test
    @DisplayName("Should skip TimeoutException")
    void shouldSkipTimeoutException() {
        TimeoutException exception = new TimeoutException("Request timed out");
        assertThat(skipPolicy.shouldSkip(exception, 0)).isTrue();
    }

    @Test
    @DisplayName("Should skip ConnectException")
    void shouldSkipConnectException() {
        ConnectException exception = new ConnectException("Connection refused");
        assertThat(skipPolicy.shouldSkip(exception, 0)).isTrue();
    }

    @Test
    @DisplayName("Should NOT skip unknown exceptions (e.g., NullPointerException)")
    void shouldNotSkipUnknownException() {
        NullPointerException exception = new NullPointerException("unexpected");
        assertThat(skipPolicy.shouldSkip(exception, 0)).isFalse();
    }
}
