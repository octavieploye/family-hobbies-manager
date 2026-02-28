package com.familyhobbies.associationservice.entity.enums;

/**
 * Difficulty/experience level of an activity.
 * Stored as VARCHAR in PostgreSQL for non-breaking additions.
 */
public enum ActivityLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    ALL_LEVELS
}
