package com.familyhobbies.common.monitoring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link KafkaMetricsService}.
 */
@DisplayName("KafkaMetricsService")
class KafkaMetricsServiceTest {

    private MeterRegistry meterRegistry;
    private KafkaMetricsService kafkaMetricsService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        kafkaMetricsService = new KafkaMetricsService(meterRegistry, "user-service");
    }

    @Nested
    @DisplayName("Published Events")
    class PublishedEvents {

        @Test
        @DisplayName("should_increment_published_counter_when_event_published")
        void should_increment_published_counter_when_event_published() {
            // When
            kafkaMetricsService.recordEventPublished(
                    "user-events", "UserRegisteredEvent");

            // Then
            Counter counter = meterRegistry.find("kafka.events.published")
                    .tag("topic", "user-events")
                    .tag("event_type", "UserRegisteredEvent")
                    .tag("service", "user-service")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should_increment_published_counter_multiple_times")
        void should_increment_published_counter_multiple_times() {
            // When
            kafkaMetricsService.recordEventPublished(
                    "user-events", "UserRegisteredEvent");
            kafkaMetricsService.recordEventPublished(
                    "user-events", "UserRegisteredEvent");
            kafkaMetricsService.recordEventPublished(
                    "user-events", "UserRegisteredEvent");

            // Then
            Counter counter = meterRegistry.find("kafka.events.published")
                    .tag("topic", "user-events")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(3.0);
        }
    }

    @Nested
    @DisplayName("Consumed Events")
    class ConsumedEvents {

        @Test
        @DisplayName("should_increment_consumed_counter_when_event_consumed")
        void should_increment_consumed_counter_when_event_consumed() {
            // When
            kafkaMetricsService.recordEventConsumed(
                    "payment-events", "PaymentCompletedEvent");

            // Then
            Counter counter = meterRegistry.find("kafka.events.consumed")
                    .tag("topic", "payment-events")
                    .tag("event_type", "PaymentCompletedEvent")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Failed Events")
    class FailedEvents {

        @Test
        @DisplayName("should_increment_failed_counter_when_event_fails")
        void should_increment_failed_counter_when_event_fails() {
            // When
            kafkaMetricsService.recordEventFailed(
                    "user-events", "UserRegisteredEvent", "deserialization_error");

            // Then
            Counter counter = meterRegistry.find("kafka.events.failed")
                    .tag("topic", "user-events")
                    .tag("reason", "deserialization_error")
                    .counter();
            assertThat(counter).isNotNull();
            assertThat(counter.count()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("Service Tag")
    class ServiceTag {

        @Test
        @DisplayName("should_tag_all_counters_with_service_name")
        void should_tag_all_counters_with_service_name() {
            // When
            kafkaMetricsService.recordEventPublished("t1", "E1");
            kafkaMetricsService.recordEventConsumed("t2", "E2");
            kafkaMetricsService.recordEventFailed("t3", "E3", "error");

            // Then
            assertThat(meterRegistry.find("kafka.events.published")
                    .tag("service", "user-service").counter()).isNotNull();
            assertThat(meterRegistry.find("kafka.events.consumed")
                    .tag("service", "user-service").counter()).isNotNull();
            assertThat(meterRegistry.find("kafka.events.failed")
                    .tag("service", "user-service").counter()).isNotNull();
        }
    }
}
