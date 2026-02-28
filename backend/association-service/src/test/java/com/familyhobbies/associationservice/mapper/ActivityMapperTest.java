package com.familyhobbies.associationservice.mapper;

import com.familyhobbies.associationservice.dto.request.ActivityRequest;
import com.familyhobbies.associationservice.dto.request.SessionRequest;
import com.familyhobbies.associationservice.dto.response.ActivityDetailResponse;
import com.familyhobbies.associationservice.dto.response.ActivityResponse;
import com.familyhobbies.associationservice.dto.response.SessionResponse;
import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.entity.enums.DayOfWeekEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link ActivityMapper}.
 *
 * Tests: 7 test methods covering toResponse, toDetailResponse, toEntity,
 * updateEntity, toSessionResponse, toSessionEntity, updateSessionEntity.
 */
class ActivityMapperTest {

    private ActivityMapper mapper;
    private Association testAssociation;
    private Activity testActivity;
    private Session testSession;

    @BeforeEach
    void setUp() {
        mapper = new ActivityMapper();

        testAssociation = Association.builder()
            .id(1L)
            .name("Lyon Natation")
            .slug("lyon-natation")
            .category(AssociationCategory.SPORT)
            .status(AssociationStatus.ACTIVE)
            .build();

        testSession = Session.builder()
            .id(10L)
            .dayOfWeek(DayOfWeekEnum.MONDAY)
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(19, 0))
            .location("Salle A")
            .instructorName("Jean Dupont")
            .maxCapacity(20)
            .active(true)
            .build();

        List<Session> sessions = new ArrayList<>();
        sessions.add(testSession);

        testActivity = Activity.builder()
            .id(5L)
            .association(testAssociation)
            .name("Natation enfants")
            .description("Swimming for children aged 6-10")
            .category(AssociationCategory.SPORT)
            .level(ActivityLevel.BEGINNER)
            .minAge(6)
            .maxAge(10)
            .maxCapacity(25)
            .priceCents(18000)
            .seasonStart(LocalDate.of(2025, 9, 1))
            .seasonEnd(LocalDate.of(2026, 6, 30))
            .status(ActivityStatus.ACTIVE)
            .sessions(sessions)
            .createdAt(Instant.parse("2025-09-01T10:00:00Z"))
            .updatedAt(Instant.parse("2025-09-15T12:00:00Z"))
            .build();

