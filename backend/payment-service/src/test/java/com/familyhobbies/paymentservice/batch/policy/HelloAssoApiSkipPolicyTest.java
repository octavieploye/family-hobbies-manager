package com.familyhobbies.paymentservice.batch.policy;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HelloAssoApiSkipPolicyTest {

    @Test
    @DisplayName("Should skip ExternalApiException when under max skip count")
    void shouldSkipExternalApiException() {
        HelloAssoApiSkipPolicy policy = new HelloAssoApiSkipPolicy(10);
        ExternalApiException exception = new ExternalApiException("API down", "HelloAsso", 503);

        assertThat(policy.shouldSkip(exception, 0)).isTrue();
        assertThat(policy.shouldSkip(exception, 5)).isTrue();
        assertThat(policy.shouldSkip(exception, 9)).isTrue();
    }

    @Test
    @DisplayName("Should not skip ExternalApiException when max skip count reached")
    void shouldNotSkipWhenMaxReached() {
        HelloAssoApiSkipPolicy policy = new HelloAssoApiSkipPolicy(5);
        ExternalApiException exception = new ExternalApiException("API down", "HelloAsso", 503);

        assertThat(policy.shouldSkip(exception, 5)).isFalse();
        assertThat(policy.shouldSkip(exception, 10)).isFalse();
    }

    @Test
    @DisplayName("Should never skip non-ExternalApiException")
    void shouldNotSkipNonApiExceptions() {
        HelloAssoApiSkipPolicy policy = new HelloAssoApiSkipPolicy(100);

        assertThat(policy.shouldSkip(new RuntimeException("unexpected"), 0)).isFalse();
        assertThat(policy.shouldSkip(new NullPointerException(), 0)).isFalse();
        assertThat(policy.shouldSkip(new IllegalStateException("bad state"), 0)).isFalse();
    }

    @Test
    @DisplayName("Should skip unlimited when maxSkipCount is -1")
    void shouldSkipUnlimited() {
        HelloAssoApiSkipPolicy policy = new HelloAssoApiSkipPolicy(-1);
        ExternalApiException exception = new ExternalApiException("API down", "HelloAsso", 503);

        assertThat(policy.shouldSkip(exception, 0)).isTrue();
        assertThat(policy.shouldSkip(exception, 100)).isTrue();
        assertThat(policy.shouldSkip(exception, 999999)).isTrue();
    }
}
