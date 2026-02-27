package com.familyhobbies.paymentservice.event;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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
}
