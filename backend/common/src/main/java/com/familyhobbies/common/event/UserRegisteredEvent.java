package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Event published when a new user registers.
 * Consumed by notification-service to send a welcome email.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRegisteredEvent extends DomainEvent {

    private Long userId;
    private String email;
    private String firstName;
    private String lastName;

    public UserRegisteredEvent(Long userId, String email, String firstName, String lastName) {
        super("USER_REGISTERED");
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
