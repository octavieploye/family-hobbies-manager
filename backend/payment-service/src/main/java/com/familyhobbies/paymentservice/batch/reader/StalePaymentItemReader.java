package com.familyhobbies.paymentservice.batch.reader;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;

/**
 * Reads payments stuck in {@link PaymentStatus#PENDING} status for more than 24 hours.
 *
 * <p>Loads all stale payments at once (expected volume: tens to low hundreds per day)
 * and iterates through them one by one. Returns {@code null} when exhausted,
 * signaling end-of-data to Spring Batch.
 *
 * <p>Uses {@link Clock} for testability (inject a fixed clock in tests).
 */
public class StalePaymentItemReader implements ItemReader<Payment> {

    private static final Logger log = LoggerFactory.getLogger(StalePaymentItemReader.class);
    private static final Duration STALE_THRESHOLD = Duration.ofHours(24);

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    private Iterator<Payment> paymentIterator;
    private boolean initialized = false;

    public StalePaymentItemReader(PaymentRepository paymentRepository, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    /**
     * Read the next stale payment.
     *
     * <p>On first invocation, queries the database for all stale PENDING payments.
     * Subsequent invocations iterate through the cached result set.
     * Returns {@code null} when all payments have been read.
     *
     * @return the next stale {@link Payment}, or {@code null} if no more items
     */
    @Override
    public Payment read() {
        if (!initialized) {
            OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(STALE_THRESHOLD);
            log.info("Payment reconciliation: querying PENDING payments older than {}", cutoff);

            List<Payment> stalePayments = paymentRepository.findByStatusAndCreatedAtBefore(
                    PaymentStatus.PENDING, cutoff);
            log.info("Payment reconciliation: found {} stale payments to reconcile",
                    stalePayments.size());

            this.paymentIterator = stalePayments.iterator();
            this.initialized = true;
        }

        if (paymentIterator != null && paymentIterator.hasNext()) {
            Payment payment = paymentIterator.next();
            log.debug("Reading stale payment: id={}, checkoutId={}, createdAt={}",
                    payment.getId(), payment.getHelloassoCheckoutId(), payment.getCreatedAt());
            return payment;
        }

        return null;
    }
}
