package com.familyhobbies.associationservice.dto.response;

import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;

/**
 * Summary response for activity list views.
 *
 * @param id           unique identifier
 * @param name         activity name
 * @param category     activity category
 * @param level        difficulty level
 * @param minAge       minimum age
 * @param maxAge       maximum age
 * @param priceCents   price in euro cents
 * @param status       current status
 * @param sessionCount number of sessions
 */
public record ActivityResponse(
    Long id,
    String name,
    AssociationCategory category,
    ActivityLevel level,
    Integer minAge,
    Integer maxAge,
    Integer priceCents,
    ActivityStatus status,
    int sessionCount
) {
}
