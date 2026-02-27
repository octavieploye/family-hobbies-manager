package com.familyhobbies.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Servlet filter that extracts user identity from gateway-injected headers
 * and populates both Spring SecurityContext and ThreadLocal UserContext.
 */
public class UserContextFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserContextFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(SecurityHeaders.X_USER_ID);
        String roles = request.getHeader(SecurityHeaders.X_USER_ROLES);

        if (userId != null && roles != null) {
            try {
                Long parsedUserId = Long.valueOf(userId);

                // Step 1: Parse roles into Spring Security authorities
                List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                    .map(String::trim)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();

                // Step 2: Set Spring SecurityContext
                var authentication = new UsernamePasswordAuthenticationToken(
                    parsedUserId,
                    null,
                    authorities
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // Step 3: Set ThreadLocal UserContext for business logic access
                UserContext.set(new UserContext(parsedUserId, parseRoles(roles)));
            } catch (NumberFormatException e) {
                log.warn("Invalid X-User-Id header value '{}': not a valid Long. "
                        + "Treating request as unauthenticated.", userId);
            }
        } else if (userId != null || roles != null) {
            // M-004: Warn when only one of the two security headers is present
            log.warn("Partial security headers detected: X-User-Id={}, X-User-Roles={}. "
                    + "Both headers are required for authentication. "
                    + "Treating request as unauthenticated.",
                    userId != null ? "[present]" : "[missing]",
                    roles != null ? "[present]" : "[missing]");
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private List<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(roles.split(","))
            .map(String::trim)
            .toList();
    }
}
