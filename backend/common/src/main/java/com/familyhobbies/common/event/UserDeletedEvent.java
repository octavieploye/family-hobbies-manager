package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Event published when a user is deleted (soft or hard).
 * Consumed by notification-service and association-service.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDeletedEvent extends DomainEvent {

    private Long userId;
    private DeletionType deletionType;

    public UserDeletedEvent(Long userId, DeletionType deletionType) {
        super("USER_DELETED");
        this.userId = userId;
        this.deletionType = deletionType;
    }
}
