package com.familyhobbies.userservice.entity.enums;

/**
 * Types of RGPD consent that users can grant or revoke.
 * Stored as VARCHAR in PostgreSQL for non-breaking additions.
 */
public enum ConsentType {
    TERMS_OF_SERVICE,
    DATA_PROCESSING,
    MARKETING_EMAIL,
    THIRD_PARTY_SHARING
}
