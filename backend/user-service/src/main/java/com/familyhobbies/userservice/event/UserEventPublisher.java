package com.familyhobbies.userservice.event;

import com.familyhobbies.common.event.UserDeletedEvent;
import com.familyhobbies.common.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes user-related domain events to Kafka topics.
 *
 * Uses fire-and-forget pattern: Kafka failures are logged but never
 * re-thrown, so that core user operations (registration, deletion, etc.) are
 * never blocked by messaging infrastructure issues.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventPublisher {

    private static final String USER_REGISTERED_TOPIC = "family-hobbies.user.registered";
    private static final String USER_DELETED_TOPIC = "family-hobbies.user.deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a UserRegisteredEvent to the Kafka topic.
     * Fire-and-forget: logs errors but never throws.
     *
     * @param event the user registered event to publish
     */
    public void publishUserRegistered(UserRegisteredEvent event) {
        try {
            kafkaTemplate.send(USER_REGISTERED_TOPIC,
                    String.valueOf(event.getUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserRegisteredEvent for userId={}: {}",
                                event.getUserId(), ex.getMessage());
                    } else {
                        log.info("Published UserRegisteredEvent for userId={} to topic={}",
                                event.getUserId(), USER_REGISTERED_TOPIC);
                    }
                });
        } catch (Exception e) {
            // Fire-and-forget: log but do NOT re-throw
            log.error("Failed to send UserRegisteredEvent for userId={}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Publishes a UserDeletedEvent to the Kafka topic.
     * Fire-and-forget: logs errors but never throws.
     *
     * @param event the user deleted event to publish
     */
    public void publishUserDeleted(UserDeletedEvent event) {
        try {
            kafkaTemplate.send(USER_DELETED_TOPIC,
                    String.valueOf(event.getUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserDeletedEvent for userId={}: {}",
                                event.getUserId(), ex.getMessage());
                    } else {
                        log.info("Published UserDeletedEvent for userId={} to topic={}",
                                event.getUserId(), USER_DELETED_TOPIC);
                    }
                });
        } catch (Exception e) {
            // Fire-and-forget: log but do NOT re-throw
            log.error("Failed to send UserDeletedEvent for userId={}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}
