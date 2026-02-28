package com.familyhobbies.associationservice.service;

import com.familyhobbies.associationservice.dto.request.SubscriptionRequest;
import com.familyhobbies.associationservice.dto.response.SubscriptionResponse;

import java.util.List;

/**
 * Service interface for subscription lifecycle operations.
 */
public interface SubscriptionService {

    /**
     * Creates a new subscription (subscribes a family member to an activity).
     */
    SubscriptionResponse createSubscription(SubscriptionRequest request, Long userId);

    /**
     * Lists all subscriptions for a family.
     */
    List<SubscriptionResponse> findByFamilyId(Long familyId, Long userId);

    /**
     * Lists all subscriptions for a family member.
     */
    List<SubscriptionResponse> findByMemberId(Long memberId, Long userId);

    /**
     * Gets a single subscription by ID.
     */
    SubscriptionResponse findById(Long subscriptionId, Long userId);

    /**
     * Cancels a subscription.
     */
    SubscriptionResponse cancelSubscription(Long subscriptionId, Long userId, String reason);

    /**
     * Activates a pending subscription (ADMIN only).
     */
    SubscriptionResponse activateSubscription(Long subscriptionId);
}
