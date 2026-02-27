package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Event published when a subscription is cancelled.
 * Consumed by notification-service to send cancellation notice.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionCancelledEvent extends DomainEvent {

    private Long subscriptionId;
    private Long userId;
    private String cancellationReason;

    public SubscriptionCancelledEvent(Long subscriptionId, Long userId,
                                       String cancellationReason) {
        super("SUBSCRIPTION_CANCELLED");
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.cancellationReason = cancellationReason;
    }
}
