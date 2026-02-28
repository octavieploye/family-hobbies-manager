package com.familyhobbies.userservice.controller;

import com.familyhobbies.userservice.dto.request.FamilyMemberRequest;
import com.familyhobbies.userservice.dto.request.FamilyRequest;
import com.familyhobbies.userservice.dto.response.FamilyMemberResponse;
import com.familyhobbies.userservice.dto.response.FamilyResponse;
import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserRole;
import com.familyhobbies.userservice.entity.UserStatus;
import com.familyhobbies.userservice.entity.enums.Relationship;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for FamilyController.
 *
 * Story: S2-001 -- Family Entity + CRUD
 * Tests: 5 test methods
 *
 * These tests verify the full HTTP round-trip:
 * - POST /api/v1/families -> 201 Created with Location header
 * - GET /api/v1/families/me -> 200 OK with family data
 * - POST /api/v1/families/{id}/members -> 201 Created
 * - DELETE /api/v1/families/{familyId}/members/{memberId} -> 204 No Content
 * - POST /api/v1/families with blank name -> 400 Bad Request
 *
 * Uses @SpringBootTest with RANDOM_PORT and TestRestTemplate for full
 * integration testing with H2 database (application-test.yml).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FamilyControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ConsentRecordRepository consentRecordRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        consentRecordRepository.deleteAll();
        familyMemberRepository.deleteAll();
        familyRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .email("dupont@email.com")
                .passwordHash("$2a$12$hashedpassword")
                .firstName("Jean")
                .lastName("Dupont")
                .role(UserRole.FAMILY)
                .status(UserStatus.ACTIVE)
                .emailVerified(false)
                .build();
        testUser = userRepository.save(testUser);
    }

    private HttpHeaders createHeadersWithUser(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-User-Roles", "FAMILY");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("should return 201 with Location header when creating a family")
    void should_return201WithLocation_when_creatingFamily() {
        // given
        FamilyRequest request = new FamilyRequest("Famille Dupont");
        HttpHeaders headers = createHeadersWithUser(testUser.getId());
        HttpEntity<FamilyRequest> entity = new HttpEntity<>(request, headers);

        // when
        ResponseEntity<FamilyResponse> response = restTemplate.exchange(
                "/api/v1/families", HttpMethod.POST, entity, FamilyResponse.class);

        // then
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Creating a family must return 201 Created");

        FamilyResponse body = response.getBody();
        assertNotNull(body, "Response body must not be null");
        assertNotNull(body.id(), "Family id must not be null");
        assertEquals("Famille Dupont", body.name());
        assertEquals(testUser.getId(), body.createdBy());

        // Verify Location header
        assertNotNull(response.getHeaders().getLocation(),
                "Location header must be present");
        assertTrue(response.getHeaders().getLocation().toString().contains("/api/v1/families/"),
                "Location header must contain the family path");
    }

    @Test
    @DisplayName("should return 200 with family data when getting my family")
    void should_return200WithFamilyData_when_gettingMyFamily() {
        // given -- create a family first
        FamilyRequest createRequest = new FamilyRequest("Famille Dupont");
        HttpHeaders headers = createHeadersWithUser(testUser.getId());
        HttpEntity<FamilyRequest> createEntity = new HttpEntity<>(createRequest, headers);
        restTemplate.exchange("/api/v1/families", HttpMethod.POST, createEntity, FamilyResponse.class);

        // when
        HttpEntity<Void> getEntity = new HttpEntity<>(headers);
        ResponseEntity<FamilyResponse> response = restTemplate.exchange(
                "/api/v1/families/me", HttpMethod.GET, getEntity, FamilyResponse.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode(),
                "Getting my family must return 200 OK");

        FamilyResponse body = response.getBody();
        assertNotNull(body, "Response body must not be null");
        assertEquals("Famille Dupont", body.name());
        assertEquals(testUser.getId(), body.createdBy());
    }

    @Test
    @DisplayName("should return 201 when adding a member to a family")
    void should_return201_when_addingMemberToFamily() {
        // given -- create a family first
        FamilyRequest createRequest = new FamilyRequest("Famille Dupont");
        HttpHeaders headers = createHeadersWithUser(testUser.getId());
        HttpEntity<FamilyRequest> createEntity = new HttpEntity<>(createRequest, headers);
        ResponseEntity<FamilyResponse> familyResponse = restTemplate.exchange(
                "/api/v1/families", HttpMethod.POST, createEntity, FamilyResponse.class);

        assertNotNull(familyResponse.getBody());
        Long familyId = familyResponse.getBody().id();

        // when -- add a member
        FamilyMemberRequest memberRequest = new FamilyMemberRequest(
                "Marie", "Dupont", LocalDate.of(2015, 6, 15),
                Relationship.CHILD, "Allergic to peanuts");
        HttpEntity<FamilyMemberRequest> memberEntity = new HttpEntity<>(memberRequest, headers);
        ResponseEntity<FamilyMemberResponse> response = restTemplate.exchange(
                "/api/v1/families/" + familyId + "/members",
                HttpMethod.POST, memberEntity, FamilyMemberResponse.class);

        // then
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
                "Adding a member must return 201 Created");

        FamilyMemberResponse body = response.getBody();
        assertNotNull(body, "Response body must not be null");
        assertEquals("Marie", body.firstName());
        assertEquals("Dupont", body.lastName());
        assertEquals(Relationship.CHILD, body.relationship());
        assertEquals(familyId, body.familyId());

        // Verify Location header
        assertNotNull(response.getHeaders().getLocation(),
                "Location header must be present for member creation");
    }

    @Test
    @DisplayName("should return 204 when removing a member from a family")
    void should_return204_when_removingMemberFromFamily() {
        // given -- create a family and add a member
        FamilyRequest createRequest = new FamilyRequest("Famille Dupont");
        HttpHeaders headers = createHeadersWithUser(testUser.getId());
        HttpEntity<FamilyRequest> createEntity = new HttpEntity<>(createRequest, headers);
        ResponseEntity<FamilyResponse> familyResponse = restTemplate.exchange(
                "/api/v1/families", HttpMethod.POST, createEntity, FamilyResponse.class);

        assertNotNull(familyResponse.getBody());
        Long familyId = familyResponse.getBody().id();

        FamilyMemberRequest memberRequest = new FamilyMemberRequest(
                "Marie", "Dupont", LocalDate.of(2015, 6, 15),
                Relationship.CHILD, null);
        HttpEntity<FamilyMemberRequest> memberEntity = new HttpEntity<>(memberRequest, headers);
        ResponseEntity<FamilyMemberResponse> memberResponse = restTemplate.exchange(
                "/api/v1/families/" + familyId + "/members",
                HttpMethod.POST, memberEntity, FamilyMemberResponse.class);

        assertNotNull(memberResponse.getBody());
        Long memberId = memberResponse.getBody().id();

        // when -- remove the member
        HttpEntity<Void> deleteEntity = new HttpEntity<>(headers);
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/families/" + familyId + "/members/" + memberId,
                HttpMethod.DELETE, deleteEntity, Void.class);

        // then
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode(),
                "Removing a member must return 204 No Content");
    }

    @Test
    @DisplayName("should return 400 when creating a family with blank name")
    void should_return400_when_creatingFamilyWithBlankName() {
        // given
        FamilyRequest request = new FamilyRequest("");
        HttpHeaders headers = createHeadersWithUser(testUser.getId());
        HttpEntity<FamilyRequest> entity = new HttpEntity<>(request, headers);

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/families", HttpMethod.POST, entity, String.class);

        // then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Creating a family with blank name must return 400 Bad Request");
    }
}
