package com.familyhobbies.common.monitoring.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Centralized service for recording Kafka-related Micrometer metrics.
 *
 * <p>Counters:
 * <ul>
 *   <li>{@code kafka.events.published} -- incremented when an event is sent to Kafka</li>
 *   <li>{@code kafka.events.consumed} -- incremented when an event is consumed from Kafka</li>
 *   <li>{@code kafka.events.failed} -- incremented when event processing fails</li>
 * </ul>
 *
 * <p>Tags:
 * <ul>
 *   <li>{@code topic} -- Kafka topic name</li>
 *   <li>{@code event_type} -- Event class simple name</li>
 *   <li>{@code service} -- Service name</li>
 * </ul>
 */
@Component
public class KafkaMetricsService {

    private final MeterRegistry meterRegistry;
    private final String serviceName;

    public KafkaMetricsService(MeterRegistry meterRegistry,
                               @Value("${spring.application.name:unknown}") String serviceName) {
        this.meterRegistry = meterRegistry;
        this.serviceName = serviceName;
    }

    /**
     * Increments the {@code kafka.events.published} counter.
     *
     * @param topic     the Kafka topic the event was sent to
     * @param eventType the event class simple name (e.g., "UserRegisteredEvent")
     */
    public void recordEventPublished(String topic, String eventType) {
        Counter.builder("kafka.events.published")
                .description("Total Kafka events published")
                .tag("topic", topic)
                .tag("event_type", eventType)
                .tag("service", serviceName)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increments the {@code kafka.events.consumed} counter.
     *
     * @param topic     the Kafka topic the event was consumed from
     * @param eventType the event class simple name
     */
    public void recordEventConsumed(String topic, String eventType) {
        Counter.builder("kafka.events.consumed")
                .description("Total Kafka events consumed")
                .tag("topic", topic)
                .tag("event_type", eventType)
                .tag("service", serviceName)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increments the {@code kafka.events.failed} counter.
     *
     * @param topic     the Kafka topic
     * @param eventType the event class simple name
     * @param reason    failure reason description
     */
    public void recordEventFailed(String topic, String eventType, String reason) {
        Counter.builder("kafka.events.failed")
                .description("Total Kafka event processing failures")
                .tag("topic", topic)
                .tag("event_type", eventType)
                .tag("reason", reason)
                .tag("service", serviceName)
                .register(meterRegistry)
                .increment();
    }
}
