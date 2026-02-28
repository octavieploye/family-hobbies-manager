package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionType;
import com.familyhobbies.associationservice.repository.ActivityRepository;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.associationservice.repository.SubscriptionRepository;
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
 * Integration tests for SubscriptionController.
 *
 * Story: S3-003 -- Subscription Entity & Lifecycle
 * Tests: 8 test methods
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SubscriptionControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AssociationRepository associationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private Association testAssociation;
    private Activity testActivity;
    private Subscription testSubscription;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
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
            .category(AssociationCategory.SPORT)
            .level(ActivityLevel.BEGINNER)
            .status(ActivityStatus.ACTIVE)
            .priceCents(18000)
            .build());

        testSubscription = subscriptionRepository.save(Subscription.builder()
            .activity(testActivity)
            .familyMemberId(10L)
            .familyId(5L)
            .userId(100L)
            .memberFirstName("Lucas")
            .memberLastName("Dupont")
            .subscriptionType(SubscriptionType.ADHESION)
            .status(SubscriptionStatus.PENDING)
            .startDate(LocalDate.of(2025, 9, 1))
            .build());
    }

    @Test
    @DisplayName("should return 201 when creating subscription")
    void should_return201_when_creatingSubscription() {
        // Delete existing subscription to avoid conflict
        subscriptionRepository.deleteAll();

        String requestBody = """
            {
                "activityId": %d,
                "familyMemberId": 20,
                "familyId": 5,
                "memberFirstName": "Emma",
                "memberLastName": "Martin",
                "subscriptionType": "COTISATION",
                "startDate": "2025-09-01"
            }
            """.formatted(testActivity.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "100");
        headers.set("X-User-Roles", "FAMILY");
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/subscriptions", entity, String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("COTISATION"));
    }

    @Test
    @DisplayName("should return 409 when creating duplicate subscription")
    void should_return409_when_creatingDuplicateSubscription() {
        String requestBody = """
            {
                "activityId": %d,
                "familyMemberId": 10,
                "familyId": 5,
                "memberFirstName": "Lucas",
                "memberLastName": "Dupont",
                "subscriptionType": "ADHESION",
                "startDate": "2025-09-01"
            }
            """.formatted(testActivity.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "100");
        headers.set("X-User-Roles", "FAMILY");
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/subscriptions", entity, String.class);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    @DisplayName("should return 200 with subscriptions when listing by family")
    void should_return200_when_listingByFamily() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "100");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/subscriptions/family/5",
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("ADHESION"));
    }

    @Test
    @DisplayName("should return 200 with subscriptions when listing by member")
    void should_return200_when_listingByMember() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "100");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/subscriptions/member/10",
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("should return 200 when getting subscription by id")
    void should_return200_when_gettingById() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "100");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/subscriptions/" + testSubscription.getId(),
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("PENDING"));
    }

    @Test
    @DisplayName("should return 403 when not owner for get by id")
    void should_return403_when_notOwnerForGetById() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "999");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/subscriptions/" + testSubscription.getId(),
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("should return 200 when cancelling subscription")
    void should_return200_when_cancellingSubscription() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", "100");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/subscriptions/" + testSubscription.getId() + "/cancel?reason=Moving%20away",
            HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("CANCELLED"));
    }

    @Test
    @DisplayName("should return 200 when activating pending subscription")
    void should_return200_when_activatingSubscription() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Roles", "ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/subscriptions/" + testSubscription.getId() + "/activate",
            HttpMethod.POST, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("ACTIVE"));
    }
}
