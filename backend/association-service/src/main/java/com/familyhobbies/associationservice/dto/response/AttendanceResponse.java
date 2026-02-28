package com.familyhobbies.associationservice.dto.response;

import com.familyhobbies.associationservice.entity.enums.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response DTO for an attendance record.
 */
public record AttendanceResponse(
    Long id,
    Long sessionId,
    Long familyMemberId,
    String memberFirstName,
    String memberLastName,
    Long subscriptionId,
    LocalDate sessionDate,
    AttendanceStatus status,
    String note,
    Long markedBy,
    Instant createdAt,
    Instant updatedAt
) {}
