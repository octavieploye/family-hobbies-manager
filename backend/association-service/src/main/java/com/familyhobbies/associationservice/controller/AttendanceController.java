package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.dto.request.BulkAttendanceRequest;
import com.familyhobbies.associationservice.dto.request.MarkAttendanceRequest;
import com.familyhobbies.associationservice.dto.response.AttendanceResponse;
import com.familyhobbies.associationservice.dto.response.AttendanceSummaryResponse;
import com.familyhobbies.associationservice.service.AttendanceService;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Attendance", description = "Attendance tracking: mark, view, and summarize attendance records")
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
    @Operation(summary = "Mark attendance",
               description = "Records a single attendance entry for a member at a session")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Attendance recorded"),
        @ApiResponse(responseCode = "400", description = "Invalid attendance data"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required"),
        @ApiResponse(responseCode = "409", description = "Attendance already recorded for this session and date")
    })
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
    @Operation(summary = "Bulk mark attendance",
               description = "Records attendance for multiple members at a session in one request")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bulk attendance recorded"),
        @ApiResponse(responseCode = "400", description = "Invalid bulk attendance data"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required")
    })
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
    @Operation(summary = "Get attendance by session",
               description = "Returns attendance records for a session on a specific date")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Attendance records returned"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required")
    })
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
    @Operation(summary = "Get attendance by member",
               description = "Returns all attendance records for a family member")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Attendance history returned"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required")
    })
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
    @Operation(summary = "Get member attendance summary",
               description = "Returns attendance summary statistics for a family member")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Attendance summary returned"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required")
    })
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
    @Operation(summary = "Get attendance by subscription",
               description = "Returns all attendance records linked to a subscription")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Attendance records returned"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required")
    })
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
    @Operation(summary = "Update attendance",
               description = "Updates an existing attendance record")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Attendance updated"),
        @ApiResponse(responseCode = "400", description = "Invalid attendance data"),
        @ApiResponse(responseCode = "403", description = "FAMILY role required"),
        @ApiResponse(responseCode = "404", description = "Attendance record not found")
    })
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
