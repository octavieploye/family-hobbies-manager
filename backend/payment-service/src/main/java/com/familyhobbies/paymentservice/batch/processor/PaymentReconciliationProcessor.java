package com.familyhobbies.paymentservice.batch.processor;

import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.dto.helloasso.HelloAssoCheckoutStatusResponse;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

import java.time.OffsetDateTime;

/**
 * Processes each stale payment by querying HelloAsso for its actual checkout status.
 *
 * <p>Maps HelloAsso checkout states to local {@link PaymentStatus}:
 * <ul>
 *     <li>{@code Authorized} / {@code Registered} -> {@link PaymentStatus#COMPLETED}</li>
 *     <li>{@code Refused} / {@code Canceled} -> {@link PaymentStatus#FAILED}</li>
 *     <li>{@code Refunded} -> {@link PaymentStatus#REFUNDED}</li>
 *     <li>{@code Pending} -> no change (returns {@code null} to skip the item)</li>
 * </ul>
 *
 * <p>If the HelloAsso API is unavailable, throws
 * {@link com.familyhobbies.errorhandling.exception.container.ExternalApiException}
 * which is handled by the skip policy.
 */
public class PaymentReconciliationProcessor implements ItemProcessor<Payment, Payment> {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationProcessor.class);

    private final HelloAssoCheckoutClient helloAssoCheckoutClient;

    public PaymentReconciliationProcessor(HelloAssoCheckoutClient helloAssoCheckoutClient) {
        this.helloAssoCheckoutClient = helloAssoCheckoutClient;
    }

    @Override
    public Payment process(Payment payment) throws Exception {
        String checkoutId = payment.getHelloassoCheckoutId();
        log.info("Reconciling payment id={} with HelloAsso checkoutId={}",
                payment.getId(), checkoutId);

        HelloAssoCheckoutStatusResponse helloAssoStatus =
                helloAssoCheckoutClient.getCheckoutStatus(checkoutId);

        String state = helloAssoStatus.getState();
        log.info("HelloAsso reports state='{}' for checkoutId={}", state, checkoutId);

        if (helloAssoStatus.isCompleted()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setPaidAt(helloAssoStatus.getDate() != null
                    ? helloAssoStatus.getDate()
                    : OffsetDateTime.now());
            log.info("Payment id={} reconciled to COMPLETED", payment.getId());
            return payment;
        }

        if (helloAssoStatus.isFailed()) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailedAt(OffsetDateTime.now());
            log.info("Payment id={} reconciled to FAILED", payment.getId());
            return payment;
        }

        if (helloAssoStatus.isRefunded()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundedAt(OffsetDateTime.now());
            log.info("Payment id={} reconciled to REFUNDED", payment.getId());
            return payment;
        }

        if (helloAssoStatus.isPending()) {
            log.info("Payment id={} still PENDING on HelloAsso -- skipping (will retry next run)",
                    payment.getId());
            return null;
        }

        // Unknown state -- log warning and skip
        log.warn("Payment id={} has unknown HelloAsso state='{}' -- skipping",
                payment.getId(), state);
        return null;
    }
}
