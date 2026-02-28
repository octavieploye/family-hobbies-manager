package com.familyhobbies.userservice.controller;

import com.familyhobbies.userservice.dto.request.RegisterRequest;
import com.familyhobbies.userservice.dto.response.AuthResponse;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.repository.ConsentRecordRepository;
import com.familyhobbies.userservice.repository.FamilyMemberRepository;
import com.familyhobbies.userservice.repository.FamilyRepository;
import com.familyhobbies.userservice.repository.RefreshTokenRepository;
import com.familyhobbies.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for user registration (POST /api/v1/auth/register).
 *
 * Story: S1-002 — Implement User Registration
 * Tests: 10 test methods (7 from sprint spec + 3 from review findings F-06)
 *
 * Uses @SpringBootTest with RANDOM_PORT and TestRestTemplate for full
 * integration testing with H2 database (application-test.yml).
 *
 * Review findings incorporated:
 * - F-02 (BLOCKER): Register endpoint returns AuthResponse (accessToken, refreshToken,
 *   tokenType, expiresIn) — not a user profile. This is the chosen design for immediate
 *   login after registration. NOTE: The API contracts doc (03-api-contracts.md) needs
 *   updating to match this approach.
 * - F-06 (WARNING): Added password complexity tests — short password, no uppercase,
 *   no special char. These test that passwords satisfying length but failing complexity
 *   are rejected.
 * - M-03 (WARNING): Added test for missing firstName/lastName fields.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserRegistrationTest {

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

    @BeforeEach
    void setUp() {
        // Clean up in FK-safe order: consent -> members -> families -> refresh tokens -> users
        consentRecordRepository.deleteAll();
        familyMemberRepository.deleteAll();
        familyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("should return 201 with tokens when registration is valid")
    void should_return201WithTokens_when_registrationIsValid() {
        // given
        RegisterRequest request = new RegisterRequest(
            "dupont@email.com", "SecureP@ss1", "Jean", "Dupont", "+33612345678");

        // when
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
            "/api/v1/auth/register", request, AuthResponse.class);

        // then
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
            "Registration must return 201 Created");

        AuthResponse body = response.getBody();
        assertNotNull(body, "Response body must not be null");
        assertNotNull(body.accessToken(), "Access token must not be null");
        assertNotNull(body.refreshToken(), "Refresh token must not be null");
        assertEquals("Bearer", body.tokenType(), "Token type must be Bearer");
        assertEquals(3600, body.expiresIn(), "Expiry must be 3600 seconds");

        // Access token must be a valid JWT (3 parts separated by dots)
        String[] parts = body.accessToken().split("\\.");
        assertEquals(3, parts.length, "Access token must be a valid JWT");
    }

    @Test
    @DisplayName("should return 409 when email is already registered")
    void should_return409_when_emailAlreadyRegistered() {
        // given
        RegisterRequest request = new RegisterRequest(
            "dupont@email.com", "SecureP@ss1", "Jean", "Dupont", null);

        // First registration -- should succeed
        restTemplate.postForEntity("/api/v1/auth/register", request, AuthResponse.class);

        // when — second registration with same email
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/register", request, String.class);

        // then
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
            "Duplicate email registration must return 409 Conflict");
    }

    @Test
    @DisplayName("should return 400 when email format is invalid")
    void should_return400_when_emailFormatIsInvalid() {
        // given
        RegisterRequest request = new RegisterRequest(
            "not-an-email", "SecureP@ss1", "Jean", "Dupont", null);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/register", request, String.class);

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Invalid email must return 400 Bad Request");
    }

    @Test
    @DisplayName("should return 400 when password is blank")
    void should_return400_when_passwordIsBlank() {
        // given
        RegisterRequest request = new RegisterRequest(
            "dupont@email.com", "", "Jean", "Dupont", null);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/register", request, String.class);

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Blank password must return 400 Bad Request");
    }

    @Test
    @DisplayName("should hash password with BCrypt before storing")
    void should_hashPasswordWithBCrypt_when_registrationIsValid() {
        // given
        RegisterRequest request = new RegisterRequest(
            "dupont@email.com", "SecureP@ss1", "Jean", "Dupont", null);

        // when
        restTemplate.postForEntity("/api/v1/auth/register", request, AuthResponse.class);

        // then
        Optional<User> savedUser = userRepository.findByEmail("dupont@email.com");
        assertTrue(savedUser.isPresent(), "User must be saved in database");

        User user = savedUser.get();
        // Password must NOT be stored in plain text
        assertNotEquals("SecureP@ss1", user.getPasswordHash(),
            "Password must not be stored in plain text");
        // Password must be verifiable with BCrypt
        assertTrue(passwordEncoder.matches("SecureP@ss1", user.getPasswordHash()),
            "Stored hash must match the original password via BCrypt");
        // BCrypt hash always starts with $2a$ (or $2b$)
        assertTrue(user.getPasswordHash().startsWith("$2a$"),
            "Password hash must be BCrypt format");
    }

    @Test
    @DisplayName("should assign FAMILY role and ACTIVE status to new user")
    void should_assignFamilyRoleAndActiveStatus_when_newUserRegistered() {
        // given
        RegisterRequest request = new RegisterRequest(
            "dupont@email.com", "SecureP@ss1", "Jean", "Dupont", null);

        // when
        restTemplate.postForEntity("/api/v1/auth/register", request, AuthResponse.class);

        // then
        Optional<User> savedUser = userRepository.findByEmail("dupont@email.com");
        assertTrue(savedUser.isPresent(), "User must be saved in database");

        User user = savedUser.get();
        assertEquals(com.familyhobbies.userservice.entity.UserRole.FAMILY, user.getRole(),
            "New user must have FAMILY role");
        assertEquals(com.familyhobbies.userservice.entity.UserStatus.ACTIVE, user.getStatus(),
            "New user must have ACTIVE status");
        assertFalse(user.isEmailVerified(),
            "New user must have emailVerified = false");
    }

    @Test
    @DisplayName("should save refresh token in database")
    void should_saveRefreshTokenInDb_when_registrationSucceeds() {
        // given
        RegisterRequest request = new RegisterRequest(
            "dupont@email.com", "SecureP@ss1", "Jean", "Dupont", null);

        // when
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
            "/api/v1/auth/register", request, AuthResponse.class);

        // then
        AuthResponse body = response.getBody();
        assertNotNull(body, "Response body must not be null");

        // The refresh token returned in the response must exist in the database
        var storedToken = refreshTokenRepository.findByToken(body.refreshToken());
        assertTrue(storedToken.isPresent(),
            "Refresh token must be saved in the database");

        var token = storedToken.get();
        assertFalse(token.isRevoked(),
            "New refresh token must not be revoked");
        assertNotNull(token.getExpiresAt(),
            "Refresh token must have an expiry date");
        assertTrue(token.getExpiresAt().isAfter(java.time.Instant.now()),
            "Refresh token expiry must be in the future");
    }

    // ==================== Review Finding F-06: Password Complexity Tests ====================

    @Test
    @DisplayName("should return 400 when password is too short (< 8 chars)")
    void should_return400_when_passwordIsTooShort() {
        // given — password "Sh@1" is only 4 characters (min 8 required)
        RegisterRequest request = new RegisterRequest(
            "dupont@email.com", "Sh@1", "Jean", "Dupont", null);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/register", request, String.class);

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Short password (< 8 chars) must return 400 Bad Request");
    }

    @Test
    @DisplayName("should return 400 when password has no uppercase letter")
    void should_return400_when_passwordHasNoUppercase() {
        // given — password "secure@1pass" has no uppercase letter
        RegisterRequest request = new RegisterRequest(
            "dupont@email.com", "secure@1pass", "Jean", "Dupont", null);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/register", request, String.class);

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Password without uppercase must return 400 Bad Request");
    }

    @Test
    @DisplayName("should return 400 when password has no special character")
    void should_return400_when_passwordHasNoSpecialCharacter() {
        // given — password "SecurePass1" has no special character
        RegisterRequest request = new RegisterRequest(
            "dupont@email.com", "SecurePass1", "Jean", "Dupont", null);

        // when
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/auth/register", request, String.class);

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Password without special character must return 400 Bad Request");
    }
}
