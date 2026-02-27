package com.familyhobbies.notificationservice.controller;

import com.familyhobbies.notificationservice.dto.request.NotificationPreferenceRequest;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.dto.response.UnreadCountResponse;
import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import com.familyhobbies.notificationservice.entity.enums.NotificationType;
import com.familyhobbies.notificationservice.repository.NotificationPreferenceRepository;
import com.familyhobbies.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for NotificationController.
 *
 * Stories: S6-001, S6-003 -- Notification REST API + Preferences
 * Tests: 6 test methods
 *
 * Uses @SpringBootTest with RANDOM_PORT and TestRestTemplate for full
 * integration testing with H2 database (application-test.yml).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository notificationPreferenceRepository;

    private Long testUserId = 100L;

    @BeforeEach
    void setUp() {
        notificationPreferenceRepository.deleteAll();
        notificationRepository.deleteAll();

        // Seed test notification
        Notification notification = Notification.builder()
                .userId(testUserId)
                .type(NotificationType.IN_APP)
                .category(NotificationCategory.WELCOME)
                .title("Test Notification")
                .message("This is a test notification")
                .read(false)
                .referenceId("1")
                .referenceType("USER")
                .build();
        notificationRepository.save(notification);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(testUserId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("should return notifications when get notifications")
    void should_returnNotifications_when_getNotifications() {
        // given
        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/notifications?page=0&size=10",
                HttpMethod.GET, entity, String.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("should return unread count when get unread count")
    void should_returnUnreadCount_when_getUnreadCount() {
        // given
        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // when
        ResponseEntity<UnreadCountResponse> response = restTemplate.exchange(
                "/api/v1/notifications/unread-count",
                HttpMethod.GET, entity, UnreadCountResponse.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().count());
    }

    @Test
    @DisplayName("should mark as read when put read")
    void should_markAsRead_when_putRead() {
        // given -- get the notification ID from DB
        Notification notification = notificationRepository.findByUserIdAndReadFalse(testUserId).get(0);
        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // when
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/notifications/" + notification.getId() + "/read",
                HttpMethod.PUT, entity, Void.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Verify it is now read
        Notification updated = notificationRepository.findById(notification.getId()).orElseThrow();
        assertEquals(true, updated.isRead());
        assertNotNull(updated.getReadAt());
    }

    @Test
    @DisplayName("should mark all as read when put read all")
    void should_markAllAsRead_when_putReadAll() {
        // given -- add a second unread notification
        Notification notification2 = Notification.builder()
                .userId(testUserId)
                .type(NotificationType.IN_APP)
                .category(NotificationCategory.SYSTEM)
                .title("Second Notification")
                .message("Another test notification")
                .read(false)
                .build();
        notificationRepository.save(notification2);

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // when
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/notifications/read-all",
                HttpMethod.PUT, entity, Void.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, notificationRepository.countByUserIdAndReadFalse(testUserId));
    }

    @Test
    @DisplayName("should return preferences when get preferences")
    void should_returnPreferences_when_getPreferences() {
        // given -- seed a preference
        NotificationPreference preference = NotificationPreference.builder()
                .userId(testUserId)
                .category(NotificationCategory.WELCOME)
                .emailEnabled(true)
                .inAppEnabled(true)
                .build();
        notificationPreferenceRepository.save(preference);

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/notifications/preferences",
                HttpMethod.GET, entity, String.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("should update preference when put preference")
    void should_updatePreference_when_putPreference() {
        // given
        NotificationPreferenceRequest request = new NotificationPreferenceRequest(
                NotificationCategory.PAYMENT, false, true);
        HttpHeaders headers = createHeaders();
        HttpEntity<NotificationPreferenceRequest> entity = new HttpEntity<>(request, headers);

        // when
        ResponseEntity<NotificationPreferenceResponse> response = restTemplate.exchange(
                "/api/v1/notifications/preferences",
                HttpMethod.PUT, entity, NotificationPreferenceResponse.class);

        // then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(NotificationCategory.PAYMENT, response.getBody().category());
        assertEquals(false, response.getBody().emailEnabled());
        assertEquals(true, response.getBody().inAppEnabled());
    }
}
