package com.familyhobbies.common.event;

/**
 * Enum representing the type of user deletion.
 * Used in {@link UserDeletedEvent} to distinguish between soft deletes
 * (status change, data retained) and hard deletes (data permanently removed).
 */
public enum DeletionType {

    /** Soft delete: user status set to DELETED, data retained for RGPD grace period. */
    SOFT,

    /** Hard delete: user data permanently removed (after RGPD grace period or by admin). */
    HARD
}
