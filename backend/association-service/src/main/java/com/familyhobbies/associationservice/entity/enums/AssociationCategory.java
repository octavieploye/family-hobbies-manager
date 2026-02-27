package com.familyhobbies.associationservice.entity.enums;

/**
 * Categories of associations available on the platform.
 * Stored as VARCHAR in PostgreSQL for non-breaking additions.
 */
public enum AssociationCategory {
    SPORT,
    DANCE,
    MUSIC,
    THEATER,
    ART,
    MARTIAL_ARTS,
    WELLNESS,
    OTHER
}
