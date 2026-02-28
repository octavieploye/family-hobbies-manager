package com.familyhobbies.paymentservice.event;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
import com.familyhobbies.paymentservice.entity.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publishes payment domain events to Kafka topics.
 * Uses fire-and-forget pattern: exceptions are caught and logged, never rethrown.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    static final String TOPIC_PAYMENT_COMPLETED = "family-hobbies.payment.completed";
    static final String TOPIC_PAYMENT_FAILED = "family-hobbies.payment.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a payment completed event.
     * Fire-and-forget: catches all exceptions to avoid disrupting the main flow.
     *
     * @param event the payment completed event
     */
    public void publishPaymentCompleted(PaymentCompletedEvent event) {
        try {
            kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED,
                    String.valueOf(event.getPaymentId()), event);
            log.info("Published PaymentCompletedEvent for paymentId={}", event.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to publish PaymentCompletedEvent for paymentId={}: {}",
                    event.getPaymentId(), e.getMessage(), e);
        }
    }

    /**
     * Publishes a payment failed event.
     * Fire-and-forget: catches all exceptions to avoid disrupting the main flow.
     *
     * @param event the payment failed event
     */
    public void publishPaymentFailed(PaymentFailedEvent event) {
        try {
            kafkaTemplate.send(TOPIC_PAYMENT_FAILED,
                    String.valueOf(event.getPaymentId()), event);
            log.info("Published PaymentFailedEvent for paymentId={}", event.getPaymentId());
        } catch (Exception e) {
            log.error("Failed to publish PaymentFailedEvent for paymentId={}: {}",
                    event.getPaymentId(), e.getMessage(), e);
        }
    }

    /**
     * Publishes a payment completed event from a Payment entity.
     * Convenience overload used by the batch reconciliation writer.
     * Fire-and-forget: catches all exceptions to avoid disrupting the main flow.
     *
     * @param payment the completed payment entity
     */
    public void publishPaymentCompleted(Payment payment) {
        Instant paidAt = payment.getPaidAt() != null
                ? payment.getPaidAt().toInstant() : Instant.now();
        String paymentMethod = payment.getPaymentMethod() != null
                ? payment.getPaymentMethod().name() : null;

        PaymentCompletedEvent event = new PaymentCompletedEvent(
                payment.getId(),
                payment.getSubscriptionId(),
                payment.getFamilyId(),
                payment.getAmount(),
                payment.getCurrency(),
                paymentMethod,
                paidAt);
        publishPaymentCompleted(event);
    }

    /**
     * Publishes a payment failed event from a Payment entity.
     * Convenience overload used by the batch reconciliation writer.
     * Fire-and-forget: catches all exceptions to avoid disrupting the main flow.
     *
     * @param payment the failed payment entity
     */
    public void publishPaymentFailed(Payment payment) {
        Instant failedAt = payment.getFailedAt() != null
                ? payment.getFailedAt().toInstant() : Instant.now();

        PaymentFailedEvent event = new PaymentFailedEvent(
                payment.getId(),
                payment.getSubscriptionId(),
                payment.getFamilyId(),
                "Reconciliation: payment failed on HelloAsso",
                failedAt);
        publishPaymentFailed(event);
    }
}
