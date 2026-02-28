package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.repository.ActivityRepository;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.associationservice.repository.SessionRepository;
import com.familyhobbies.associationservice.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for AssociationController.
 *
 * Story: S2-003 -- Association Entity + Search
 * Tests: 4 test methods
 *
 * These tests verify the full HTTP round-trip:
 * - GET /api/v1/associations -> 200 OK with paginated results
 * - GET /api/v1/associations?city=Lyon -> 200 OK with filtered results
 * - GET /api/v1/associations/{id} -> 200 OK with detail
 * - GET /api/v1/associations/{id} -> 404 Not Found
 *
 * Uses @SpringBootTest with RANDOM_PORT and TestRestTemplate for full
 * integration testing with H2 database (application-test.yml).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssociationControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AssociationRepository associationRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    private Association lyonAssociation;
    private Association parisAssociation;

    @BeforeEach
    void setUp() {
        subscriptionRepository.deleteAll();
        sessionRepository.deleteAll();
        activityRepository.deleteAll();
        associationRepository.deleteAll();

        lyonAssociation = associationRepository.save(Association.builder()
            .name("Lyon Natation Metropole")
            .slug("lyon-natation-metropole")
            .description("Club de natation proposant des cours pour tous niveaux")
            .address("12 Rue des Bains")
            .city("Lyon")
            .postalCode("69003")
            .department("Rhone")
            .region("Auvergne-Rhone-Alpes")
            .phone("+33 4 72 33 45 67")
            .email("contact@lyon-natation.fr")
            .website("https://www.lyon-natation-metropole.fr")
            .logoUrl("https://cdn.familyhobbies.fr/logos/lyon-natation.png")
            .helloassoSlug("lyon-natation-metropole")
            .category(AssociationCategory.SPORT)
            .status(AssociationStatus.ACTIVE)
            .build());

        parisAssociation = associationRepository.save(Association.builder()
            .name("Paris Athletisme Club")
            .slug("paris-athletisme-club")
            .description("Club d'athletisme en plein coeur de Paris")
            .address("15 Avenue de Breteuil")
            .city("Paris")
            .postalCode("75007")
            .department("Paris")
            .region("Ile-de-France")
            .phone("+33 1 45 67 23 89")
            .email("info@paris-athletisme.fr")
            .website("https://www.paris-athletisme-club.fr")
            .logoUrl("https://cdn.familyhobbies.fr/logos/paris-athletisme.png")
            .helloassoSlug("paris-athletisme-club")
            .category(AssociationCategory.SPORT)
            .status(AssociationStatus.ACTIVE)
            .build());
    }

    @Test
    @DisplayName("should return 200 with paginated results when searching all associations")
    void should_return200WithPaginatedResults_when_searchingAll() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/associations", String.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode(),
            "Searching all associations must return 200 OK");
        assertNotNull(response.getBody(), "Response body must not be null");
        assertTrue(response.getBody().contains("Lyon Natation Metropole"),
            "Response must contain Lyon association");
        assertTrue(response.getBody().contains("Paris Athletisme Club"),
            "Response must contain Paris association");
        assertTrue(response.getBody().contains("\"totalElements\":2"),
            "Response must report 2 total elements");
    }

    @Test
    @DisplayName("should return 200 with filtered results when searching by city")
    void should_return200WithFilteredResults_when_searchingByCity() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/associations?city=Lyon", String.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode(),
            "Searching by city must return 200 OK");
        assertNotNull(response.getBody(), "Response body must not be null");
        assertTrue(response.getBody().contains("Lyon Natation Metropole"),
            "Response must contain Lyon association");
        assertTrue(!response.getBody().contains("Paris Athletisme Club"),
            "Response must not contain Paris association when filtering by Lyon");
        assertTrue(response.getBody().contains("\"totalElements\":1"),
            "Response must report 1 total element for Lyon");
    }

    @Test
    @DisplayName("should return 200 with association detail when valid id")
    void should_return200WithDetail_when_validId() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/associations/" + lyonAssociation.getId(), String.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode(),
            "Getting association by id must return 200 OK");
        assertNotNull(response.getBody(), "Response body must not be null");
        assertTrue(response.getBody().contains("Lyon Natation Metropole"),
            "Response must contain association name");
        assertTrue(response.getBody().contains("lyon-natation-metropole"),
            "Response must contain association slug");
        assertTrue(response.getBody().contains("12 Rue des Bains"),
            "Response must contain full address in detail view");
    }

    @Test
    @DisplayName("should return 404 when association id not found")
    void should_return404_when_idNotFound() {
        // when
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/associations/99999", String.class);

        // then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
            "Getting non-existent association must return 404 Not Found");
    }
}
