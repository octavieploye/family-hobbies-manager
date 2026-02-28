package com.familyhobbies.associationservice.mapper;

import com.familyhobbies.associationservice.dto.request.MarkAttendanceRequest;
import com.familyhobbies.associationservice.dto.response.AttendanceResponse;
import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Attendance;
import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AttendanceStatus;
import com.familyhobbies.associationservice.entity.enums.DayOfWeekEnum;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link AttendanceMapper}.
 *
 * Tests: 4 test methods covering toResponse (with member names from subscription)
 * and toEntity (from request with session, subscription, markedBy).
 */
class AttendanceMapperTest {

    private AttendanceMapper mapper;
    private Session testSession;
    private Subscription testSubscription;
    private Attendance testAttendance;

    @BeforeEach
    void setUp() {
        mapper = new AttendanceMapper();

        Activity testActivity = Activity.builder()
            .id(5L)
            .name("Natation enfants")
            .category(AssociationCategory.SPORT)
            .level(ActivityLevel.BEGINNER)
            .status(ActivityStatus.ACTIVE)
            .priceCents(18000)
            .build();

        testSession = Session.builder()
            .id(10L)
            .activity(testActivity)
            .dayOfWeek(DayOfWeekEnum.MONDAY)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(19, 0))
            .location("Salle A")
            .active(true)
            .build();

        testSubscription = Subscription.builder()
            .id(100L)
            .activity(testActivity)
            .familyMemberId(20L)
            .familyId(5L)
            .userId(200L)
            .memberFirstName("Lucas")
            .memberLastName("Dupont")
            .subscriptionType(SubscriptionType.ADHESION)
            .status(SubscriptionStatus.ACTIVE)
            .startDate(LocalDate.of(2025, 9, 1))
            .build();

        testAttendance = Attendance.builder()
            .id(50L)
            .session(testSession)
            .familyMemberId(20L)
            .subscription(testSubscription)
            .sessionDate(LocalDate.of(2025, 10, 6))
            .status(AttendanceStatus.PRESENT)
            .note("On time")
            .markedBy(200L)
            .createdAt(Instant.parse("2025-10-06T18:30:00Z"))
            .updatedAt(Instant.parse("2025-10-06T18:30:00Z"))
            .build();
    }

    @Test
    @DisplayName("should_mapEntityFieldsIncludingMemberNames_when_toResponse")
    void should_mapEntityFieldsIncludingMemberNames_when_toResponse() {
        AttendanceResponse response = mapper.toResponse(testAttendance);

        assertNotNull(response);
        assertEquals(50L, response.id());
        assertEquals(10L, response.sessionId());
        assertEquals(20L, response.familyMemberId());
        assertEquals("Lucas", response.memberFirstName());
        assertEquals("Dupont", response.memberLastName());
        assertEquals(100L, response.subscriptionId());
        assertEquals(LocalDate.of(2025, 10, 6), response.sessionDate());
        assertEquals(AttendanceStatus.PRESENT, response.status());
        assertEquals("On time", response.note());
        assertEquals(200L, response.markedBy());
        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());
    }

    @Test
    @DisplayName("should_returnNull_when_toResponseWithNull")
    void should_returnNull_when_toResponseWithNull() {
        assertNull(mapper.toResponse(null));
    }

    @Test
    @DisplayName("should_createEntityFromRequest_when_toEntity")
    void should_createEntityFromRequest_when_toEntity() {
        MarkAttendanceRequest request = new MarkAttendanceRequest(
            10L,
            20L,
            100L,
            LocalDate.of(2025, 10, 13),
            AttendanceStatus.ABSENT,
            "Sick"
        );

        Attendance entity = mapper.toEntity(request, testSession, testSubscription, 200L);

        assertNotNull(entity);
        assertEquals(testSession, entity.getSession());
        assertEquals(20L, entity.getFamilyMemberId());
        assertEquals(testSubscription, entity.getSubscription());
        assertEquals(LocalDate.of(2025, 10, 13), entity.getSessionDate());
        assertEquals(AttendanceStatus.ABSENT, entity.getStatus());
        assertEquals("Sick", entity.getNote());
        assertEquals(200L, entity.getMarkedBy());
    }

    @Test
    @DisplayName("should_returnNull_when_toEntityWithNull")
    void should_returnNull_when_toEntityWithNull() {
        assertNull(mapper.toEntity(null, testSession, testSubscription, 200L));
    }
}
