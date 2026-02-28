package com.familyhobbies.associationservice.dto.response;

import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response DTO for subscription details.
 * Includes denormalized names for display convenience.
 *
 * @param id                 unique identifier
 * @param activityId         subscribed activity ID
 * @param activityName       activity name (denormalized)
 * @param associationName    association name (denormalized)
 * @param familyMemberId     family member ID
 * @param memberFirstName    member first name (stored at creation time)
 * @param memberLastName     member last name (stored at creation time)
 * @param familyId           family ID
 * @param userId             user who created the subscription
 * @param subscriptionType   ADHESION or COTISATION
 * @param status             current lifecycle status
 * @param startDate          subscription start date
 * @param endDate            subscription end date (nullable)
 * @param cancellationReason reason for cancellation (nullable)
 * @param cancelledAt        cancellation timestamp (nullable)
 * @param createdAt          creation timestamp
 * @param updatedAt          last update timestamp
 */
public record SubscriptionResponse(
    Long id,
    Long activityId,
    String activityName,
    String associationName,
    Long familyMemberId,
    String memberFirstName,
    String memberLastName,
    Long familyId,
    Long userId,
    SubscriptionType subscriptionType,
    SubscriptionStatus status,
    LocalDate startDate,
    LocalDate endDate,
    String cancellationReason,
    Instant cancelledAt,
    Instant createdAt,
    Instant updatedAt
) {
}
