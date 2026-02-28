package com.familyhobbies.associationservice.entity.enums;

/**
 * Days of the week for session scheduling.
 * Stored as VARCHAR in PostgreSQL for non-breaking additions.
 *
 * <p>Named {@code DayOfWeekEnum} to avoid collision with {@link java.time.DayOfWeek}.
 * The {@code Enum} suffix is an intentional deviation from the project naming convention
 * to prevent ambiguous imports and IDE confusion across the codebase.</p>
 */
public enum DayOfWeekEnum {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}
