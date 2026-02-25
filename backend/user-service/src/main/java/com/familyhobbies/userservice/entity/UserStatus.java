package com.familyhobbies.userservice.entity;

public enum UserStatus {

    /** Account is active and fully functional. */
    ACTIVE,

    /** Account is temporarily inactive. */
    INACTIVE,

    /** Account is suspended by an administrator. */
    SUSPENDED,

    /** Account has been soft-deleted (RGPD right-to-be-forgotten). */
    DELETED
}
