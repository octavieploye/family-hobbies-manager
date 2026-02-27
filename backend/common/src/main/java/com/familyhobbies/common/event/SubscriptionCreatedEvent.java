package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Event published when a subscription is created.
 * Consumed by notification-service to send confirmation.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionCreatedEvent extends DomainEvent {

    private Long subscriptionId;
    private Long userId;
    private Long familyId;
    private Long associationId;
    private String subscriptionType;

    public SubscriptionCreatedEvent(Long subscriptionId, Long userId, Long familyId,
                                     Long associationId, String subscriptionType) {
        super("SUBSCRIPTION_CREATED");
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.familyId = familyId;
        this.associationId = associationId;
        this.subscriptionType = subscriptionType;
    }
}
