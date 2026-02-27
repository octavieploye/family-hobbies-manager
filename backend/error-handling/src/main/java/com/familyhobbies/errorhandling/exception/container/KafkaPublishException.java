package com.familyhobbies.errorhandling.exception.container;

import com.familyhobbies.errorhandling.dto.ErrorCode;
import com.familyhobbies.errorhandling.exception.BaseException;

/**
 * Thrown when a Kafka message fails to publish (HTTP 503).
 */
public class KafkaPublishException extends BaseException {

    private final String topic;

    public KafkaPublishException(String message, String topic) {
        super(message, ErrorCode.KAFKA_PUBLISH_FAILURE);
        this.topic = topic;
    }

    public KafkaPublishException(String message, String topic, Throwable cause) {
        super(message, ErrorCode.KAFKA_PUBLISH_FAILURE, cause);
        this.topic = topic;
    }

    /**
     * Static factory that builds a descriptive message from the topic name and cause.
     * <p>Example: {@code KafkaPublishException.forTopic("payment-events", cause)}
     * produces message {@code "Failed to publish message to topic: payment-events"}.
     */
    public static KafkaPublishException forTopic(String topic, Throwable cause) {
        String message = "Failed to publish message to topic: " + topic;
        return new KafkaPublishException(message, topic, cause);
    }

    public String getTopic() {
        return topic;
    }
}
