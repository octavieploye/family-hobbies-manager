package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.SubscriptionCancelledEvent;
import com.familyhobbies.common.event.SubscriptionCreatedEvent;
import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import com.familyhobbies.notificationservice.entity.enums.NotificationType;
import com.familyhobbies.notificationservice.service.EmailService;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Kafka consumer for subscription-related events.
 * Creates subscription notifications and sends confirmation/cancellation emails.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionEventConsumer {

    private final NotificationCreationService notificationCreationService;
    private final EmailService emailService;

    @KafkaListener(topics = "family-hobbies.subscription.created", groupId = "notification-service-group")
    public void handleSubscriptionCreated(SubscriptionCreatedEvent event) {
        log.info("Received SubscriptionCreatedEvent: subscriptionId={}, userId={}",
                event.getSubscriptionId(), event.getUserId());

        notificationCreationService.createNotification(
                event.getUserId(),
                NotificationType.IN_APP,
                NotificationCategory.SUBSCRIPTION,
                "Inscription confirmee",
                "Votre inscription a ete confirmee avec succes. Type : " + event.getSubscriptionType(),
                String.valueOf(event.getSubscriptionId()),
                "SUBSCRIPTION"
        );

        try {
            emailService.sendTemplatedEmail(
                    null, // Email resolved upstream
                    "SUBSCRIPTION_CREATED",
                    Map.of(
                            "subscriptionType", event.getSubscriptionType(),
                            "subscriptionId", event.getSubscriptionId().toString()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to send subscription created email for subscriptionId {}: {}",
                    event.getSubscriptionId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "family-hobbies.subscription.cancelled", groupId = "notification-service-group")
    public void handleSubscriptionCancelled(SubscriptionCancelledEvent event) {
        log.info("Received SubscriptionCancelledEvent: subscriptionId={}, userId={}",
                event.getSubscriptionId(), event.getUserId());

        notificationCreationService.createNotification(
                event.getUserId(),
                NotificationType.IN_APP,
                NotificationCategory.SUBSCRIPTION,
                "Inscription annulee",
                "Votre inscription a ete annulee. Raison : " + event.getCancellationReason(),
                String.valueOf(event.getSubscriptionId()),
                "SUBSCRIPTION"
        );

        try {
            emailService.sendTemplatedEmail(
                    null, // Email resolved upstream
                    "SUBSCRIPTION_CANCELLED",
                    Map.of(
                            "cancellationReason", event.getCancellationReason(),
                            "subscriptionId", event.getSubscriptionId().toString()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to send subscription cancelled email for subscriptionId {}: {}",
                    event.getSubscriptionId(), e.getMessage(), e);
        }
    }
}
