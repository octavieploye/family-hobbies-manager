package com.familyhobbies.associationservice.dto.request;

import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;

/**
 * Search criteria for filtering activities within an association.
 * All fields are optional -- null means "no filter on this field".
 *
 * @param category filter by activity category
 * @param level    filter by activity level
 * @param minAge   filter by minimum age
 * @param maxAge   filter by maximum age
 */
public record ActivitySearchRequest(
    AssociationCategory category,
    ActivityLevel level,
    Integer minAge,
    Integer maxAge
) {
}
