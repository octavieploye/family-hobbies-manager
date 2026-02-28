package com.familyhobbies.associationservice.dto.response;

import com.familyhobbies.associationservice.entity.enums.DayOfWeekEnum;

import java.time.LocalTime;

/**
 * Response DTO for session details.
 *
 * @param id             unique identifier
 * @param activityId     parent activity ID
 * @param dayOfWeek      day of the week
 * @param startTime      session start time
 * @param endTime        session end time
 * @param location       room/venue name
 * @param instructorName instructor name
 * @param maxCapacity    session-level capacity override
 * @param active         whether the session is active
 */
public record SessionResponse(
    Long id,
    Long activityId,
    DayOfWeekEnum dayOfWeek,
    LocalTime startTime,
    LocalTime endTime,
    String location,
    String instructorName,
    Integer maxCapacity,
    boolean active
) {
}
