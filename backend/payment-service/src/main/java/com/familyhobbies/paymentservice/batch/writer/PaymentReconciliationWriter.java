package com.familyhobbies.paymentservice.batch.writer;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import com.familyhobbies.paymentservice.event.PaymentEventPublisher;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.ArrayList;

/**
 * Writes reconciled payments to the database and publishes Kafka events.
 *
 * <p>For each payment in the chunk:
 * <ol>
 *     <li>Saves the updated payment to the database</li>
 *     <li>Publishes the appropriate Kafka event based on the new status:
 *         <ul>
 *             <li>{@link PaymentStatus#COMPLETED} -> PaymentCompletedEvent</li>
 *             <li>{@link PaymentStatus#FAILED} -> PaymentFailedEvent</li>
 *             <li>{@link PaymentStatus#REFUNDED} -> logged, no event yet</li>
 *         </ul>
 *     </li>
 * </ol>
 */
public class PaymentReconciliationWriter implements ItemWriter<Payment> {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationWriter.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentReconciliationWriter(PaymentRepository paymentRepository,
                                        PaymentEventPublisher paymentEventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentEventPublisher = paymentEventPublisher;
    }

    @Override
    public void write(Chunk<? extends Payment> chunk) throws Exception {
        log.info("Writing {} reconciled payments", chunk.size());

        // Batch persist all payments at once
        var payments = new ArrayList<>(chunk.getItems());
        paymentRepository.saveAll(payments);

        // Publish Kafka events individually (per-item)
        for (Payment payment : payments) {
            publishEventForStatus(payment);
        }

        log.info("Successfully wrote {} reconciled payments", chunk.size());
    }

    private void publishEventForStatus(Payment payment) {
        switch (payment.getStatus()) {
            case COMPLETED -> {
                log.info("Publishing PaymentCompletedEvent for payment id={}", payment.getId());
                paymentEventPublisher.publishPaymentCompleted(payment);
            }
            case FAILED -> {
                log.info("Publishing PaymentFailedEvent for payment id={}", payment.getId());
                paymentEventPublisher.publishPaymentFailed(payment);
            }
            case REFUNDED ->
                log.info("Payment id={} reconciled to REFUNDED -- no Kafka event published yet",
                        payment.getId());
            default ->
                log.warn("Unexpected status {} for payment id={} during write phase",
                        payment.getStatus(), payment.getId());
        }
    }
}
