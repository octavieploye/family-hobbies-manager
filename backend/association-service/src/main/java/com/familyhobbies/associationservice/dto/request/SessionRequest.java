package com.familyhobbies.associationservice.dto.request;

import com.familyhobbies.associationservice.entity.enums.DayOfWeekEnum;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

/**
 * Request DTO for creating or updating a session.
 *
 * @param dayOfWeek      day of the week (required)
 * @param startTime      session start time (required)
 * @param endTime        session end time (required)
 * @param location       room/venue name (optional)
 * @param instructorName instructor name (optional)
 * @param maxCapacity    session-level capacity override (optional)
 */
public record SessionRequest(
    @NotNull DayOfWeekEnum dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @Size(max = 200) String location,
    @Size(max = 100) String instructorName,
    Integer maxCapacity
) {
}
