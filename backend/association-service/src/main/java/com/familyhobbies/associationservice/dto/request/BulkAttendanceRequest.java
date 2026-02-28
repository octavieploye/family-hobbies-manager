package com.familyhobbies.associationservice.dto.request;

import com.familyhobbies.associationservice.entity.enums.AttendanceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Request DTO for bulk marking attendance for a session on a specific date.
 */
public record BulkAttendanceRequest(
    @NotNull Long sessionId,
    @NotNull LocalDate sessionDate,
    @NotEmpty @Valid List<AttendanceMark> marks
) {

    /**
     * Individual attendance mark within a bulk request.
     */
    public record AttendanceMark(
        @NotNull Long familyMemberId,
        @NotNull Long subscriptionId,
        @NotNull AttendanceStatus status,
        String note
    ) {}
}
