package com.familyhobbies.associationservice.service.impl;

import com.familyhobbies.associationservice.dto.request.BulkAttendanceRequest;
import com.familyhobbies.associationservice.dto.request.MarkAttendanceRequest;
import com.familyhobbies.associationservice.dto.response.AttendanceResponse;
import com.familyhobbies.associationservice.dto.response.AttendanceSummaryResponse;
import com.familyhobbies.associationservice.entity.Attendance;
import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.AttendanceStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.event.AttendanceEventPublisher;
import com.familyhobbies.associationservice.mapper.AttendanceMapper;
import com.familyhobbies.associationservice.repository.AttendanceRepository;
import com.familyhobbies.associationservice.repository.SessionRepository;
import com.familyhobbies.associationservice.repository.SubscriptionRepository;
import com.familyhobbies.associationservice.service.AttendanceService;
import com.familyhobbies.errorhandling.exception.web.BadRequestException;
import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link AttendanceService}.
 * Handles attendance marking with business rules enforcement.
 */
@Service
@Transactional(readOnly = true)
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final AttendanceMapper attendanceMapper;
    private final AttendanceEventPublisher eventPublisher;

    public AttendanceServiceImpl(AttendanceRepository attendanceRepository,
                                  SessionRepository sessionRepository,
                                  SubscriptionRepository subscriptionRepository,
                                  AttendanceMapper attendanceMapper,
                                  AttendanceEventPublisher eventPublisher) {
        this.attendanceRepository = attendanceRepository;
        this.sessionRepository = sessionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.attendanceMapper = attendanceMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public AttendanceResponse markAttendance(MarkAttendanceRequest request, Long userId) {
        Session session = findSessionOrThrow(request.sessionId());
        Subscription subscription = findSubscriptionOrThrow(request.subscriptionId());

        validateSessionIsActive(session);
        validateSubscriptionIsActive(subscription);
        validateSubscriptionOwnership(subscription, userId);
        validateSessionDateNotFuture(request.sessionDate());

        Attendance attendance = attendanceMapper.toEntity(request, session, subscription, userId);

        Attendance saved = saveAttendanceHandlingDuplicates(attendance);

        eventPublisher.publishAttendanceMarked(saved);

        return attendanceMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public List<AttendanceResponse> markBulkAttendance(BulkAttendanceRequest request, Long userId) {
        Session session = findSessionOrThrow(request.sessionId());
        validateSessionIsActive(session);
        validateSessionDateNotFuture(request.sessionDate());

        List<AttendanceResponse> results = new ArrayList<>();

        for (BulkAttendanceRequest.AttendanceMark mark : request.marks()) {
            Subscription subscription = findSubscriptionOrThrow(mark.subscriptionId());
            validateSubscriptionIsActive(subscription);

            Attendance attendance = Attendance.builder()
                .session(session)
                .familyMemberId(mark.familyMemberId())
                .subscription(subscription)
                .sessionDate(request.sessionDate())
                .status(mark.status())
                .note(mark.note())
                .markedBy(userId)
                .build();

            Attendance saved = saveAttendanceHandlingDuplicates(attendance);

            eventPublisher.publishAttendanceMarked(saved);

            results.add(attendanceMapper.toResponse(saved));
        }

        return results;
    }

    @Override
    public List<AttendanceResponse> findBySessionAndDate(Long sessionId, LocalDate date, Long userId) {
        findSessionOrThrow(sessionId);
        List<Attendance> records = attendanceRepository.findBySession_IdAndSessionDate(sessionId, date);
        return records.stream().map(attendanceMapper::toResponse).toList();
    }

    @Override
    public List<AttendanceResponse> findByMemberId(Long memberId, Long userId) {
        List<Attendance> records = attendanceRepository.findByFamilyMemberId(memberId);
        validateMemberOwnership(records, userId);
        return records.stream().map(attendanceMapper::toResponse).toList();
    }

    @Override
    public AttendanceSummaryResponse getMemberSummary(Long memberId, Long userId) {
        List<Attendance> records = attendanceRepository.findByFamilyMemberId(memberId);
        validateMemberOwnership(records, userId);

        int total = (int) attendanceRepository.countByFamilyMemberId(memberId);
        int presentCount = (int) attendanceRepository.countByFamilyMemberIdAndStatus(
            memberId, AttendanceStatus.PRESENT);
        int absentCount = (int) attendanceRepository.countByFamilyMemberIdAndStatus(
            memberId, AttendanceStatus.ABSENT);
        int excusedCount = (int) attendanceRepository.countByFamilyMemberIdAndStatus(
            memberId, AttendanceStatus.EXCUSED);
        int lateCount = (int) attendanceRepository.countByFamilyMemberIdAndStatus(
            memberId, AttendanceStatus.LATE);

        double attendanceRate = total > 0 ? (double) presentCount / total * 100.0 : 0.0;

        return new AttendanceSummaryResponse(
            memberId,
            null, // memberFirstName - cross-service, populated if available
            null, // memberLastName - cross-service, populated if available
            total,
            presentCount,
            absentCount,
            excusedCount,
            lateCount,
            attendanceRate
        );
    }

    @Override
    public List<AttendanceResponse> findBySubscriptionId(Long subscriptionId, Long userId) {
        Subscription subscription = findSubscriptionOrThrow(subscriptionId);
        validateSubscriptionOwnership(subscription, userId);
        List<Attendance> records = attendanceRepository.findBySubscription_Id(subscriptionId);
        return records.stream().map(attendanceMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public AttendanceResponse updateAttendance(Long attendanceId, MarkAttendanceRequest request, Long userId) {
        Attendance attendance = attendanceRepository.findById(attendanceId)
            .orElseThrow(() -> ResourceNotFoundException.of("Attendance", attendanceId));

        validateSubscriptionOwnership(attendance.getSubscription(), userId);

        attendance.setStatus(request.status());
        attendance.setNote(request.note());

        Attendance saved = attendanceRepository.save(attendance);
        return attendanceMapper.toResponse(saved);
    }

    private Session findSessionOrThrow(Long sessionId) {
        return sessionRepository.findById(sessionId)
            .orElseThrow(() -> ResourceNotFoundException.of("Session", sessionId));
    }

    private Subscription findSubscriptionOrThrow(Long subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> ResourceNotFoundException.of("Subscription", subscriptionId));
    }

    private void validateSessionIsActive(Session session) {
        if (!session.isActive()) {
            throw new BadRequestException(
                "Cannot mark attendance on inactive session: " + session.getId());
        }
    }

    private void validateSubscriptionIsActive(Subscription subscription) {
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new BadRequestException(
                "Cannot mark attendance without an active subscription. Current status: "
                + subscription.getStatus());
        }
    }

    private void validateSubscriptionOwnership(Subscription subscription, Long userId) {
        if (!subscription.getUserId().equals(userId)) {
            throw new ForbiddenException(
                "You do not have permission to mark attendance for this subscription");
        }
    }

    private void validateSessionDateNotFuture(LocalDate sessionDate) {
        if (sessionDate.isAfter(LocalDate.now())) {
            throw new BadRequestException(
                "Cannot mark attendance for a future date: " + sessionDate);
        }
    }

    private void validateMemberOwnership(List<Attendance> records, Long userId) {
        if (!records.isEmpty()) {
            Subscription subscription = records.get(0).getSubscription();
            if (!subscription.getUserId().equals(userId)) {
                throw new ForbiddenException(
                    "You do not have permission to view attendance for this member");
            }
        }
    }

    private Attendance saveAttendanceHandlingDuplicates(Attendance attendance) {
        try {
            return attendanceRepository.save(attendance);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                "Attendance record already exists for member "
                + attendance.getFamilyMemberId()
                + " on session " + attendance.getSession().getId()
                + " for date " + attendance.getSessionDate());
        }
    }
}
