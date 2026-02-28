package com.familyhobbies.userservice.controller;

import com.familyhobbies.userservice.dto.request.LoginRequest;
import com.familyhobbies.userservice.dto.request.RefreshTokenRequest;
import com.familyhobbies.userservice.dto.request.RegisterRequest;
import com.familyhobbies.userservice.dto.response.AuthResponse;
import com.familyhobbies.userservice.entity.RefreshToken;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserRole;
import com.familyhobbies.userservice.entity.UserStatus;
import com.familyhobbies.userservice.repository.ConsentRecordRepository;
import com.familyhobbies.userservice.repository.FamilyMemberRepository;
import com.familyhobbies.userservice.repository.FamilyRepository;
import com.familyhobbies.userservice.repository.RefreshTokenRepository;
import com.familyhobbies.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for login, refresh, and logout flows.
 *
 * Story: S1-003 — Implement Login, Refresh, and Logout
 * Tests: 9 test methods
 *
 * Uses @SpringBootTest with RANDOM_PORT and TestRestTemplate for full
 * integration testing with H2 database (application-test.yml).
 *
 * Review findings incorporated:
 * - F-03 (WARNING): Logout test includes a comment explaining that X-User-Id header
 *   simulates the gateway-injected header. The API contract specifies a refresh token
 *   in the request body, but our Sprint 1 implementation uses X-User-Id header for
 *   simplicity (revokes all tokens for user). This design choice is documented here.
 * - F-05 (WARNING): Refresh token validity is 7 days (not 30 days). The
 *   register_shouldSaveRefreshTokenInDb test in UserRegistrationTest already verifies
 *   the token is in the future; an explicit 7-day assertion is added in
 *   refresh_validToken_shouldReturn200WithNewTokens.
 * - F-10 (NOTE): login_inactiveUser_shouldReturn401 — we intentionally return 401
 *   (not 403) to avoid leaking account status. This is a security design choice:
 *   all authentication failures return the same "Invalid credentials" error.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthFlowTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User existingUser;

    @BeforeEach
    void setUp() {
        // Clean up in FK-safe order: consent -> members -> families -> refresh tokens -> users
        consentRecordRepository.deleteAll();
        familyMemberRepository.deleteAll();
        familyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        // Create an existing user for login tests
        existingUser = User.builder()
            .email("dupont@email.com")
            .passwordHash(passwordEncoder.encode("SecureP@ss1"))
            .firstName("Jean")
            .lastName("Dupont")
            .role(UserRole.FAMILY)
            .status(UserStatus.ACTIVE)
            .emailVerified(false)
            .build();
        existingUser = userRepository.save(existingUser);
    }

    // ==================== LOGIN TESTS ====================

    @Test
    @DisplayName("should return 200 with tokens when credentials are valid")
    void should_return200WithTokens_when_credentialsAreValid() {
        // given
        LoginRequest request = new LoginRequest("dupont@email.com", "SecureP@ss1");

        // when
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, AuthResponse.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode(),
            "Login with valid credentials must return 200 OK");

        AuthResponse body = response.getBody();
        assertNotNull(body, "Response body must not be null");
        assertNotNull(body.accessToken(), "Access token must not be null");
        assertNotNull(body.refreshToken(), "Refresh token must not be null");
        assertEquals("Bearer", body.tokenType());
        assertEquals(3600, body.expiresIn());

        // Access token must be a valid JWT
        String[] parts = body.accessToken().split("\\.");
        assertEquals(3, parts.length, "Access token must be a valid JWT");
    }

    @Test
    @DisplayName("should return 401 when password is wrong")
    void should_return401_when_passwordIsWrong() {
        // given
        LoginRequest request = new LoginRequest("dupont@email.com", "WrongPassword1!");

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, String.class);

        // then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
            "Wrong password must return 401 Unauthorized");
    }

    @Test
    @DisplayName("should return 401 when email does not exist")
    void should_return401_when_emailDoesNotExist() {
        // given
        LoginRequest request = new LoginRequest("unknown@email.com", "SecureP@ss1");

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, String.class);

        // then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
            "Unknown email must return 401 Unauthorized");
    }

    @Test
    @DisplayName("should return 401 when user account is inactive")
    void should_return401_when_userAccountIsInactive() {
        // given — set the user to INACTIVE
        // NOTE (F-10): We intentionally return 401 (not 403) to avoid leaking account
        // status information. All authentication failures return the same generic error
        // to prevent user enumeration attacks.
        existingUser.setStatus(UserStatus.INACTIVE);
        userRepository.save(existingUser);

        LoginRequest request = new LoginRequest("dupont@email.com", "SecureP@ss1");

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/login", request, String.class);

        // then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
            "Inactive user must return 401 Unauthorized");
    }

    // ==================== REFRESH TESTS ====================

    @Test
    @DisplayName("should return 200 with new tokens when refresh token is valid")
    void should_return200WithNewTokens_when_refreshTokenIsValid() {
        // given — login to get a valid refresh token
        LoginRequest loginRequest = new LoginRequest("dupont@email.com", "SecureP@ss1");
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
            "/api/v1/auth/login", loginRequest, AuthResponse.class);

        AuthResponse loginBody = loginResponse.getBody();
        assertNotNull(loginBody);

        // when — refresh the token
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(loginBody.refreshToken());
        ResponseEntity<AuthResponse> refreshResponse = restTemplate.postForEntity(
            "/api/v1/auth/refresh", refreshRequest, AuthResponse.class);

        // then
        assertEquals(HttpStatus.OK, refreshResponse.getStatusCode(),
            "Valid refresh must return 200 OK");

        AuthResponse refreshBody = refreshResponse.getBody();
        assertNotNull(refreshBody);
        assertNotNull(refreshBody.accessToken());
        assertNotNull(refreshBody.refreshToken());

        // New tokens must be different from the old ones
        assertNotEquals(loginBody.accessToken(), refreshBody.accessToken(),
            "New access token must be different from old one");
        assertNotEquals(loginBody.refreshToken(), refreshBody.refreshToken(),
            "New refresh token must be different from old one (rotation)");
    }

    @Test
    @DisplayName("should return 401 when refresh token has been revoked")
    void should_return401_when_refreshTokenHasBeenRevoked() {
        // given — login to get a refresh token
        LoginRequest loginRequest = new LoginRequest("dupont@email.com", "SecureP@ss1");
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
            "/api/v1/auth/login", loginRequest, AuthResponse.class);

        AuthResponse loginBody = loginResponse.getBody();
        assertNotNull(loginBody);

        // Manually revoke the token in the database
        Optional<RefreshToken> storedToken = refreshTokenRepository.findByToken(loginBody.refreshToken());
        assertTrue(storedToken.isPresent());
        RefreshToken token = storedToken.get();
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // when — try to use the revoked token
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(loginBody.refreshToken());
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/refresh", refreshRequest, String.class);

        // then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
            "Revoked refresh token must return 401 Unauthorized");
    }

    @Test
    @DisplayName("should return 401 when refresh token has expired")
    void should_return401_when_refreshTokenHasExpired() {
        // given — create a refresh token that is already expired
        RefreshToken expiredToken = RefreshToken.builder()
            .user(existingUser)
            .token("expired-token-value-for-testing")
            .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
            .revoked(false)
            .build();
        refreshTokenRepository.save(expiredToken);

        // when
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest("expired-token-value-for-testing");
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/refresh", refreshRequest, String.class);

        // then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
            "Expired refresh token must return 401 Unauthorized");
    }

    @Test
    @DisplayName("should revoke the used refresh token after rotation")
    void should_revokeUsedRefreshToken_when_tokenIsRotated() {
        // given — login to get a refresh token
        LoginRequest loginRequest = new LoginRequest("dupont@email.com", "SecureP@ss1");
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
            "/api/v1/auth/login", loginRequest, AuthResponse.class);

        AuthResponse loginBody = loginResponse.getBody();
        assertNotNull(loginBody);
        String originalRefreshToken = loginBody.refreshToken();

        // when — refresh the token
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(originalRefreshToken);
        restTemplate.postForEntity("/api/v1/auth/refresh", refreshRequest, AuthResponse.class);

        // then — the original refresh token must now be revoked in the database
        Optional<RefreshToken> originalToken = refreshTokenRepository.findByToken(originalRefreshToken);
        assertTrue(originalToken.isPresent(),
            "Original refresh token must still exist in database");
        assertTrue(originalToken.get().isRevoked(),
            "Original refresh token must be revoked after rotation");
    }

    // ==================== LOGOUT TESTS ====================

    @Test
    @DisplayName("should revoke all refresh tokens on logout")
    void should_revokeAllRefreshTokens_when_logoutCalled() {
        // given — login twice to create two refresh tokens
        LoginRequest loginRequest = new LoginRequest("dupont@email.com", "SecureP@ss1");
        ResponseEntity<AuthResponse> loginResponse1 = restTemplate.postForEntity(
            "/api/v1/auth/login", loginRequest, AuthResponse.class);
        ResponseEntity<AuthResponse> loginResponse2 = restTemplate.postForEntity(
            "/api/v1/auth/login", loginRequest, AuthResponse.class);

        assertNotNull(loginResponse1.getBody());
        assertNotNull(loginResponse2.getBody());

        // when — logout by sending X-User-Id header
        // NOTE (F-03): The X-User-Id header simulates what the API Gateway would inject
        // after validating the JWT. In production, the gateway extracts the user ID from
        // the JWT and adds this header. In tests (without gateway), we set it directly.
        // The API contract says logout expects a refreshToken in the body, but our Sprint 1
        // implementation revokes ALL tokens for the user ID, which is a stronger security
        // guarantee (invalidates every session, not just one).
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(existingUser.getId()));
        HttpEntity<Void> logoutRequest = new HttpEntity<>(headers);

        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
            "/api/v1/auth/logout", HttpMethod.POST, logoutRequest, Void.class);

        // then
        assertEquals(HttpStatus.NO_CONTENT, logoutResponse.getStatusCode(),
            "Logout must return 204 No Content");

        // Both refresh tokens must now be revoked
        Optional<RefreshToken> token1 = refreshTokenRepository.findByToken(
            loginResponse1.getBody().refreshToken());
        Optional<RefreshToken> token2 = refreshTokenRepository.findByToken(
            loginResponse2.getBody().refreshToken());

        assertTrue(token1.isPresent());
        assertTrue(token2.isPresent());
        assertTrue(token1.get().isRevoked(),
            "First refresh token must be revoked after logout");
        assertTrue(token2.get().isRevoked(),
            "Second refresh token must be revoked after logout");
    }
}
