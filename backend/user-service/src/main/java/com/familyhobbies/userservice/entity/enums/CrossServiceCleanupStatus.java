package com.familyhobbies.userservice.entity.enums;

/**
 * Outcome of cross-service data cleanup during RGPD anonymization.
 *
 * <p>After anonymizing a user's PII in user-service, the system calls
 * association-service and payment-service to delete or anonymize that
 * user's data in those services as well.
 */
public enum CrossServiceCleanupStatus {

    /** Both association-service and payment-service cleanup succeeded. */
    SUCCESS,

    /** One of the two services failed, but the other succeeded. */
    PARTIAL_FAILURE,

    /** Both cross-service cleanup calls failed. */
    FAILED
}
