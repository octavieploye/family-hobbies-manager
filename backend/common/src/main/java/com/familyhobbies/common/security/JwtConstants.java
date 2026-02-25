package com.familyhobbies.common.security;

/**
 * Constants for JWT token processing.
 * Used by api-gateway for token validation and by services for claim extraction.
 */
public final class JwtConstants {

    private JwtConstants() {
        // Utility class — no instantiation
    }

    /** JWT claim key for user roles. */
    public static final String CLAIM_ROLES = "roles";

    /** JWT claim key for user email. */
    public static final String CLAIM_EMAIL = "email";

    /** JWT claim key for user first name. */
    public static final String CLAIM_FIRST_NAME = "firstName";

    /** JWT claim key for user last name. */
    public static final String CLAIM_LAST_NAME = "lastName";

    /** Authorization header name. */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer token prefix. */
    public static final String BEARER_PREFIX = "Bearer ";

    /** Role: family user. */
    public static final String ROLE_FAMILY = "FAMILY";

    /** Role: association manager. */
    public static final String ROLE_ASSOCIATION = "ASSOCIATION";

    /** Role: administrator. */
    public static final String ROLE_ADMIN = "ADMIN";
}
