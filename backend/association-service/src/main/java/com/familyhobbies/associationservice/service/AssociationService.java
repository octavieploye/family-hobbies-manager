package com.familyhobbies.associationservice.service;

import com.familyhobbies.associationservice.dto.request.AssociationSearchRequest;
import com.familyhobbies.associationservice.dto.response.AssociationDetailResponse;
import com.familyhobbies.associationservice.dto.response.AssociationResponse;
import org.springframework.data.domain.Page;

/**
 * Service interface for association operations.
 */
public interface AssociationService {

    /**
     * Searches associations with optional filters (city, category, keyword).
     * Returns a paginated result set.
     *
     * @param request the search criteria
     * @return a page of association summary responses
     */
    Page<AssociationResponse> searchAssociations(AssociationSearchRequest request);

    /**
     * Retrieves a single association by its database ID.
     *
     * @param id the association ID
     * @return the detailed association response
     * @throws com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException if not found
     */
    AssociationDetailResponse getAssociationById(Long id);

    /**
     * Retrieves a single association by its URL slug.
     *
     * @param slug the unique slug
     * @return the detailed association response
     * @throws com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException if not found
     */
    AssociationDetailResponse getAssociationBySlug(String slug);
}
