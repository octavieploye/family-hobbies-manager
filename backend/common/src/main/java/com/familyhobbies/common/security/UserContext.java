package com.familyhobbies.common.security;

import java.util.Collections;
import java.util.List;

/**
 * ThreadLocal holder for the current user's identity.
 *
 * Set by UserContextFilter at the start of each request.
 * Cleared by UserContextFilter in a finally block after the request completes.
 */
public final class UserContext {

    private static final ThreadLocal<UserContext> CURRENT = new ThreadLocal<>();

    private final Long userId;
    private final List<String> roles;

    public UserContext(Long userId, List<String> roles) {
        this.userId = userId;
        this.roles = roles;
    }

    /**
     * Store the UserContext for the current thread.
     */
    public static void set(UserContext context) {
        CURRENT.set(context);
    }

    /**
     * Retrieve the UserContext for the current thread.
     * Throws IllegalStateException if not set.
     */
    public static UserContext get() {
        UserContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException(
                "UserContext not set -- is UserContextFilter registered?");
        }
        return ctx;
    }

    /**
     * Remove the UserContext from the current thread.
     */
    public static void clear() {
        CURRENT.remove();
    }

    public Long getUserId() {
        return userId;
    }

    public List<String> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
