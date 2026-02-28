package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.entity.enums.DayOfWeekEnum;
import com.familyhobbies.associationservice.repository.ActivityRepository;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.associationservice.repository.SessionRepository;
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
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for ActivityController.
 *
 * Story: S3-002 -- Activity & Session Controller + API
 * Tests: 10 test methods
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActivityControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AssociationRepository associationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SessionRepository sessionRepository;

    private Association testAssociation;
    private Activity testActivity;
    private Session testSession;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        activityRepository.deleteAll();
        associationRepository.deleteAll();

        testAssociation = associationRepository.save(Association.builder()
            .name("Lyon Natation Metropole")
            .slug("lyon-natation-metropole")
            .category(AssociationCategory.SPORT)
            .status(AssociationStatus.ACTIVE)
            .build());

        testActivity = activityRepository.save(Activity.builder()
            .association(testAssociation)
            .name("Natation enfants")
            .description("Cours de natation pour enfants")
            .category(AssociationCategory.SPORT)
            .level(ActivityLevel.BEGINNER)
            .minAge(6)
            .maxAge(10)
            .maxCapacity(15)
            .priceCents(18000)
            .seasonStart(LocalDate.of(2025, 9, 1))
            .seasonEnd(LocalDate.of(2026, 6, 30))
            .status(ActivityStatus.ACTIVE)
            .build());

        testSession = sessionRepository.save(Session.builder()
            .activity(testActivity)
            .dayOfWeek(DayOfWeekEnum.TUESDAY)
            .startTime(LocalTime.of(17, 0))
            .endTime(LocalTime.of(18, 0))
            .location("Piscine municipale")
            .instructorName("Marie Dupont")
            .maxCapacity(15)
            .active(true)
            .build());
    }

    @Test
    @DisplayName("should return 200 with paginated activities when listing")
    void should_return200WithActivities_when_listing() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/associations/" + testAssociation.getId() + "/activities", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Natation enfants"));
    }

    @Test
    @DisplayName("should return 200 with activity detail when getting by id")
    void should_return200WithDetail_when_gettingById() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/associations/" + testAssociation.getId() + "/activities/" + testActivity.getId(),
            String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Natation enfants"));
        assertTrue(response.getBody().contains("Lyon Natation Metropole"));
    }

    @Test
    @DisplayName("should return 404 when activity not found")
    void should_return404_when_activityNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/associations/" + testAssociation.getId() + "/activities/99999",
            String.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("should return 201 when creating activity")
    void should_return201_when_creatingActivity() {
        String requestBody = """
            {
                "name": "Natation adultes",
                "description": "Cours pour adultes",
                "category": "SPORT",
                "level": "ALL_LEVELS",
                "priceCents": 25000
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Roles", "ADMIN");
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/associations/" + testAssociation.getId() + "/activities",
            entity, String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Natation adultes"));
    }

    @Test
    @DisplayName("should return 200 when updating activity")
    void should_return200_when_updatingActivity() {
        String requestBody = """
            {
                "name": "Natation enfants updated",
                "description": "Updated description",
                "category": "SPORT",
                "level": "INTERMEDIATE",
                "priceCents": 20000
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Roles", "ADMIN");
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/associations/" + testAssociation.getId() + "/activities/" + testActivity.getId(),
            HttpMethod.PUT, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Natation enfants updated"));
    }

    @Test
    @DisplayName("should return 204 when soft-deleting activity")
    void should_return204_when_deletingActivity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Roles", "ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
            "/api/v1/associations/" + testAssociation.getId() + "/activities/" + testActivity.getId(),
            HttpMethod.DELETE, entity, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    @DisplayName("should return 200 with sessions when listing sessions")
    void should_return200WithSessions_when_listingSessions() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/associations/" + testAssociation.getId()
                + "/activities/" + testActivity.getId() + "/sessions",
            String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("TUESDAY"));
        assertTrue(response.getBody().contains("Piscine municipale"));
    }

    @Test
    @DisplayName("should return 201 when creating session")
    void should_return201_when_creatingSession() {
        String requestBody = """
            {
                "dayOfWeek": "WEDNESDAY",
                "startTime": "14:00",
                "endTime": "15:00",
                "location": "Studio A",
                "instructorName": "Claire Martin",
                "maxCapacity": 12
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Roles", "ADMIN");
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/associations/" + testAssociation.getId()
                + "/activities/" + testActivity.getId() + "/sessions",
            entity, String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("WEDNESDAY"));
    }

    @Test
    @DisplayName("should return 200 when updating session")
    void should_return200_when_updatingSession() {
        String requestBody = """
            {
                "dayOfWeek": "FRIDAY",
                "startTime": "18:00",
                "endTime": "19:00",
                "location": "Salle B",
                "instructorName": "Jean Paul"
            }
            """;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Roles", "ADMIN");
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/associations/" + testAssociation.getId()
                + "/activities/" + testActivity.getId()
                + "/sessions/" + testSession.getId(),
            HttpMethod.PUT, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("FRIDAY"));
    }

    @Test
    @DisplayName("should return 204 when deactivating session")
    void should_return204_when_deactivatingSession() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Roles", "ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
            "/api/v1/associations/" + testAssociation.getId()
                + "/activities/" + testActivity.getId()
                + "/sessions/" + testSession.getId(),
            HttpMethod.DELETE, entity, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }
}
