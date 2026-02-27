package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.UserRegisteredEvent;
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
 * Kafka consumer for user-related events.
 * Creates welcome notifications and sends welcome emails.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventConsumer {

    private final NotificationCreationService notificationCreationService;
    private final EmailService emailService;

    @KafkaListener(topics = "family-hobbies.user.registered", groupId = "notification-service-group")
    public void handleUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegisteredEvent: userId={}, email={}", event.getUserId(), event.getEmail());

        notificationCreationService.createNotification(
                event.getUserId(),
                NotificationType.IN_APP,
                NotificationCategory.WELCOME,
                "Bienvenue sur Family Hobbies Manager !",
                "Bonjour " + event.getFirstName() + ", bienvenue sur notre plateforme. " +
                        "Decouvrez les associations pres de chez vous.",
                String.valueOf(event.getUserId()),
                "USER"
        );

        try {
            emailService.sendTemplatedEmail(
                    event.getEmail(),
                    "WELCOME",
                    Map.of(
                            "firstName", event.getFirstName(),
                            "lastName", event.getLastName(),
                            "email", event.getEmail()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", event.getEmail(), e.getMessage(), e);
        }
    }
}
