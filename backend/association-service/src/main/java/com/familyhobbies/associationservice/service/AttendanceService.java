package com.familyhobbies.associationservice.service;

import com.familyhobbies.associationservice.dto.request.BulkAttendanceRequest;
import com.familyhobbies.associationservice.dto.request.MarkAttendanceRequest;
import com.familyhobbies.associationservice.dto.response.AttendanceResponse;
import com.familyhobbies.associationservice.dto.response.AttendanceSummaryResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Service interface for attendance tracking operations.
 */
public interface AttendanceService {

    /**
     * Marks a single attendance record.
     */
    AttendanceResponse markAttendance(MarkAttendanceRequest request, Long userId);

    /**
     * Marks attendance for multiple members of a session at once.
     */
    List<AttendanceResponse> markBulkAttendance(BulkAttendanceRequest request, Long userId);

    /**
     * Gets attendance records for a session on a specific date.
     */
    List<AttendanceResponse> findBySessionAndDate(Long sessionId, LocalDate date, Long userId);

    /**
     * Gets attendance history for a family member.
     */
    List<AttendanceResponse> findByMemberId(Long memberId, Long userId);

    /**
     * Gets attendance summary stats for a family member.
     */
    AttendanceSummaryResponse getMemberSummary(Long memberId, Long userId);

    /**
     * Gets attendance records for a subscription.
     */
    List<AttendanceResponse> findBySubscriptionId(Long subscriptionId, Long userId);

    /**
     * Updates an existing attendance record.
     */
    AttendanceResponse updateAttendance(Long attendanceId, MarkAttendanceRequest request, Long userId);
}
