package com.familyhobbies.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String userId = request.getHeader(SecurityHeaders.X_USER_ID);
        String roles = request.getHeader(SecurityHeaders.X_USER_ROLES);

        if (userId != null && roles != null) {
            // Step 1: Parse roles into Spring Security authorities
            List<SimpleGrantedAuthority> authorities = Arrays.stream(roles.split(","))
                .map(String::trim)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

            // Step 2: Set Spring SecurityContext
            var authentication = new UsernamePasswordAuthenticationToken(
                Long.valueOf(userId),
                null,
                authorities
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Step 3: Set ThreadLocal UserContext for business logic access
            UserContext.set(new UserContext(Long.valueOf(userId), parseRoles(roles)));
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
