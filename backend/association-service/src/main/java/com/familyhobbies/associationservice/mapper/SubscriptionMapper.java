package com.familyhobbies.associationservice.mapper;

import com.familyhobbies.associationservice.dto.request.SubscriptionRequest;
import com.familyhobbies.associationservice.dto.response.SubscriptionResponse;
import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Subscription;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Subscription} entities to/from DTOs.
 * Manual mapper (no MapStruct) for full control and transparency.
 */
@Component
public class SubscriptionMapper {

    /**
     * Maps a subscription entity to a response DTO with denormalized names.
     */
    public SubscriptionResponse toResponse(Subscription entity) {
        if (entity == null) {
            return null;
        }
        Activity activity = entity.getActivity();
        Association association = activity != null ? activity.getAssociation() : null;

        return new SubscriptionResponse(
            entity.getId(),
            activity != null ? activity.getId() : null,
            activity != null ? activity.getName() : null,
            association != null ? association.getName() : null,
            entity.getFamilyMemberId(),
            entity.getFamilyId(),
            entity.getUserId(),
            entity.getSubscriptionType(),
            entity.getStatus(),
            entity.getStartDate(),
            entity.getEndDate(),
            entity.getCancellationReason(),
            entity.getCancelledAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * Creates a new Subscription entity from a request DTO.
     * The activity entity and userId must be set by the caller.
     */
    public Subscription toEntity(SubscriptionRequest request, Activity activity, Long userId) {
        if (request == null) {
            return null;
        }
        return Subscription.builder()
            .activity(activity)
            .familyMemberId(request.familyMemberId())
            .familyId(request.familyId())
            .userId(userId)
            .subscriptionType(request.subscriptionType())
            .startDate(request.startDate())
            .endDate(request.endDate())
            .build();
    }
}
