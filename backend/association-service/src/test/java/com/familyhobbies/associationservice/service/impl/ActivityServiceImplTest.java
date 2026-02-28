package com.familyhobbies.associationservice.service.impl;

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
import com.familyhobbies.associationservice.mapper.ActivityMapper;
import com.familyhobbies.associationservice.repository.ActivityRepository;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.associationservice.repository.SessionRepository;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ActivityServiceImpl.
 *
 * Story: S3-002 -- Activity & Session Controller + API
 * Tests: 12 test methods
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceImplTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private AssociationRepository associationRepository;

    @Mock
    private ActivityMapper activityMapper;

    @InjectMocks
    private ActivityServiceImpl activityService;

    private Association testAssociation;
    private Activity testActivity;
    private Session testSession;

    @BeforeEach
    void setUp() {
        testAssociation = Association.builder()
            .id(1L)
            .name("Lyon Natation Metropole")
            .slug("lyon-natation-metropole")
            .category(AssociationCategory.SPORT)
            .status(AssociationStatus.ACTIVE)
            .build();

        testActivity = Activity.builder()
            .id(1L)
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
            .sessions(new ArrayList<>())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        testSession = Session.builder()
            .id(1L)
            .activity(testActivity)
            .dayOfWeek(DayOfWeekEnum.TUESDAY)
            .startTime(LocalTime.of(17, 0))
            .endTime(LocalTime.of(18, 0))
            .location("Piscine municipale")
            .instructorName("Marie Dupont")
            .maxCapacity(15)
            .active(true)
            .build();
    }

    @Test
    @DisplayName("should_returnPaginatedActivities_when_listActivities")
    void should_returnPaginatedActivities_when_listActivities() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Activity> activityPage = new PageImpl<>(List.of(testActivity));
        ActivityResponse expectedResponse = new ActivityResponse(
            1L, "Natation enfants", AssociationCategory.SPORT, ActivityLevel.BEGINNER,
            6, 10, 18000, ActivityStatus.ACTIVE, 0
        );

        when(associationRepository.existsById(1L)).thenReturn(true);
        when(activityRepository.findByAssociation_Id(1L, pageable)).thenReturn(activityPage);
        when(activityMapper.toResponse(testActivity)).thenReturn(expectedResponse);

        Page<ActivityResponse> result = activityService.listActivities(1L, null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Natation enfants");
    }

    @Test
    @DisplayName("should_throwResourceNotFound_when_associationNotFoundForList")
    void should_throwResourceNotFound_when_associationNotFoundForList() {
        when(associationRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> activityService.listActivities(999L, null, null, PageRequest.of(0, 20)))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should_returnActivityDetail_when_validIds")
    void should_returnActivityDetail_when_validIds() {
        ActivityDetailResponse expectedResponse = new ActivityDetailResponse(
            1L, 1L, "Lyon Natation Metropole", "Natation enfants", "Cours de natation pour enfants",
            AssociationCategory.SPORT, ActivityLevel.BEGINNER, 6, 10, 15, 18000,
            LocalDate.of(2025, 9, 1), LocalDate.of(2026, 6, 30), ActivityStatus.ACTIVE,
            Collections.emptyList(), Instant.now(), Instant.now()
        );

        when(activityRepository.findByIdAndAssociation_Id(1L, 1L)).thenReturn(Optional.of(testActivity));
        when(activityMapper.toDetailResponse(testActivity)).thenReturn(expectedResponse);

        ActivityDetailResponse result = activityService.getActivityDetail(1L, 1L);

        assertThat(result.name()).isEqualTo("Natation enfants");
        assertThat(result.associationName()).isEqualTo("Lyon Natation Metropole");
    }

    @Test
    @DisplayName("should_throwResourceNotFound_when_activityNotFound")
    void should_throwResourceNotFound_when_activityNotFound() {
        when(activityRepository.findByIdAndAssociation_Id(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.getActivityDetail(1L, 999L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should_createActivity_when_validRequest")
    void should_createActivity_when_validRequest() {
        ActivityRequest request = new ActivityRequest(
            "Natation enfants", "Cours de natation", AssociationCategory.SPORT,
            ActivityLevel.BEGINNER, 6, 10, 15, 18000,
            LocalDate.of(2025, 9, 1), LocalDate.of(2026, 6, 30)
        );
        ActivityDetailResponse expectedResponse = new ActivityDetailResponse(
            1L, 1L, "Lyon Natation Metropole", "Natation enfants", "Cours de natation",
            AssociationCategory.SPORT, ActivityLevel.BEGINNER, 6, 10, 15, 18000,
            LocalDate.of(2025, 9, 1), LocalDate.of(2026, 6, 30), ActivityStatus.ACTIVE,
            Collections.emptyList(), Instant.now(), Instant.now()
        );

        when(associationRepository.findById(1L)).thenReturn(Optional.of(testAssociation));
        when(activityMapper.toEntity(request, testAssociation)).thenReturn(testActivity);
        when(activityRepository.save(testActivity)).thenReturn(testActivity);
        when(activityMapper.toDetailResponse(testActivity)).thenReturn(expectedResponse);

        ActivityDetailResponse result = activityService.createActivity(1L, request);

        assertThat(result.name()).isEqualTo("Natation enfants");
        verify(activityRepository).save(testActivity);
    }

    @Test
    @DisplayName("should_throwResourceNotFound_when_creatingActivityForMissingAssociation")
    void should_throwResourceNotFound_when_creatingActivityForMissingAssociation() {
        ActivityRequest request = new ActivityRequest(
            "Test", "desc", AssociationCategory.SPORT, ActivityLevel.ALL_LEVELS,
            null, null, null, 0, null, null
        );

        when(associationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.createActivity(999L, request))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should_updateActivity_when_validRequest")
    void should_updateActivity_when_validRequest() {
        ActivityRequest request = new ActivityRequest(
            "Updated name", "Updated desc", AssociationCategory.SPORT,
            ActivityLevel.INTERMEDIATE, 8, 14, 20, 25000, null, null
        );
        ActivityDetailResponse expectedResponse = new ActivityDetailResponse(
            1L, 1L, "Lyon Natation Metropole", "Updated name", "Updated desc",
            AssociationCategory.SPORT, ActivityLevel.INTERMEDIATE, 8, 14, 20, 25000,
            null, null, ActivityStatus.ACTIVE, Collections.emptyList(), Instant.now(), Instant.now()
        );

        when(activityRepository.findByIdAndAssociation_Id(1L, 1L)).thenReturn(Optional.of(testActivity));
        when(activityRepository.save(testActivity)).thenReturn(testActivity);
        when(activityMapper.toDetailResponse(testActivity)).thenReturn(expectedResponse);

        ActivityDetailResponse result = activityService.updateActivity(1L, 1L, request);

        assertThat(result.name()).isEqualTo("Updated name");
        verify(activityMapper).updateEntity(testActivity, request);
    }

    @Test
    @DisplayName("should_softDeleteActivity_when_deleteIsCalled")
    void should_softDeleteActivity_when_deleteIsCalled() {
        when(activityRepository.findByIdAndAssociation_Id(1L, 1L)).thenReturn(Optional.of(testActivity));
        when(activityRepository.save(testActivity)).thenReturn(testActivity);

        activityService.deleteActivity(1L, 1L);

        assertThat(testActivity.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
        verify(activityRepository).save(testActivity);
    }

    @Test
    @DisplayName("should_returnSessions_when_listSessions")
    void should_returnSessions_when_listSessions() {
        SessionResponse expectedSession = new SessionResponse(
            1L, 1L, DayOfWeekEnum.TUESDAY, LocalTime.of(17, 0), LocalTime.of(18, 0),
            "Piscine municipale", "Marie Dupont", 15, true
        );

        when(activityRepository.findByIdAndAssociation_Id(1L, 1L)).thenReturn(Optional.of(testActivity));
        when(sessionRepository.findByActivity_IdAndActiveTrue(1L)).thenReturn(List.of(testSession));
        when(activityMapper.toSessionResponse(testSession)).thenReturn(expectedSession);

        List<SessionResponse> result = activityService.listSessions(1L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).dayOfWeek()).isEqualTo(DayOfWeekEnum.TUESDAY);
    }

    @Test
    @DisplayName("should_createSession_when_validRequest")
    void should_createSession_when_validRequest() {
        SessionRequest request = new SessionRequest(
            DayOfWeekEnum.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(15, 0),
            "Studio A", "Claire Martin", 12
        );
        SessionResponse expectedResponse = new SessionResponse(
            2L, 1L, DayOfWeekEnum.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(15, 0),
            "Studio A", "Claire Martin", 12, true
        );

        when(activityRepository.findByIdAndAssociation_Id(1L, 1L)).thenReturn(Optional.of(testActivity));
        when(activityMapper.toSessionEntity(request, testActivity)).thenReturn(testSession);
        when(sessionRepository.save(testSession)).thenReturn(testSession);
        when(activityMapper.toSessionResponse(testSession)).thenReturn(expectedResponse);

        SessionResponse result = activityService.createSession(1L, 1L, request);

        assertThat(result.dayOfWeek()).isEqualTo(DayOfWeekEnum.WEDNESDAY);
        verify(sessionRepository).save(testSession);
    }

    @Test
    @DisplayName("should_updateSession_when_validRequest")
    void should_updateSession_when_validRequest() {
        SessionRequest request = new SessionRequest(
            DayOfWeekEnum.FRIDAY, LocalTime.of(18, 0), LocalTime.of(19, 0),
            "Salle B", "Jean Paul", 20
        );
        SessionResponse expectedResponse = new SessionResponse(
            1L, 1L, DayOfWeekEnum.FRIDAY, LocalTime.of(18, 0), LocalTime.of(19, 0),
            "Salle B", "Jean Paul", 20, true
        );

        when(activityRepository.findByIdAndAssociation_Id(1L, 1L)).thenReturn(Optional.of(testActivity));
        when(sessionRepository.findByIdAndActivity_Id(1L, 1L)).thenReturn(Optional.of(testSession));
        when(sessionRepository.save(testSession)).thenReturn(testSession);
        when(activityMapper.toSessionResponse(testSession)).thenReturn(expectedResponse);

        SessionResponse result = activityService.updateSession(1L, 1L, 1L, request);

        assertThat(result.dayOfWeek()).isEqualTo(DayOfWeekEnum.FRIDAY);
        verify(activityMapper).updateSessionEntity(testSession, request);
    }

    @Test
    @DisplayName("should_deactivateSession_when_deleteIsCalled")
    void should_deactivateSession_when_deleteIsCalled() {
        when(activityRepository.findByIdAndAssociation_Id(1L, 1L)).thenReturn(Optional.of(testActivity));
        when(sessionRepository.findByIdAndActivity_Id(1L, 1L)).thenReturn(Optional.of(testSession));
        when(sessionRepository.save(testSession)).thenReturn(testSession);

        activityService.deleteSession(1L, 1L, 1L);

        assertThat(testSession.isActive()).isFalse();
        verify(sessionRepository).save(testSession);
    }
}
