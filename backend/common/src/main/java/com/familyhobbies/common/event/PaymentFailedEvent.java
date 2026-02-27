package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a payment fails.
 * Consumed by notification-service to alert the user.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentFailedEvent extends DomainEvent {

    private Long paymentId;
    private Long subscriptionId;
    private Long familyId;
    private String errorReason;
    private Instant failedAt;

    public PaymentFailedEvent(Long paymentId, Long subscriptionId, Long familyId,
                               String errorReason, Instant failedAt) {
        super("PAYMENT_FAILED");
        this.paymentId = paymentId;
        this.subscriptionId = subscriptionId;
        this.familyId = familyId;
        this.errorReason = errorReason;
        this.failedAt = failedAt;
    }
}
