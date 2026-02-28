package com.familyhobbies.associationservice.service.impl;

import com.familyhobbies.associationservice.dto.request.BulkAttendanceRequest;
import com.familyhobbies.associationservice.dto.request.MarkAttendanceRequest;
import com.familyhobbies.associationservice.dto.response.AttendanceResponse;
import com.familyhobbies.associationservice.dto.response.AttendanceSummaryResponse;
import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Attendance;
import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.AttendanceStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionType;
import com.familyhobbies.associationservice.event.AttendanceEventPublisher;
import com.familyhobbies.associationservice.mapper.AttendanceMapper;
import com.familyhobbies.associationservice.repository.AttendanceRepository;
import com.familyhobbies.associationservice.repository.SessionRepository;
import com.familyhobbies.associationservice.repository.SubscriptionRepository;
import com.familyhobbies.errorhandling.exception.web.BadRequestException;
import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AttendanceServiceImpl.
 *
 * Story: S4-001 -- Attendance Entity + API
 * Tests: 14 test methods
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private AttendanceMapper attendanceMapper;

    @Mock
    private AttendanceEventPublisher eventPublisher;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    private Session testSession;
    private Subscription testSubscription;
    private Attendance testAttendance;
    private AttendanceResponse testResponse;
    private MarkAttendanceRequest testRequest;

    @BeforeEach
    void setUp() {
        Activity activity = Activity.builder()
            .id(1L)
            .name("Natation enfants")
            .build();

        testSession = Session.builder()
            .id(1L)
            .activity(activity)
            .active(true)
            .build();

        testSubscription = Subscription.builder()
            .id(1L)
            .activity(activity)
            .familyMemberId(10L)
            .familyId(5L)
            .userId(100L)
            .subscriptionType(SubscriptionType.ADHESION)
            .status(SubscriptionStatus.ACTIVE)
            .startDate(LocalDate.of(2025, 9, 1))
            .build();

        testAttendance = Attendance.builder()
            .id(1L)
            .session(testSession)
            .familyMemberId(10L)
            .subscription(testSubscription)
            .sessionDate(LocalDate.of(2025, 10, 15))
            .status(AttendanceStatus.PRESENT)
            .markedBy(100L)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        testResponse = new AttendanceResponse(
            1L, 1L, 10L, null, null, 1L,
            LocalDate.of(2025, 10, 15), AttendanceStatus.PRESENT,
            null, 100L, Instant.now(), Instant.now()
        );

        testRequest = new MarkAttendanceRequest(
            1L, 10L, 1L,
            LocalDate.of(2025, 10, 15),
            AttendanceStatus.PRESENT, null
        );
    }

    @Test
    @DisplayName("should_markAttendance_when_validRequest")
    void should_markAttendance_when_validRequest() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));
        when(attendanceMapper.toEntity(testRequest, testSession, testSubscription, 100L))
            .thenReturn(testAttendance);
        when(attendanceRepository.save(testAttendance)).thenReturn(testAttendance);
        when(attendanceMapper.toResponse(testAttendance)).thenReturn(testResponse);

        AttendanceResponse result = attendanceService.markAttendance(testRequest, 100L);

        assertThat(result.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(result.sessionId()).isEqualTo(1L);
        verify(eventPublisher).publishAttendanceMarked(testAttendance);
    }

    @Test
    @DisplayName("should_throwResourceNotFound_when_sessionNotFound")
    void should_throwResourceNotFound_when_sessionNotFound() {
        when(sessionRepository.findById(999L)).thenReturn(Optional.empty());

        MarkAttendanceRequest request = new MarkAttendanceRequest(
            999L, 10L, 1L, LocalDate.of(2025, 10, 15),
            AttendanceStatus.PRESENT, null
        );

        assertThatThrownBy(() -> attendanceService.markAttendance(request, 100L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should_throwResourceNotFound_when_subscriptionNotFound")
    void should_throwResourceNotFound_when_subscriptionNotFound() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(subscriptionRepository.findById(999L)).thenReturn(Optional.empty());

        MarkAttendanceRequest request = new MarkAttendanceRequest(
            1L, 10L, 999L, LocalDate.of(2025, 10, 15),
            AttendanceStatus.PRESENT, null
        );

        assertThatThrownBy(() -> attendanceService.markAttendance(request, 100L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should_throwBadRequest_when_sessionNotActive")
    void should_throwBadRequest_when_sessionNotActive() {
        Session inactiveSession = Session.builder()
            .id(2L)
            .active(false)
            .build();

        when(sessionRepository.findById(2L)).thenReturn(Optional.of(inactiveSession));
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));

        MarkAttendanceRequest request = new MarkAttendanceRequest(
            2L, 10L, 1L, LocalDate.of(2025, 10, 15),
            AttendanceStatus.PRESENT, null
        );

        assertThatThrownBy(() -> attendanceService.markAttendance(request, 100L))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("inactive session");
    }

    @Test
    @DisplayName("should_throwBadRequest_when_subscriptionNotActive")
    void should_throwBadRequest_when_subscriptionNotActive() {
        Subscription pendingSubscription = Subscription.builder()
            .id(2L)
            .userId(100L)
            .status(SubscriptionStatus.PENDING)
            .build();

        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(subscriptionRepository.findById(2L)).thenReturn(Optional.of(pendingSubscription));

        MarkAttendanceRequest request = new MarkAttendanceRequest(
            1L, 10L, 2L, LocalDate.of(2025, 10, 15),
            AttendanceStatus.PRESENT, null
        );

        assertThatThrownBy(() -> attendanceService.markAttendance(request, 100L))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("active subscription");
    }

    @Test
    @DisplayName("should_throwForbidden_when_notSubscriptionOwner")
    void should_throwForbidden_when_notSubscriptionOwner() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));

        assertThatThrownBy(() -> attendanceService.markAttendance(testRequest, 999L))
            .isInstanceOf(ForbiddenException.class);

        verify(eventPublisher, never()).publishAttendanceMarked(any());
    }

    @Test
    @DisplayName("should_throwBadRequest_when_futureDateProvided")
    void should_throwBadRequest_when_futureDateProvided() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));

        MarkAttendanceRequest futureRequest = new MarkAttendanceRequest(
            1L, 10L, 1L,
            LocalDate.now().plusDays(5),
            AttendanceStatus.PRESENT, null
        );

        assertThatThrownBy(() -> attendanceService.markAttendance(futureRequest, 100L))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("future date");
    }

    @Test
    @DisplayName("should_throwConflict_when_duplicateAttendance")
    void should_throwConflict_when_duplicateAttendance() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));
        when(attendanceMapper.toEntity(testRequest, testSession, testSubscription, 100L))
            .thenReturn(testAttendance);
        when(attendanceRepository.save(testAttendance))
            .thenThrow(new DataIntegrityViolationException("Duplicate"));

        assertThatThrownBy(() -> attendanceService.markAttendance(testRequest, 100L))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("should_returnAttendanceList_when_findBySessionAndDate")
    void should_returnAttendanceList_when_findBySessionAndDate() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(testSession));
        when(attendanceRepository.findBySession_IdAndSessionDate(1L, LocalDate.of(2025, 10, 15)))
            .thenReturn(List.of(testAttendance));
        when(attendanceMapper.toResponse(testAttendance)).thenReturn(testResponse);

        List<AttendanceResponse> results = attendanceService.findBySessionAndDate(
            1L, LocalDate.of(2025, 10, 15), 100L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("should_returnMemberHistory_when_findByMemberId")
    void should_returnMemberHistory_when_findByMemberId() {
        when(attendanceRepository.findByFamilyMemberId(10L)).thenReturn(List.of(testAttendance));
        when(attendanceMapper.toResponse(testAttendance)).thenReturn(testResponse);

        List<AttendanceResponse> results = attendanceService.findByMemberId(10L, 100L);

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("should_throwForbidden_when_notOwnerForMemberHistory")
    void should_throwForbidden_when_notOwnerForMemberHistory() {
        when(attendanceRepository.findByFamilyMemberId(10L)).thenReturn(List.of(testAttendance));

        assertThatThrownBy(() -> attendanceService.findByMemberId(10L, 999L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("should_returnSummary_when_getMemberSummary")
    void should_returnSummary_when_getMemberSummary() {
        when(attendanceRepository.findByFamilyMemberId(10L)).thenReturn(List.of(testAttendance));
        when(attendanceRepository.countByFamilyMemberId(10L)).thenReturn(4L);
        when(attendanceRepository.countByFamilyMemberIdAndStatus(10L, AttendanceStatus.PRESENT)).thenReturn(3L);
        when(attendanceRepository.countByFamilyMemberIdAndStatus(10L, AttendanceStatus.ABSENT)).thenReturn(0L);
        when(attendanceRepository.countByFamilyMemberIdAndStatus(10L, AttendanceStatus.EXCUSED)).thenReturn(1L);
        when(attendanceRepository.countByFamilyMemberIdAndStatus(10L, AttendanceStatus.LATE)).thenReturn(0L);

        AttendanceSummaryResponse result = attendanceService.getMemberSummary(10L, 100L);

        assertThat(result.totalSessions()).isEqualTo(4);
        assertThat(result.presentCount()).isEqualTo(3);
        assertThat(result.excusedCount()).isEqualTo(1);
        assertThat(result.attendanceRate()).isEqualTo(75.0);
    }

    @Test
    @DisplayName("should_returnSubscriptionAttendance_when_findBySubscriptionId")
    void should_returnSubscriptionAttendance_when_findBySubscriptionId() {
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));
        when(attendanceRepository.findBySubscription_Id(1L)).thenReturn(List.of(testAttendance));
        when(attendanceMapper.toResponse(testAttendance)).thenReturn(testResponse);

        List<AttendanceResponse> results = attendanceService.findBySubscriptionId(1L, 100L);

        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("should_updateAttendance_when_validRequest")
    void should_updateAttendance_when_validRequest() {
        MarkAttendanceRequest updateRequest = new MarkAttendanceRequest(
            1L, 10L, 1L, LocalDate.of(2025, 10, 15),
            AttendanceStatus.EXCUSED, "Was sick"
        );

        AttendanceResponse updatedResponse = new AttendanceResponse(
            1L, 1L, 10L, null, null, 1L,
            LocalDate.of(2025, 10, 15), AttendanceStatus.EXCUSED,
            "Was sick", 100L, Instant.now(), Instant.now()
        );

        when(attendanceRepository.findById(1L)).thenReturn(Optional.of(testAttendance));
        when(attendanceRepository.save(testAttendance)).thenReturn(testAttendance);
        when(attendanceMapper.toResponse(testAttendance)).thenReturn(updatedResponse);

        AttendanceResponse result = attendanceService.updateAttendance(1L, updateRequest, 100L);

        assertThat(result.status()).isEqualTo(AttendanceStatus.EXCUSED);
        assertThat(result.note()).isEqualTo("Was sick");
    }
}
