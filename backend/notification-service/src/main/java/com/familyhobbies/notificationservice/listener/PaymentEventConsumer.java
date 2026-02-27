package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
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
 * Kafka consumer for payment-related events.
 * Creates payment notifications and sends payment confirmation/failure emails.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final NotificationCreationService notificationCreationService;
    private final EmailService emailService;

    @KafkaListener(topics = "family-hobbies.payment.completed", groupId = "notification-service-group")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent: paymentId={}, familyId={}",
                event.getPaymentId(), event.getFamilyId());

        notificationCreationService.createNotification(
                event.getFamilyId(),
                NotificationType.IN_APP,
                NotificationCategory.PAYMENT,
                "Paiement confirme",
                "Votre paiement de " + event.getAmount() + " " + event.getCurrency() +
                        " a ete confirme avec succes.",
                String.valueOf(event.getPaymentId()),
                "PAYMENT"
        );

        try {
            emailService.sendTemplatedEmail(
                    null, // Email resolved upstream; placeholder for family-level notification
                    "PAYMENT_COMPLETED",
                    Map.of(
                            "amount", event.getAmount().toString(),
                            "currency", event.getCurrency(),
                            "paymentId", event.getPaymentId().toString(),
                            "paymentMethod", event.getPaymentMethod()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to send payment completed email for paymentId {}: {}",
                    event.getPaymentId(), e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "family-hobbies.payment.failed", groupId = "notification-service-group")
    public void handlePaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailedEvent: paymentId={}, familyId={}",
                event.getPaymentId(), event.getFamilyId());

        notificationCreationService.createNotification(
                event.getFamilyId(),
                NotificationType.IN_APP,
                NotificationCategory.PAYMENT,
                "Echec de paiement",
                "Votre paiement n'a pas pu etre traite. Raison : " + event.getErrorReason(),
                String.valueOf(event.getPaymentId()),
                "PAYMENT"
        );

        try {
            emailService.sendTemplatedEmail(
                    null, // Email resolved upstream
                    "PAYMENT_FAILED",
                    Map.of(
                            "errorReason", event.getErrorReason(),
                            "paymentId", event.getPaymentId().toString()
                    )
            );
        } catch (Exception e) {
            log.error("Failed to send payment failed email for paymentId {}: {}",
                    event.getPaymentId(), e.getMessage(), e);
        }
    }
}
