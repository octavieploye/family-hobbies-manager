package com.familyhobbies.associationservice.dto.request;

import com.familyhobbies.associationservice.entity.enums.AttendanceStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for marking a single attendance record.
 */
public record MarkAttendanceRequest(
    @NotNull Long sessionId,
    @NotNull Long familyMemberId,
    @NotNull Long subscriptionId,
    @NotNull LocalDate sessionDate,
    @NotNull AttendanceStatus status,
    @Size(max = 500) String note
) {}
