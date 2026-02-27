package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.dto.request.AssociationSearchRequest;
import com.familyhobbies.associationservice.dto.response.AssociationDetailResponse;
import com.familyhobbies.associationservice.dto.response.AssociationResponse;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.service.AssociationService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for association search and retrieval.
 * All read endpoints are public (no JWT required).
 */
@RestController
@RequestMapping("/api/v1/associations")
public class AssociationController {

    private final AssociationService associationService;

    public AssociationController(AssociationService associationService) {
        this.associationService = associationService;
    }

    /**
     * Search associations with optional filters.
     * GET /api/v1/associations?city=Lyon&category=SPORT&keyword=natation&page=0&size=20
     */
    @GetMapping
    public ResponseEntity<Page<AssociationResponse>> searchAssociations(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) AssociationCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        AssociationSearchRequest request = new AssociationSearchRequest(
            city, category, keyword, page, size
        );

        Page<AssociationResponse> results = associationService.searchAssociations(request);
        return ResponseEntity.ok(results);
    }

    /**
     * Get a single association by its database ID.
     * GET /api/v1/associations/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AssociationDetailResponse> getAssociationById(
            @PathVariable Long id) {
        AssociationDetailResponse response = associationService.getAssociationById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Get a single association by its URL slug.
     * GET /api/v1/associations/slug/{slug}
     */
    @GetMapping("/slug/{slug}")
    public ResponseEntity<AssociationDetailResponse> getAssociationBySlug(
            @PathVariable String slug) {
        AssociationDetailResponse response = associationService.getAssociationBySlug(slug);
        return ResponseEntity.ok(response);
    }
}
