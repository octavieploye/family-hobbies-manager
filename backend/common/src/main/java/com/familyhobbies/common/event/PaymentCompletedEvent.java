package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published when a payment is completed successfully.
 * Consumed by notification-service to send confirmation email.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentCompletedEvent extends DomainEvent {

    private Long paymentId;
    private Long subscriptionId;
    private Long familyId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private Instant paidAt;

    public PaymentCompletedEvent(Long paymentId, Long subscriptionId, Long familyId,
                                  BigDecimal amount, String currency, String paymentMethod,
                                  Instant paidAt) {
        super("PAYMENT_COMPLETED");
        this.paymentId = paymentId;
        this.subscriptionId = subscriptionId;
        this.familyId = familyId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.paidAt = paidAt;
    }
}