        testSession.setActivity(testActivity);
    }

    @Test
    @DisplayName("should_mapEntityFieldsCorrectly_when_toResponse")
    void should_mapEntityFieldsCorrectly_when_toResponse() {
        ActivityResponse response = mapper.toResponse(testActivity);

        assertNotNull(response);
        assertEquals(5L, response.id());
        assertEquals("Natation enfants", response.name());
        assertEquals(AssociationCategory.SPORT, response.category());
        assertEquals(ActivityLevel.BEGINNER, response.level());
        assertEquals(6, response.minAge());
        assertEquals(10, response.maxAge());
        assertEquals(18000, response.priceCents());
        assertEquals(ActivityStatus.ACTIVE, response.status());
        assertEquals(1, response.sessionCount());
    }

    @Test
    @DisplayName("should_returnNull_when_toResponseWithNull")
    void should_returnNull_when_toResponseWithNull() {
        assertNull(mapper.toResponse(null));
    }

    @Test
    @DisplayName("should_includeAssociationNameAndSessions_when_toDetailResponse")
    void should_includeAssociationNameAndSessions_when_toDetailResponse() {
        ActivityDetailResponse response = mapper.toDetailResponse(testActivity);

        assertNotNull(response);
        assertEquals(5L, response.id());
        assertEquals(1L, response.associationId());
        assertEquals("Lyon Natation", response.associationName());
        assertEquals("Natation enfants", response.name());
        assertEquals("Swimming for children aged 6-10", response.description());
        assertEquals(AssociationCategory.SPORT, response.category());
        assertEquals(ActivityLevel.BEGINNER, response.level());
        assertEquals(6, response.minAge());
        assertEquals(10, response.maxAge());
        assertEquals(25, response.maxCapacity());
        assertEquals(18000, response.priceCents());
        assertEquals(LocalDate.of(2025, 9, 1), response.seasonStart());
        assertEquals(LocalDate.of(2026, 6, 30), response.seasonEnd());
        assertEquals(ActivityStatus.ACTIVE, response.status());
        assertEquals(1, response.sessions().size());
        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());
    }

    @Test
    @DisplayName("should_createEntityFromRequest_when_toEntity")
    void should_createEntityFromRequest_when_toEntity() {
        ActivityRequest request = new ActivityRequest(
            "Danse classique",
            "Classical dance for beginners",
            AssociationCategory.DANCE,
            ActivityLevel.BEGINNER,
            5,
            12,
            30,
            15000,
            LocalDate.of(2025, 9, 1),
            LocalDate.of(2026, 6, 30)
        );

        Activity entity = mapper.toEntity(request, testAssociation);

        assertNotNull(entity);
        assertEquals("Danse classique", entity.getName());
        assertEquals("Classical dance for beginners", entity.getDescription());
        assertEquals(AssociationCategory.DANCE, entity.getCategory());
        assertEquals(ActivityLevel.BEGINNER, entity.getLevel());
        assertEquals(5, entity.getMinAge());
        assertEquals(12, entity.getMaxAge());
        assertEquals(30, entity.getMaxCapacity());
        assertEquals(15000, entity.getPriceCents());
        assertEquals(LocalDate.of(2025, 9, 1), entity.getSeasonStart());
        assertEquals(LocalDate.of(2026, 6, 30), entity.getSeasonEnd());
        assertEquals(testAssociation, entity.getAssociation());
    }

    @Test
    @DisplayName("should_updateExistingEntityFields_when_updateEntity")
    void should_updateExistingEntityFields_when_updateEntity() {
        ActivityRequest request = new ActivityRequest(
            "Natation ados",
            "Swimming for teenagers",
            AssociationCategory.SPORT,
            ActivityLevel.INTERMEDIATE,
            12,
            18,
            20,
            22000,
            LocalDate.of(2025, 10, 1),
            LocalDate.of(2026, 7, 15)
        );

        mapper.updateEntity(testActivity, request);

        assertEquals("Natation ados", testActivity.getName());
        assertEquals("Swimming for teenagers", testActivity.getDescription());
        assertEquals(AssociationCategory.SPORT, testActivity.getCategory());
        assertEquals(ActivityLevel.INTERMEDIATE, testActivity.getLevel());
        assertEquals(12, testActivity.getMinAge());
        assertEquals(18, testActivity.getMaxAge());
        assertEquals(20, testActivity.getMaxCapacity());
        assertEquals(22000, testActivity.getPriceCents());
        assertEquals(LocalDate.of(2025, 10, 1), testActivity.getSeasonStart());
        assertEquals(LocalDate.of(2026, 7, 15), testActivity.getSeasonEnd());
    }

    @Test
    @DisplayName("should_mapSessionEntityCorrectly_when_toSessionResponse")
    void should_mapSessionEntityCorrectly_when_toSessionResponse() {
        SessionResponse response = mapper.toSessionResponse(testSession);

        assertNotNull(response);
        assertEquals(10L, response.id());
        assertEquals(5L, response.activityId());
        assertEquals(DayOfWeekEnum.MONDAY, response.dayOfWeek());
        assertEquals(LocalTime.of(18, 0), response.startTime());
        assertEquals(LocalTime.of(19, 0), response.endTime());
        assertEquals("Salle A", response.location());
        assertEquals("Jean Dupont", response.instructorName());
        assertEquals(20, response.maxCapacity());
        assertEquals(true, response.active());
    }

    @Test
    @DisplayName("should_createSessionFromRequest_when_toSessionEntity")
    void should_createSessionFromRequest_when_toSessionEntity() {
        SessionRequest request = new SessionRequest(
            DayOfWeekEnum.WEDNESDAY,
            LocalTime.of(14, 0),
            LocalTime.of(15, 30),
            "Gymnase B",
            "Marie Martin",
            15
        );

        Session entity = mapper.toSessionEntity(request, testActivity);

        assertNotNull(entity);
        assertEquals(testActivity, entity.getActivity());
        assertEquals(DayOfWeekEnum.WEDNESDAY, entity.getDayOfWeek());
        assertEquals(LocalTime.of(14, 0), entity.getStartTime());
        assertEquals(LocalTime.of(15, 30), entity.getEndTime());
        assertEquals("Gymnase B", entity.getLocation());
        assertEquals("Marie Martin", entity.getInstructorName());
        assertEquals(15, entity.getMaxCapacity());
    }

    @Test
    @DisplayName("should_updateExistingSession_when_updateSessionEntity")
    void should_updateExistingSession_when_updateSessionEntity() {
        SessionRequest request = new SessionRequest(
            DayOfWeekEnum.FRIDAY,
            LocalTime.of(16, 0),
            LocalTime.of(17, 30),
            "Piscine C",
            "Pierre Leroy",
            18
        );

        mapper.updateSessionEntity(testSession, request);

        assertEquals(DayOfWeekEnum.FRIDAY, testSession.getDayOfWeek());
        assertEquals(LocalTime.of(16, 0), testSession.getStartTime());
        assertEquals(LocalTime.of(17, 30), testSession.getEndTime());
        assertEquals("Piscine C", testSession.getLocation());
        assertEquals("Pierre Leroy", testSession.getInstructorName());
        assertEquals(18, testSession.getMaxCapacity());
    }
}
