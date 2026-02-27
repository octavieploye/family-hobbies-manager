package com.familyhobbies.associationservice.entity.enums;

/**
 * Lifecycle status of an association.
 * Stored as VARCHAR in PostgreSQL for non-breaking additions.
 */
public enum AssociationStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
