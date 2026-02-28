package com.familyhobbies.common.monitoring.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaHealthIndicator}.
 *
 * <p>Tests use invalid bootstrap servers to trigger DOWN status,
 * and verify the health response structure. Integration tests with
 * a real Kafka broker are done in the verification checklist.
 */
@DisplayName("KafkaHealthIndicator")
class KafkaHealthIndicatorTest {

    @Test
    @DisplayName("should_return_down_when_broker_unreachable")
    void should_return_down_when_broker_unreachable() {
        // Given -- non-existent broker
        KafkaHealthIndicator indicator =
                new KafkaHealthIndicator("localhost:19092");

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("bootstrapServers");
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("bootstrapServers"))
                .isEqualTo("localhost:19092");
    }

    @Test
    @DisplayName("should_include_bootstrap_servers_in_details_when_down")
    void should_include_bootstrap_servers_in_details_when_down() {
        // Given
        KafkaHealthIndicator indicator =
                new KafkaHealthIndicator("invalid-host:9092");

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getDetails().get("bootstrapServers"))
                .isEqualTo("invalid-host:9092");
    }

    @Test
    @DisplayName("should_include_error_message_in_details_when_down")
    void should_include_error_message_in_details_when_down() {
        // Given
        KafkaHealthIndicator indicator =
                new KafkaHealthIndicator("localhost:19092");

        // When
        Health health = indicator.health();

        // Then
        assertThat(health.getDetails().get("error")).isNotNull();
        assertThat(health.getDetails().get("error").toString()).isNotBlank();
    }
}
