package com.familyhobbies.associationservice.mapper;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.dto.response.AssociationDetailResponse;
import com.familyhobbies.associationservice.dto.response.AssociationResponse;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Maps {@link Association} entities to response DTOs and HelloAsso organizations to entities.
 * Manual mapper (no MapStruct) for full control and transparency.
 */
@Component
public class AssociationMapper {

    private static final Map<String, AssociationCategory> CATEGORY_MAP = Map.ofEntries(
        Map.entry("sport", AssociationCategory.SPORT),
        Map.entry("sports", AssociationCategory.SPORT),
        Map.entry("danse", AssociationCategory.DANCE),
        Map.entry("dance", AssociationCategory.DANCE),
        Map.entry("musique", AssociationCategory.MUSIC),
        Map.entry("music", AssociationCategory.MUSIC),
        Map.entry("theatre", AssociationCategory.THEATER),
        Map.entry("theater", AssociationCategory.THEATER),
        Map.entry("art", AssociationCategory.ART),
        Map.entry("arts", AssociationCategory.ART),
        Map.entry("arts plastiques", AssociationCategory.ART),
        Map.entry("arts martiaux", AssociationCategory.MARTIAL_ARTS),
        Map.entry("martial arts", AssociationCategory.MARTIAL_ARTS),
        Map.entry("combat", AssociationCategory.MARTIAL_ARTS),
        Map.entry("bien-etre", AssociationCategory.WELLNESS),
        Map.entry("bien-être", AssociationCategory.WELLNESS),
        Map.entry("wellness", AssociationCategory.WELLNESS),
        Map.entry("yoga", AssociationCategory.WELLNESS),
        Map.entry("meditation", AssociationCategory.WELLNESS)
    );

    /**
     * Maps an association entity to a summary response (list view).
     *
     * @param entity the association entity
     * @return the summary response DTO
     */
    public AssociationResponse toResponse(Association entity) {
        if (entity == null) {
            return null;
        }
        return new AssociationResponse(
            entity.getId(),
            entity.getName(),
            entity.getSlug(),
            entity.getCity(),
            entity.getPostalCode(),
            entity.getCategory(),
            entity.getStatus(),
            entity.getLogoUrl()
        );
    }

    /**
     * Maps an association entity to a detailed response (single view).
     *
     * @param entity the association entity
     * @return the detail response DTO
     */
    public AssociationDetailResponse toDetailResponse(Association entity) {
        if (entity == null) {
            return null;
        }
        return new AssociationDetailResponse(
            entity.getId(),
            entity.getName(),
            entity.getSlug(),
            entity.getDescription(),
            entity.getAddress(),
            entity.getCity(),
            entity.getPostalCode(),
            entity.getDepartment(),
            entity.getRegion(),
            entity.getPhone(),
            entity.getEmail(),
            entity.getWebsite(),
            entity.getLogoUrl(),
            entity.getHelloassoSlug(),
            entity.getCategory(),
            entity.getStatus(),
            entity.getLastSyncedAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * Creates a new {@link Association} entity from a HelloAsso organization.
     * Sets defaults for status (ACTIVE) and category (via normalizeCategory).
     *
     * @param org the HelloAsso organization
     * @return a new Association entity (not yet persisted)
     */
    public Association fromHelloAsso(HelloAssoOrganization org) {
        if (org == null) {
            return null;
        }
        return Association.builder()
            .name(org.name())
            .slug(org.slug())
            .description(org.description())
            .city(org.city())
            .postalCode(org.zipCode())
            .department(org.department())
            .region(org.region())
            .website(org.url())
            .logoUrl(org.logo())
            .helloassoSlug(org.slug())
            .category(normalizeCategory(org.category()))
            .status(AssociationStatus.ACTIVE)
            .lastSyncedAt(OffsetDateTime.now())
            .build();
    }

    /**
     * Updates mutable fields of an existing {@link Association} from HelloAsso data.
     * Does not change id, slug, helloassoSlug, status, or timestamps.
     *
     * @param entity the existing association to update
     * @param org    the HelloAsso organization with fresh data
     */
    public void updateFromHelloAsso(Association entity, HelloAssoOrganization org) {
        if (entity == null || org == null) {
            return;
        }
        entity.setName(org.name());
        entity.setDescription(org.description());
        entity.setCity(org.city());
        entity.setPostalCode(org.zipCode());
        entity.setDepartment(org.department());
        entity.setRegion(org.region());
        entity.setWebsite(org.url());
        entity.setLogoUrl(org.logo());
        entity.setCategory(normalizeCategory(org.category()));
        entity.setLastSyncedAt(OffsetDateTime.now());
    }

    /**
     * Normalizes a French or English category string to an {@link AssociationCategory} enum value.
     * Returns {@link AssociationCategory#OTHER} if the category is unknown or null.
     *
     * @param category the raw category string from HelloAsso
     * @return the normalized enum value
     */
    public AssociationCategory normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return AssociationCategory.OTHER;
        }
        return CATEGORY_MAP.getOrDefault(category.toLowerCase().trim(), AssociationCategory.OTHER);
    }
}
