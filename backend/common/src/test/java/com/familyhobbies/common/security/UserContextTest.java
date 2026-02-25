package com.familyhobbies.common.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserContext ThreadLocal holder.
 *
 * Story: S1-005 — Implement Downstream UserContext Filter
 * Tests: 5 test methods
 *
 * These tests verify:
 * 1. set() and get() store and retrieve the correct UserContext.
 * 2. get() throws IllegalStateException when context is not set.
 * 3. clear() removes the context from the ThreadLocal.
 * 4. hasRole() returns correct boolean for role membership.
 * 5. isAdmin() returns true only when "ADMIN" role is present.
 */
class UserContextTest {

    @AfterEach
    void tearDown() {
        // Always clean up ThreadLocal after each test
        UserContext.clear();
    }

    @Test
    @DisplayName("should store and retrieve UserContext with correct userId and roles")
    void shouldStoreAndRetrieveUserContext() {
        // given
        UserContext context = new UserContext(42L, List.of("FAMILY"));

        // when
        UserContext.set(context);
        UserContext retrieved = UserContext.get();

        // then
        assertNotNull(retrieved);
        assertEquals(42L, retrieved.getUserId());
        assertEquals(1, retrieved.getRoles().size());
        assertEquals("FAMILY", retrieved.getRoles().get(0));
    }

    @Test
    @DisplayName("should throw IllegalStateException when get() called without set()")
    void getShouldThrowIfNotSet() {
        // when & then
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            UserContext::get
        );

        // Verify the message helps developers debug the issue
        assertTrue(exception.getMessage().contains("UserContext not set"),
            "Exception message should indicate that UserContext was not set");
    }

    @Test
    @DisplayName("should remove context from ThreadLocal after clear()")
    void clearShouldRemoveContext() {
        // given — set a context
        UserContext.set(new UserContext(1L, List.of("FAMILY")));

        // Verify it is set
        assertNotNull(UserContext.get());

        // when — clear the context
        UserContext.clear();

        // then — get() throws because context was cleared
        assertThrows(IllegalStateException.class, UserContext::get);
    }

    @Test
    @DisplayName("should return correct boolean for hasRole()")
    void hasRoleShouldReturnCorrectly() {
        // given
        UserContext.set(new UserContext(1L, List.of("FAMILY", "ASSOCIATION")));
        UserContext ctx = UserContext.get();

        // then
        assertTrue(ctx.hasRole("FAMILY"), "Should have FAMILY role");
        assertTrue(ctx.hasRole("ASSOCIATION"), "Should have ASSOCIATION role");
        assertFalse(ctx.hasRole("ADMIN"), "Should NOT have ADMIN role");
        assertFalse(ctx.hasRole("UNKNOWN"), "Should NOT have UNKNOWN role");
    }

    @Test
    @DisplayName("should return true for isAdmin() only when ADMIN role is present")
    void isAdminShouldReturnTrueForAdmin() {
        // Test 1: non-admin user
        UserContext.set(new UserContext(1L, List.of("FAMILY")));
        assertFalse(UserContext.get().isAdmin(), "FAMILY user should not be admin");
        UserContext.clear();

        // Test 2: admin user
        UserContext.set(new UserContext(2L, List.of("ADMIN")));
        assertTrue(UserContext.get().isAdmin(), "ADMIN user should be admin");
        UserContext.clear();

        // Test 3: user with multiple roles including ADMIN
        UserContext.set(new UserContext(3L, List.of("FAMILY", "ADMIN")));
        assertTrue(UserContext.get().isAdmin(),
            "User with FAMILY and ADMIN roles should be admin");
    }
}
