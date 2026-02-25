package com.familyhobbies.common.security;

/**
 * Constants for security-related HTTP headers.
 * The API Gateway sets these headers after JWT validation.
 * Downstream services read them to identify the authenticated user.
 */
public final class SecurityHeaders {

    private SecurityHeaders() {
        // Utility class — no instantiation
    }

    /** Header carrying the authenticated user's ID (from JWT sub claim). */
    public static final String X_USER_ID = "X-User-Id";

    /** Header carrying the authenticated user's roles (comma-separated, from JWT roles claim). */
    public static final String X_USER_ROLES = "X-User-Roles";

    /** Header carrying a correlation ID for distributed tracing (UUID, generated if absent). */
    public static final String X_CORRELATION_ID = "X-Correlation-Id";
}
