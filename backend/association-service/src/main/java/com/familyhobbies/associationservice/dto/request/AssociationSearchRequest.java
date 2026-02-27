package com.familyhobbies.associationservice.dto.request;

import com.familyhobbies.associationservice.entity.enums.AssociationCategory;

/**
 * Search criteria for filtering associations.
 * All fields are optional — null means "no filter on this field".
 *
 * @param city     filter by city name (case-insensitive)
 * @param category filter by association category
 * @param keyword  free-text search across name and description
 * @param page     page number (0-based), defaults to 0
 * @param size     page size, defaults to 20
 */
public record AssociationSearchRequest(
    String city,
    AssociationCategory category,
    String keyword,
    Integer page,
    Integer size
) {
    /**
     * Returns the page number, defaulting to 0 if null.
     */
    public int pageOrDefault() {
        return page != null ? page : 0;
    }

    /**
     * Returns the page size, defaulting to 20 if null.
     */
    public int sizeOrDefault() {
        return size != null ? size : 20;
    }
}
