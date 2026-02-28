package com.familyhobbies.associationservice.entity.enums;

/**
 * Lifecycle status of an activity.
 * Stored as VARCHAR in PostgreSQL for non-breaking additions.
 */
public enum ActivityStatus {
    ACTIVE,
    SUSPENDED,
    CANCELLED,
    COMPLETED
}
