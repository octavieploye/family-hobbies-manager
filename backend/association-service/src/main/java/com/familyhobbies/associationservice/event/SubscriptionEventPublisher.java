package com.familyhobbies.associationservice.event;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.common.event.SubscriptionCancelledEvent;
import com.familyhobbies.common.event.SubscriptionCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes subscription-related domain events to Kafka topics.
 *
 * Uses fire-and-forget pattern: Kafka failures are logged but never
 * re-thrown, so that core subscription operations are never blocked
 * by messaging infrastructure issues.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEventPublisher {

    private static final String SUBSCRIPTION_CREATED_TOPIC = "family-hobbies.subscription.created";
    private static final String SUBSCRIPTION_CANCELLED_TOPIC = "family-hobbies.subscription.cancelled";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a SubscriptionCreatedEvent to Kafka.
     * Fire-and-forget: logs errors but never throws.
     *
     * @param subscription the newly created subscription entity
     */
    public void publishSubscriptionCreated(Subscription subscription) {
        try {
            Long associationId = subscription.getActivity() != null
                && subscription.getActivity().getAssociation() != null
                ? subscription.getActivity().getAssociation().getId()
                : null;

            SubscriptionCreatedEvent event = new SubscriptionCreatedEvent(
                subscription.getId(),
                subscription.getUserId(),
                subscription.getFamilyId(),
                associationId,
                subscription.getSubscriptionType().name()
            );

            kafkaTemplate.send(SUBSCRIPTION_CREATED_TOPIC,
                    String.valueOf(subscription.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish SubscriptionCreatedEvent for subscriptionId={}: {}",
                                subscription.getId(), ex.getMessage());
                    } else {
                        log.info("Published SubscriptionCreatedEvent for subscriptionId={} to topic={}",
                                subscription.getId(), SUBSCRIPTION_CREATED_TOPIC);
                    }
                });
        } catch (Exception e) {
            log.error("Failed to send SubscriptionCreatedEvent for subscriptionId={}: {}",
                    subscription.getId(), e.getMessage(), e);
        }
    }

    /**
     * Publishes a SubscriptionCancelledEvent to Kafka.
     * Fire-and-forget: logs errors but never throws.
     *
     * @param subscription the cancelled subscription entity
     */
    public void publishSubscriptionCancelled(Subscription subscription) {
        try {
            SubscriptionCancelledEvent event = new SubscriptionCancelledEvent(
                subscription.getId(),
                subscription.getUserId(),
                subscription.getCancellationReason()
            );

            kafkaTemplate.send(SUBSCRIPTION_CANCELLED_TOPIC,
                    String.valueOf(subscription.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish SubscriptionCancelledEvent for subscriptionId={}: {}",
                                subscription.getId(), ex.getMessage());
                    } else {
                        log.info("Published SubscriptionCancelledEvent for subscriptionId={} to topic={}",
                                subscription.getId(), SUBSCRIPTION_CANCELLED_TOPIC);
                    }
                });
        } catch (Exception e) {
            log.error("Failed to send SubscriptionCancelledEvent for subscriptionId={}: {}",
                    subscription.getId(), e.getMessage(), e);
        }
    }
}
