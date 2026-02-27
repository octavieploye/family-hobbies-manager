package com.familyhobbies.userservice.security;

import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserRole;
import com.familyhobbies.userservice.entity.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;

import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider (user-service version).
 *
 * Story: S1-001 — Implement JwtTokenProvider
 * Tests: 8 test methods
 *
 * These tests verify:
 * 1. Access token generation produces a valid JWT with 3 parts
 * 2. Access token contains correct claims (sub, email, roles, firstName, lastName, iat, exp)
 * 3. Valid token validation returns claims
 * 4. Expired token throws ExpiredJwtException
 * 5. Tampered token throws JwtException
 * 6. User ID extraction from token
 * 7. Roles extraction from token
 * 8. Refresh tokens are unique UUIDs
 *
 * Review findings incorporated:
 * - F-04 (WARNING): Constructor supports direct instantiation with a single secret string
 *   for unit testing. Implementation should also support @Value injection.
 *   The test uses a test-friendly constructor with defaults for validity periods.
 * - F-09 (NOTE): Tamper test changes last character — valid approach for JJWT.
 */
class JwtTokenProviderTest {

    private static final String TEST_SECRET =
        "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256-signing";

    private JwtTokenProvider jwtTokenProvider;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET);

        testUser = User.builder()
            .id(1L)
            .email("dupont@email.com")
            .firstName("Jean")
            .lastName("Dupont")
            .passwordHash("$2a$12$hashedpassword")
            .role(UserRole.FAMILY)
            .status(UserStatus.ACTIVE)
            .emailVerified(false)
            .build();
    }

    @Test
    @DisplayName("should generate a valid access token with 3 JWT parts")
    void should_generateValidAccessToken_when_userProvided() {
        // given
        // testUser is set up in @BeforeEach

        // when
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // then
        assertNotNull(token, "Access token must not be null");
        assertFalse(token.isBlank(), "Access token must not be blank");

        // A JWT has three parts separated by dots
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "JWT must have 3 parts (header.payload.signature)");
    }

    @Test
    @DisplayName("should include correct claims in access token (sub, email, roles, firstName, lastName, iat, exp)")
    void should_containCorrectClaims_when_accessTokenGenerated() {
        // given
        // testUser is set up in @BeforeEach

        // when
        String token = jwtTokenProvider.generateAccessToken(testUser);
        Claims claims = jwtTokenProvider.validateToken(token);

        // then
        assertEquals("1", claims.getSubject(), "Subject must be user ID as string");
        assertEquals("dupont@email.com", claims.get("email", String.class));
        assertEquals("Jean", claims.get("firstName", String.class));
        assertEquals("Dupont", claims.get("lastName", String.class));

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);
        assertNotNull(roles, "Roles claim must exist");
        assertEquals(1, roles.size());
        assertEquals("FAMILY", roles.get(0));

        assertNotNull(claims.getIssuedAt(), "iat claim must exist");
        assertNotNull(claims.getExpiration(), "exp claim must exist");

        // Expiration should be approximately 1 hour from now
        long diffMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(3_600_000, diffMs, "Access token validity must be 1 hour (3600000 ms)");
    }

    @Test
    @DisplayName("should validate a valid token and return claims")
    void should_returnClaims_when_tokenIsValid() {
        // given
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // when
        Claims claims = jwtTokenProvider.validateToken(token);

        // then
        assertNotNull(claims, "Claims must not be null for a valid token");
        assertEquals("1", claims.getSubject());
    }

    @Test
    @DisplayName("should reject an expired token with ExpiredJwtException")
    void should_throwExpiredJwtException_when_tokenIsExpired() {
        // given — create a token that expired 1 hour ago
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        Date pastDate = new Date(System.currentTimeMillis() - 7_200_000); // 2 hours ago
        Date expiredDate = new Date(System.currentTimeMillis() - 3_600_000); // 1 hour ago

        String expiredToken = Jwts.builder()
            .subject("1")
            .issuedAt(pastDate)
            .expiration(expiredDate)
            .signWith(key, Jwts.SIG.HS256)
            .compact();

        // when & then
        assertThrows(ExpiredJwtException.class,
            () -> jwtTokenProvider.validateToken(expiredToken),
            "Expired token must throw ExpiredJwtException");
    }

    @Test
    @DisplayName("should reject a tampered token with JwtException")
    void should_throwJwtException_when_tokenIsTampered() {
        // given
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // Tamper with the token by changing the last character of the signature
        char lastChar = token.charAt(token.length() - 1);
        char tamperedChar = (lastChar == 'a') ? 'b' : 'a';
        String tamperedToken = token.substring(0, token.length() - 1) + tamperedChar;

        // when & then
        assertThrows(io.jsonwebtoken.JwtException.class,
            () -> jwtTokenProvider.validateToken(tamperedToken),
            "Tampered token must throw JwtException");
    }

    @Test
    @DisplayName("should extract user ID from a valid token")
    void should_extractUserId_when_tokenIsValid() {
        // given
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // when
        Long userId = jwtTokenProvider.getUserIdFromToken(token);

        // then
        assertEquals(1L, userId, "Extracted user ID must match the user's ID");
    }

    @Test
    @DisplayName("should extract roles from a valid token")
    void should_extractRoles_when_tokenIsValid() {
        // given
        String token = jwtTokenProvider.generateAccessToken(testUser);

        // when
        List<String> roles = jwtTokenProvider.getRolesFromToken(token);

        // then
        assertNotNull(roles);
        assertEquals(1, roles.size());
        assertEquals("FAMILY", roles.get(0));
    }

    @Test
    @DisplayName("should generate unique refresh tokens in UUID format")
    void should_generateUniqueUUIDs_when_refreshTokensGenerated() {
        // when
        String token1 = jwtTokenProvider.generateRefreshToken();
        String token2 = jwtTokenProvider.generateRefreshToken();
        String token3 = jwtTokenProvider.generateRefreshToken();

        // then
        assertNotNull(token1);
        assertNotNull(token2);
        assertNotNull(token3);

        // All three must be different
        assertNotEquals(token1, token2, "Refresh tokens must be unique");
        assertNotEquals(token2, token3, "Refresh tokens must be unique");
        assertNotEquals(token1, token3, "Refresh tokens must be unique");

        // Each must be a valid UUID format (8-4-4-4-12 hex digits)
        assertTrue(token1.matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "Refresh token must be UUID format");
    }
}
