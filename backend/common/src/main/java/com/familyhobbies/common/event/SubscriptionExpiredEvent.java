package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when a subscription transitions to EXPIRED status.
 *
 * <p>Topic: {@code subscription.expired}
 *
 * <p>Published by: association-service (SubscriptionExpiryJobListener)
 * <p>Consumed by: notification-service (sends renewal reminder to family)
 *
 * <p>Key: subscriptionId (for Kafka partition ordering per subscription)
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionExpiredEvent extends DomainEvent {

    private Long subscriptionId;
    private Long userId;
    private Long familyMemberId;
    private Long familyId;
    private Long associationId;
    private Long activityId;
    private Instant expiredAt;

    public SubscriptionExpiredEvent(Long subscriptionId, Long userId,
                                     Long familyMemberId, Long familyId,
                                     Long associationId, Long activityId,
                                     Instant expiredAt) {
        super("SUBSCRIPTION_EXPIRED");
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.familyMemberId = familyMemberId;
        this.familyId = familyId;
        this.associationId = associationId;
        this.activityId = activityId;
        this.expiredAt = expiredAt;
    }

    /**
     * Factory method with auto-generated DomainEvent fields.
     */
    public static SubscriptionExpiredEvent of(Long subscriptionId, Long userId,
                                                Long familyMemberId, Long familyId,
                                                Long associationId, Long activityId,
                                                Instant expiredAt) {
        return new SubscriptionExpiredEvent(
                subscriptionId, userId, familyMemberId,
                familyId, associationId, activityId, expiredAt);
    }
}
