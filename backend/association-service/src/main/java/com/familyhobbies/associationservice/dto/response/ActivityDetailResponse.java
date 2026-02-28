package com.familyhobbies.associationservice.dto.response;

import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Detailed response for single activity views, including embedded sessions.
 *
 * @param id              unique identifier
 * @param associationId   owning association ID
 * @param associationName owning association name
 * @param name            activity name
 * @param description     full description
 * @param category        activity category
 * @param level           difficulty level
 * @param minAge          minimum age
 * @param maxAge          maximum age
 * @param maxCapacity     maximum participants
 * @param priceCents      price in euro cents
 * @param seasonStart     season start date
 * @param seasonEnd       season end date
 * @param status          current status
 * @param sessions        list of sessions
 * @param createdAt       creation timestamp
 * @param updatedAt       last update timestamp
 */
public record ActivityDetailResponse(
    Long id,
    Long associationId,
    String associationName,
    String name,
    String description,
    AssociationCategory category,
    ActivityLevel level,
    Integer minAge,
    Integer maxAge,
    Integer maxCapacity,
    Integer priceCents,
    LocalDate seasonStart,
    LocalDate seasonEnd,
    ActivityStatus status,
    List<SessionResponse> sessions,
    Instant createdAt,
    Instant updatedAt
) {
}
