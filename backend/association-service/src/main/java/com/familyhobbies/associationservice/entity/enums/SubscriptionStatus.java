package com.familyhobbies.associationservice.entity.enums;

/**
 * Lifecycle status of a subscription.
 * Stored as VARCHAR in PostgreSQL for non-breaking additions.
 */
public enum SubscriptionStatus {
    PENDING,
    ACTIVE,
    EXPIRED,
    CANCELLED
}
