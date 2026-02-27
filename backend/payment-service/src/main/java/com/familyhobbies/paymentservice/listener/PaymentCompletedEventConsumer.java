package com.familyhobbies.paymentservice.listener;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import com.familyhobbies.paymentservice.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that creates invoices when payment completed events are received.
 * Non-fatal: errors are caught and logged to avoid message reprocessing loops.
 */
@Component
public class PaymentCompletedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedEventConsumer.class);

    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;

    public PaymentCompletedEventConsumer(PaymentRepository paymentRepository,
                                         InvoiceService invoiceService) {
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
    }

    /**
     * Processes payment completed events by creating an invoice for the payment.
     *
     * @param event the payment completed event from Kafka
     */
    @KafkaListener(
            topics = "family-hobbies.payment.completed",
            groupId = "payment-service-invoice-group"
    )
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompletedEvent for paymentId={}", event.getPaymentId());

        try {
            Payment payment = paymentRepository.findById(event.getPaymentId())
                    .orElse(null);

            if (payment == null) {
                log.error("Payment not found for paymentId={}, cannot create invoice",
                        event.getPaymentId());
                return;
            }

            invoiceService.createInvoice(payment);
            log.info("Invoice created successfully for paymentId={}", event.getPaymentId());

        } catch (Exception e) {
            log.error("Failed to create invoice for paymentId={}: {}",
                    event.getPaymentId(), e.getMessage(), e);
        }
    }
}
