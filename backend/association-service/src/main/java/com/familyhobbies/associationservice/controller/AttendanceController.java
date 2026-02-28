package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.dto.request.BulkAttendanceRequest;
import com.familyhobbies.associationservice.dto.request.MarkAttendanceRequest;
import com.familyhobbies.associationservice.dto.response.AttendanceResponse;
import com.familyhobbies.associationservice.dto.response.AttendanceSummaryResponse;
import com.familyhobbies.associationservice.service.AttendanceService;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for attendance tracking operations.
 *
 * Path prefix: /api/v1/attendance
 *
 * Protected endpoints: all require FAMILY role (via X-User-Id header).
 * Bulk endpoint also supports ASSOCIATION role.
 */
@RestController
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    /**
     * Mark a single attendance record.
     * POST /api/v1/attendance
     */
    @PostMapping
    public ResponseEntity<AttendanceResponse> markAttendance(
            @Valid @RequestBody MarkAttendanceRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        AttendanceResponse result = attendanceService.markAttendance(request, userId);
        URI location = URI.create("/api/v1/attendance/" + result.id());
        return ResponseEntity.created(location).body(result);
    }

    /**
     * Bulk mark attendance for a session (all members at once).
     * POST /api/v1/attendance/bulk
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<AttendanceResponse>> markBulkAttendance(
            @Valid @RequestBody BulkAttendanceRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        List<AttendanceResponse> results = attendanceService.markBulkAttendance(request, userId);
        return ResponseEntity.ok(results);
    }

    /**
     * Get attendance for a session on a specific date.
     * GET /api/v1/attendance/session/{sessionId}?date=YYYY-MM-DD
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<AttendanceResponse>> findBySessionAndDate(
            @PathVariable Long sessionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        List<AttendanceResponse> results = attendanceService.findBySessionAndDate(sessionId, date, userId);
        return ResponseEntity.ok(results);
    }

    /**
     * Get attendance history for a family member.
     * GET /api/v1/attendance/member/{memberId}
     */
    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<AttendanceResponse>> findByMemberId(
            @PathVariable Long memberId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        List<AttendanceResponse> results = attendanceService.findByMemberId(memberId, userId);
        return ResponseEntity.ok(results);
    }

    /**
     * Get attendance summary stats for a family member.
     * GET /api/v1/attendance/member/{memberId}/summary
     */
    @GetMapping("/member/{memberId}/summary")
    public ResponseEntity<AttendanceSummaryResponse> getMemberSummary(
            @PathVariable Long memberId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        AttendanceSummaryResponse result = attendanceService.getMemberSummary(memberId, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Get attendance for a subscription.
     * GET /api/v1/attendance/subscription/{subscriptionId}
     */
    @GetMapping("/subscription/{subscriptionId}")
    public ResponseEntity<List<AttendanceResponse>> findBySubscriptionId(
            @PathVariable Long subscriptionId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        List<AttendanceResponse> results = attendanceService.findBySubscriptionId(subscriptionId, userId);
        return ResponseEntity.ok(results);
    }

    /**
     * Update an existing attendance record.
     * PUT /api/v1/attendance/{attendanceId}
     */
    @PutMapping("/{attendanceId}")
    public ResponseEntity<AttendanceResponse> updateAttendance(
            @PathVariable Long attendanceId,
            @Valid @RequestBody MarkAttendanceRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateFamilyRole(roles);
        AttendanceResponse result = attendanceService.updateAttendance(attendanceId, request, userId);
        return ResponseEntity.ok(result);
    }

    /**
     * Validates that the caller has the FAMILY role (or ADMIN which inherits FAMILY).
     *
     * @param roles comma-separated roles from X-User-Roles header
     * @throws ForbiddenException if FAMILY role is not present
     */
    private void validateFamilyRole(String roles) {
        if (roles == null || (!roles.contains("FAMILY") && !roles.contains("ADMIN"))) {
            throw new ForbiddenException("FAMILY role required to access attendance endpoints");
        }
    }
}
