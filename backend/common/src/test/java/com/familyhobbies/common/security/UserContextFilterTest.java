package com.familyhobbies.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for UserContextFilter.
 *
 * Story: S1-005 — Implement Downstream UserContext Filter
 * Tests: 5 test methods
 *
 * These tests verify:
 * 1. UserContext is populated from X-User-Id and X-User-Roles headers.
 * 2. Spring SecurityContext is set with correct authorities (ROLE_ prefix).
 * 3. Both contexts are cleared after the request completes.
 * 4. Missing headers result in no context being set.
 * 5. Multiple comma-separated roles are parsed correctly.
 *
 * Uses MockHttpServletRequest/Response (Spring Test utilities) for
 * fast, isolated unit testing without starting a server.
 *
 * Review findings incorporated:
 * - F-11 (NOTE): Uses custom FilterChain lambdas for state capture instead of
 *   MockFilterChain. This is intentional and preferred for capturing internal state.
 * - M-08 (WARNING): Exception-during-processing cleanup test is deferred to
 *   implementation phase. The shouldClearContextAfterRequest test verifies normal cleanup.
 */
class UserContextFilterTest {

    private UserContextFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new UserContextFilter();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        // Ensure clean state before each test
        SecurityContextHolder.clearContext();
        UserContext.clear();
    }

    @Test
    @DisplayName("should populate UserContext from X-User-Id and X-User-Roles headers")
    void shouldPopulateUserContextFromHeaders() throws ServletException, IOException {
        // given
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-User-Roles", "FAMILY");

        // Capture UserContext inside the filter chain (before cleanup)
        AtomicReference<Long> capturedUserId = new AtomicReference<>();
        AtomicReference<java.util.List<String>> capturedRoles = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            UserContext ctx = UserContext.get();
            capturedUserId.set(ctx.getUserId());
            capturedRoles.set(ctx.getRoles());
        };

        // when
        filter.doFilter(request, response, chain);

        // then
        assertEquals(1L, capturedUserId.get());
        assertNotNull(capturedRoles.get());
        assertEquals(1, capturedRoles.get().size());
        assertTrue(capturedRoles.get().contains("FAMILY"));
    }

    @Test
    @DisplayName("should set Spring SecurityContext with ROLE_ prefixed authorities")
    void shouldSetSpringSecurityContext() throws ServletException, IOException {
        // given
        request.addHeader("X-User-Id", "42");
        request.addHeader("X-User-Roles", "ADMIN");

        // Capture SecurityContext inside the filter chain
        AtomicReference<Object> capturedPrincipal = new AtomicReference<>();
        AtomicReference<java.util.Collection<?>> capturedAuthorities = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertNotNull(auth, "Authentication should be set in SecurityContext");
            capturedPrincipal.set(auth.getPrincipal());
            capturedAuthorities.set(auth.getAuthorities());
        };

        // when
        filter.doFilter(request, response, chain);

        // then
        assertEquals(42L, capturedPrincipal.get());
        assertNotNull(capturedAuthorities.get());

        boolean hasAdminRole = capturedAuthorities.get().stream()
            .anyMatch(a -> a.toString().equals("ROLE_ADMIN"));
        assertTrue(hasAdminRole, "Authorities should contain ROLE_ADMIN");
    }

    @Test
    @DisplayName("should clear both contexts after request completes")
    void shouldClearContextAfterRequest() throws ServletException, IOException {
        // given
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-User-Roles", "FAMILY");

        FilterChain chain = (req, res) -> {
            // Verify context IS set during the request
            assertNotNull(UserContext.get());
            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        };

        // when
        filter.doFilter(request, response, chain);

        // then — contexts are cleared AFTER doFilter returns
        assertThrows(IllegalStateException.class, UserContext::get,
            "UserContext should be cleared after request");
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
            "SecurityContext should be cleared after request");
    }

    @Test
    @DisplayName("should not set context when headers are missing")
    void missingHeaders_shouldNotSetContext() throws ServletException, IOException {
        // given — no headers set on request

        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);

        FilterChain chain = (req, res) -> {
            chainCalled.set(true);

            // SecurityContext should not have authentication
            assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "SecurityContext should not be set when headers are missing");

            // UserContext should not be set
            assertThrows(IllegalStateException.class, UserContext::get,
                "UserContext should not be set when headers are missing");
        };

        // when
        filter.doFilter(request, response, chain);

        // then — chain was called (request proceeded)
        assertTrue(chainCalled.get(), "Filter chain should be invoked even without headers");
    }

    @Test
    @DisplayName("should parse multiple comma-separated roles correctly")
    void multipleRoles_shouldParseCorrectly() throws ServletException, IOException {
        // given
        request.addHeader("X-User-Id", "99");
        request.addHeader("X-User-Roles", "FAMILY,ADMIN");

        AtomicReference<java.util.List<String>> capturedRoles = new AtomicReference<>();
        AtomicReference<java.util.Collection<?>> capturedAuthorities = new AtomicReference<>();

        FilterChain chain = (req, res) -> {
            UserContext ctx = UserContext.get();
            capturedRoles.set(ctx.getRoles());

            var auth = SecurityContextHolder.getContext().getAuthentication();
            capturedAuthorities.set(auth.getAuthorities());
        };

        // when
        filter.doFilter(request, response, chain);

        // then — UserContext has both roles
        assertNotNull(capturedRoles.get());
        assertEquals(2, capturedRoles.get().size());
        assertTrue(capturedRoles.get().contains("FAMILY"));
        assertTrue(capturedRoles.get().contains("ADMIN"));

        // SecurityContext has both authorities with ROLE_ prefix
        assertNotNull(capturedAuthorities.get());
        assertEquals(2, capturedAuthorities.get().size());

        boolean hasFamilyRole = capturedAuthorities.get().stream()
            .anyMatch(a -> a.toString().equals("ROLE_FAMILY"));
        boolean hasAdminRole = capturedAuthorities.get().stream()
            .anyMatch(a -> a.toString().equals("ROLE_ADMIN"));
        assertTrue(hasFamilyRole, "Should have ROLE_FAMILY authority");
        assertTrue(hasAdminRole, "Should have ROLE_ADMIN authority");
    }
}
