package com.familyhobbies.associationservice.dto.response;

import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;

import java.time.OffsetDateTime;

/**
 * Detailed response for single association views.
 * Contains all public fields of an association.
 *
 * @param id              unique identifier
 * @param name            association name
 * @param slug            URL-friendly unique slug
 * @param description     full description
 * @param address         street address
 * @param city            city
 * @param postalCode      postal code
 * @param department      department (e.g. Rhone)
 * @param region          region (e.g. Auvergne-Rhone-Alpes)
 * @param phone           phone number
 * @param email           contact email
 * @param website         website URL
 * @param logoUrl         logo URL
 * @param helloassoSlug   HelloAsso organization slug
 * @param category        association category
 * @param status          current status
 * @param lastSyncedAt    last HelloAsso sync timestamp
 * @param createdAt       creation timestamp
 * @param updatedAt       last update timestamp
 */
public record AssociationDetailResponse(
    Long id,
    String name,
    String slug,
    String description,
    String address,
    String city,
    String postalCode,
    String department,
    String region,
    String phone,
    String email,
    String website,
    String logoUrl,
    String helloassoSlug,
    AssociationCategory category,
    AssociationStatus status,
    OffsetDateTime lastSyncedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
