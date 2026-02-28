package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Attendance;
import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.entity.enums.AttendanceStatus;
import com.familyhobbies.associationservice.entity.enums.DayOfWeekEnum;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionType;
import com.familyhobbies.associationservice.repository.ActivityRepository;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.associationservice.repository.AttendanceRepository;
import com.familyhobbies.associationservice.repository.SessionRepository;
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
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for AttendanceController.
 *
 * Story: S4-001 -- Attendance Entity + API
 * Tests: 10 test methods
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AttendanceControllerIntegrationTest {

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

    @Autowired
    private AttendanceRepository attendanceRepository;

    private Association testAssociation;
    private Activity testActivity;
    private Session testSession;
    private Subscription testSubscription;
    private Attendance testAttendance;

    @BeforeEach
    void setUp() {
        attendanceRepository.deleteAll();
        subscriptionRepository.deleteAll();
        sessionRepository.deleteAll();
        activityRepository.deleteAll();
        associationRepository.deleteAll();

        testAssociation = associationRepository.save(Association.builder()
            .name("Lyon Natation Metropole")
            .slug("lyon-natation-metropole-att")
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

        testSession = sessionRepository.save(Session.builder()
            .activity(testActivity)
            .dayOfWeek(DayOfWeekEnum.TUESDAY)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(19, 0))
            .location("Piscine municipale")
            .active(true)
            .build());

        testSubscription = subscriptionRepository.save(Subscription.builder()
            .activity(testActivity)
            .familyMemberId(10L)
            .familyId(5L)
            .userId(100L)
            .subscriptionType(SubscriptionType.ADHESION)
            .status(SubscriptionStatus.ACTIVE)
            .startDate(LocalDate.of(2025, 9, 1))
            .build());
    }

    private HttpHeaders createHeaders(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", String.valueOf(userId));
        headers.set("X-User-Roles", "FAMILY");
        return headers;
    }

    @Test
    @DisplayName("should return 201 when marking attendance")
    void should_return201_when_markingAttendance() {
        String requestBody = """
            {
                "sessionId": %d,
                "familyMemberId": 10,
                "subscriptionId": %d,
                "sessionDate": "2025-10-15",
                "status": "PRESENT"
            }
            """.formatted(testSession.getId(), testSubscription.getId());

        HttpEntity<String> entity = new HttpEntity<>(requestBody, createHeaders(100L));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/attendance", entity, String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("PRESENT"));
    }

    @Test
    @DisplayName("should return 400 when marking attendance with future date")
    void should_return400_when_markingWithFutureDate() {
        String futureDate = LocalDate.now().plusDays(5).toString();
        String requestBody = """
            {
                "sessionId": %d,
                "familyMemberId": 10,
                "subscriptionId": %d,
                "sessionDate": "%s",
                "status": "PRESENT"
            }
            """.formatted(testSession.getId(), testSubscription.getId(), futureDate);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, createHeaders(100L));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/attendance", entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("should return 400 when subscription is not active")
    void should_return400_when_subscriptionNotActive() {
        Subscription pendingSub = subscriptionRepository.save(Subscription.builder()
            .activity(testActivity)
            .familyMemberId(20L)
            .familyId(5L)
            .userId(100L)
            .subscriptionType(SubscriptionType.ADHESION)
            .status(SubscriptionStatus.PENDING)
            .startDate(LocalDate.of(2025, 9, 1))
            .build());

        String requestBody = """
            {
                "sessionId": %d,
                "familyMemberId": 20,
                "subscriptionId": %d,
                "sessionDate": "2025-10-15",
                "status": "PRESENT"
            }
            """.formatted(testSession.getId(), pendingSub.getId());

        HttpEntity<String> entity = new HttpEntity<>(requestBody, createHeaders(100L));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/attendance", entity, String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("should return 403 when not subscription owner")
    void should_return403_when_notOwner() {
        String requestBody = """
            {
                "sessionId": %d,
                "familyMemberId": 10,
                "subscriptionId": %d,
                "sessionDate": "2025-10-15",
                "status": "PRESENT"
            }
            """.formatted(testSession.getId(), testSubscription.getId());

        HttpEntity<String> entity = new HttpEntity<>(requestBody, createHeaders(999L));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/attendance", entity, String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    @DisplayName("should return 200 when bulk marking attendance")
    void should_return200_when_bulkMarking() {
        String requestBody = """
            {
                "sessionId": %d,
                "sessionDate": "2025-10-15",
                "marks": [
                    {
                        "familyMemberId": 10,
                        "subscriptionId": %d,
                        "status": "PRESENT"
                    }
                ]
            }
            """.formatted(testSession.getId(), testSubscription.getId());

        HttpEntity<String> entity = new HttpEntity<>(requestBody, createHeaders(100L));

        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/attendance/bulk", entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("should return 200 when getting session attendance")
    void should_return200_when_gettingSessionAttendance() {
        // First create an attendance record
        attendanceRepository.save(Attendance.builder()
            .session(testSession)
            .familyMemberId(10L)
            .subscription(testSubscription)
            .sessionDate(LocalDate.of(2025, 10, 15))
            .status(AttendanceStatus.PRESENT)
            .markedBy(100L)
            .build());

        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(100L));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/attendance/session/" + testSession.getId() + "?date=2025-10-15",
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("PRESENT"));
    }

    @Test
    @DisplayName("should return 200 when getting member history")
    void should_return200_when_gettingMemberHistory() {
        attendanceRepository.save(Attendance.builder()
            .session(testSession)
            .familyMemberId(10L)
            .subscription(testSubscription)
            .sessionDate(LocalDate.of(2025, 10, 15))
            .status(AttendanceStatus.PRESENT)
            .markedBy(100L)
            .build());

        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(100L));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/attendance/member/10",
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("should return 200 when getting member summary")
    void should_return200_when_gettingMemberSummary() {
        attendanceRepository.save(Attendance.builder()
            .session(testSession)
            .familyMemberId(10L)
            .subscription(testSubscription)
            .sessionDate(LocalDate.of(2025, 10, 15))
            .status(AttendanceStatus.PRESENT)
            .markedBy(100L)
            .build());

        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(100L));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/attendance/member/10/summary",
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("attendanceRate"));
    }

    @Test
    @DisplayName("should return 200 when getting subscription attendance")
    void should_return200_when_gettingSubscriptionAttendance() {
        attendanceRepository.save(Attendance.builder()
            .session(testSession)
            .familyMemberId(10L)
            .subscription(testSubscription)
            .sessionDate(LocalDate.of(2025, 10, 15))
            .status(AttendanceStatus.PRESENT)
            .markedBy(100L)
            .build());

        HttpEntity<Void> entity = new HttpEntity<>(createHeaders(100L));

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/attendance/subscription/" + testSubscription.getId(),
            HttpMethod.GET, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("should return 200 when updating attendance")
    void should_return200_when_updatingAttendance() {
        Attendance saved = attendanceRepository.save(Attendance.builder()
            .session(testSession)
            .familyMemberId(10L)
            .subscription(testSubscription)
            .sessionDate(LocalDate.of(2025, 10, 15))
            .status(AttendanceStatus.PRESENT)
            .markedBy(100L)
            .build());

        String requestBody = """
            {
                "sessionId": %d,
                "familyMemberId": 10,
                "subscriptionId": %d,
                "sessionDate": "2025-10-15",
                "status": "EXCUSED",
                "note": "Was sick"
            }
            """.formatted(testSession.getId(), testSubscription.getId());

        HttpHeaders headers = createHeaders(100L);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.exchange(
            "/api/v1/attendance/" + saved.getId(),
            HttpMethod.PUT, entity, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("EXCUSED"));
    }
}
