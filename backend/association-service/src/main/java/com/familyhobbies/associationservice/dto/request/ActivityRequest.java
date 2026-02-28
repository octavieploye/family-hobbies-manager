package com.familyhobbies.associationservice.dto.request;

import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for creating or updating an activity.
 *
 * @param name        activity name (required)
 * @param description detailed description (optional)
 * @param category    activity category (required)
 * @param level       difficulty level (optional, defaults to ALL_LEVELS)
 * @param minAge      minimum age for registration (optional)
 * @param maxAge      maximum age for registration (optional)
 * @param maxCapacity maximum participants (optional)
 * @param priceCents  price in euro cents (required, >= 0)
 * @param seasonStart season start date (optional)
 * @param seasonEnd   season end date (optional)
 */
public record ActivityRequest(
    @NotBlank @Size(max = 200) String name,
    String description,
    @NotNull AssociationCategory category,
    ActivityLevel level,
    Integer minAge,
    Integer maxAge,
    Integer maxCapacity,
    @NotNull @Min(0) Integer priceCents,
    LocalDate seasonStart,
    LocalDate seasonEnd
) {
}
