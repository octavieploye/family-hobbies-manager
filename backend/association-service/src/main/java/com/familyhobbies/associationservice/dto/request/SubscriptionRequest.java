package com.familyhobbies.associationservice.dto.request;

import com.familyhobbies.associationservice.entity.enums.SubscriptionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for creating a subscription (subscribing a family member to an activity).
 *
 * @param activityId        the activity to subscribe to (required)
 * @param familyMemberId    the family member subscribing (required)
 * @param familyId          the family (required)
 * @param memberFirstName   member's first name (required, stored at creation time to avoid cross-service calls)
 * @param memberLastName    member's last name (required, stored at creation time to avoid cross-service calls)
 * @param subscriptionType  type of subscription: ADHESION or COTISATION (required)
 * @param startDate         subscription start date (required)
 * @param endDate           subscription end date (optional, null = ongoing)
 */
public record SubscriptionRequest(
    @NotNull Long activityId,
    @NotNull Long familyMemberId,
    @NotNull Long familyId,
    @NotBlank @Size(max = 100) String memberFirstName,
    @NotBlank @Size(max = 100) String memberLastName,
    @NotNull SubscriptionType subscriptionType,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {
}
