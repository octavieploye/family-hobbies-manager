package com.familyhobbies.userservice.controller;

import com.familyhobbies.userservice.entity.ConsentRecord;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserRole;
import com.familyhobbies.userservice.entity.UserStatus;
import com.familyhobbies.userservice.entity.enums.ConsentType;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for RgpdController.
 *
 * Story: S4-003 -- RGPD Consent Management + Data Export
 * Story: S4-006 -- RGPD Account Deletion
 * Tests: 8 test methods
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RgpdControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        consentRecordRepository.deleteAll();
        familyMemberRepository.deleteAll();
        familyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
            .email("rgpd-test@test.com")
            .passwordHash(passwordEncoder.encode("TestPassword123!"))
            .firstName("Marie")
            .lastName("Martin")
            .role(UserRole.FAMILY)
            .status(UserStatus.ACTIVE)
            .emailVerified(true)
            .build();
        testUser = userRepository.save(testUser);
    }

    private HttpHeaders createHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-User-Roles", "FAMILY");
        return headers;
    }

    @Test
    @DisplayName("should return 200 when recording consent")
    void should_return200_when_recordingConsent() {
        String requestBody = """
            {
                "consentType": "TERMS_OF_SERVICE",
                "granted": true,
                "ipAddress": "192.168.1.1"
            }
            """;

        HttpEntity<String> entity = new HttpEntity<>(requestBody, createHeaders(testUser.getId()));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/rgpd/consent", entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("TERMS_OF_SERVICE"));
        assertTrue(response.getBody().contains("true"));
    }

    @Test
    @DisplayName("should return 200 when getting current consents")
    void should_return200_when_gettingCurrentConsents() {
        // First record a consent
        consentRecordRepository.save(ConsentRecord.builder()
            .user(testUser)
            .consentType(ConsentType.DATA_PROCESSING)
            .granted(true)
            .version("1.0")
            .build());

        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(testUser.getId()));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/rgpd/consent",
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("DATA_PROCESSING"));
    }

    @Test
    @DisplayName("should return 200 with empty list when no consents exist")
    void should_return200WithEmptyList_when_noConsentsExist() {
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(testUser.getId()));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/rgpd/consent",
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("[]", response.getBody());
    }

    @Test
    @DisplayName("should return 200 when getting consent history")
    void should_return200_when_gettingConsentHistory() {
        consentRecordRepository.save(ConsentRecord.builder()
            .user(testUser)
            .consentType(ConsentType.MARKETING_EMAIL)
            .granted(true)
            .version("1.0")
            .build());

        consentRecordRepository.save(ConsentRecord.builder()
            .user(testUser)
            .consentType(ConsentType.MARKETING_EMAIL)
            .granted(false)
            .version("1.0")
            .build());

        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(testUser.getId()));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/rgpd/consent/history",
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("MARKETING_EMAIL"));
    }

    @Test
    @DisplayName("should return 200 when exporting user data")
    void should_return200_when_exportingUserData() {
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(testUser.getId()));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/rgpd/export",
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("rgpd-test@test.com"));
        assertTrue(response.getBody().contains("Marie"));
        assertTrue(response.getBody().contains("exportedAt"));
    }

    @Test
    @DisplayName("should return 204 when deleting account with valid password")
    void should_return204_when_deletingAccount() {
        String requestBody = """
            {
                "password": "TestPassword123!",
                "reason": "Moving abroad"
            }
            """;

        HttpHeaders headers = createHeaders(testUser.getId());
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Void> response = restTemplate.exchange(
            "/api/v1/rgpd/account",
            HttpMethod.DELETE, entity, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Verify user was anonymized
        User deletedUser = userRepository.findById(testUser.getId()).orElseThrow();
        assertEquals(UserStatus.DELETED, deletedUser.getStatus());
        assertEquals("deleted_" + testUser.getId() + "@removed.local", deletedUser.getEmail());
    }

    @Test
    @DisplayName("should return 400 when deleting account with wrong password")
    void should_return400_when_deletingWithWrongPassword() {
        String requestBody = """
            {
                "password": "WrongPassword123!",
                "reason": "Test"
            }
            """;

        HttpHeaders headers = createHeaders(testUser.getId());
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/rgpd/account",
            HttpMethod.DELETE, entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("should return 404 when user not found for export")
    void should_return404_when_userNotFound() {
        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(999L));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/rgpd/export",
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
