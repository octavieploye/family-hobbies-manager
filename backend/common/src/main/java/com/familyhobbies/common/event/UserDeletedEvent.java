package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Event published when a user is deleted (soft or hard).
 * Consumed by notification-service and association-service.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDeletedEvent extends DomainEvent {

    private Long userId;
    private String deletionType;

    public UserDeletedEvent(Long userId, String deletionType) {
        super("USER_DELETED");
        this.userId = userId;
        this.deletionType = deletionType;
    }
}
