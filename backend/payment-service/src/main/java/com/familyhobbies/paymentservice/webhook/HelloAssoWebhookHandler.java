package com.familyhobbies.paymentservice.webhook;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
import com.familyhobbies.paymentservice.dto.request.HelloAssoWebhookPayload;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.PaymentWebhookLog;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import com.familyhobbies.paymentservice.event.PaymentEventPublisher;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import com.familyhobbies.paymentservice.repository.PaymentWebhookLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Processes webhook events from HelloAsso.
 * Handles idempotency, payment status updates, and Kafka event publishing.
 */
@Component
public class HelloAssoWebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(HelloAssoWebhookHandler.class);

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookLogRepository webhookLogRepository;
    private final PaymentEventPublisher eventPublisher;

    public HelloAssoWebhookHandler(PaymentRepository paymentRepository,
                                    PaymentWebhookLogRepository webhookLogRepository,
                                    PaymentEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.webhookLogRepository = webhookLogRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Processes a webhook payload from HelloAsso.
     *
     * @param payload    the deserialized webhook payload
     * @param rawPayload the raw JSON string for logging
     * @return true if the webhook was processed successfully
     */
    @Transactional
    public boolean handleWebhook(HelloAssoWebhookPayload payload, String rawPayload) {
        if (payload.data() == null || payload.data().id() == null) {
            log.warn("Received webhook with missing data or event ID");
            return false;
        }

        String eventId = payload.data().id();

        // Idempotency check
        if (webhookLogRepository.existsByHelloassoEventIdAndProcessedTrue(eventId)) {
            log.info("Webhook already processed: eventId={}", eventId);
            return true;
        }

        PaymentWebhookLog webhookLog = PaymentWebhookLog.builder()
                .helloassoEventId(eventId)
                .eventType(payload.eventType())
                .payload(rawPayload)
                .build();

        try {
            processPaymentUpdate(payload);
            webhookLog.setProcessed(true);
            webhookLog.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
            webhookLogRepository.save(webhookLog);
            log.info("Webhook processed successfully: eventId={}, type={}", eventId, payload.eventType());
            return true;
        } catch (Exception e) {
            webhookLog.setProcessed(false);
            webhookLog.setErrorMessage(e.getMessage());
            webhookLogRepository.save(webhookLog);
            log.error("Error processing webhook: eventId={}, error={}", eventId, e.getMessage(), e);
            return false;
        }
    }

    private void processPaymentUpdate(HelloAssoWebhookPayload payload) {
        String checkoutId = payload.data().id();
        Optional<Payment> paymentOpt = paymentRepository.findByHelloassoCheckoutId(checkoutId);

        if (paymentOpt.isEmpty()) {
            log.warn("No payment found for HelloAsso checkout ID: {}", checkoutId);
            return;
        }

        Payment payment = paymentOpt.get();
        String state = payload.data().state();

        PaymentStatus newStatus = mapHelloAssoState(state);
        payment.setStatus(newStatus);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        switch (newStatus) {
            case COMPLETED -> {
                payment.setPaidAt(now);
                paymentRepository.save(payment);
                eventPublisher.publishPaymentCompleted(new PaymentCompletedEvent(
                        payment.getId(),
                        payment.getSubscriptionId(),
                        payment.getFamilyId(),
                        payment.getAmount(),
                        payment.getCurrency(),
                        payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null,
                        Instant.now()));
            }
            case FAILED -> {
                payment.setFailedAt(now);
                paymentRepository.save(payment);
                eventPublisher.publishPaymentFailed(new PaymentFailedEvent(
                        payment.getId(),
                        payment.getSubscriptionId(),
                        payment.getFamilyId(),
                        "Payment refused by HelloAsso",
                        Instant.now()));
            }
            case REFUNDED -> {
                payment.setRefundedAt(now);
                paymentRepository.save(payment);
            }
            default -> paymentRepository.save(payment);
        }
    }

    private PaymentStatus mapHelloAssoState(String state) {
        if (state == null) {
            return PaymentStatus.PENDING;
        }
        return switch (state.toLowerCase()) {
            case "authorized" -> PaymentStatus.COMPLETED;
            case "refused" -> PaymentStatus.FAILED;
            case "refunded" -> PaymentStatus.REFUNDED;
            case "cancelled" -> PaymentStatus.CANCELLED;
            default -> PaymentStatus.PENDING;
        };
    }
}
