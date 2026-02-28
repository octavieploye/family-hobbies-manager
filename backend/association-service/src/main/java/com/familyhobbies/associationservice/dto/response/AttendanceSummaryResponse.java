package com.familyhobbies.associationservice.dto.response;

/**
 * Response DTO for attendance summary statistics per family member.
 */
public record AttendanceSummaryResponse(
    Long familyMemberId,
    String memberFirstName,
    String memberLastName,
    int totalSessions,
    int presentCount,
    int absentCount,
    int excusedCount,
    int lateCount,
    double attendanceRate
) {}
