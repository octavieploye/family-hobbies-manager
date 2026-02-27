package com.familyhobbies.associationservice.mapper;

import com.familyhobbies.associationservice.dto.response.AssociationDetailResponse;
import com.familyhobbies.associationservice.dto.response.AssociationResponse;
import com.familyhobbies.associationservice.entity.Association;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Association} entities to response DTOs.
 * Manual mapper (no MapStruct) for full control and transparency.
 */
@Component
public class AssociationMapper {

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
}
