package com.familyhobbies.associationservice.dto.response;

import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;

/**
 * Summary response for association list views.
 * Contains only the fields needed for search result cards.
 *
 * @param id         unique identifier
 * @param name       association name
 * @param slug       URL-friendly unique slug
 * @param city       city where the association is located
 * @param postalCode postal code
 * @param category   association category (SPORT, DANCE, etc.)
 * @param status     current status (ACTIVE, INACTIVE, ARCHIVED)
 * @param logoUrl    URL to the association logo
 */
public record AssociationResponse(
    Long id,
    String name,
    String slug,
    String city,
    String postalCode,
    AssociationCategory category,
    AssociationStatus status,
    String logoUrl
) {
}
