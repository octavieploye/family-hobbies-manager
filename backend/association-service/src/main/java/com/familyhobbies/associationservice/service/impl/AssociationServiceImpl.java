package com.familyhobbies.associationservice.service.impl;

import com.familyhobbies.associationservice.dto.request.AssociationSearchRequest;
import com.familyhobbies.associationservice.dto.response.AssociationDetailResponse;
import com.familyhobbies.associationservice.dto.response.AssociationResponse;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.mapper.AssociationMapper;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.associationservice.repository.AssociationSpecification;
import com.familyhobbies.associationservice.service.AssociationService;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AssociationService}.
 * Handles association search and retrieval with JPA Specifications.
 */
@Service
@Transactional(readOnly = true)
public class AssociationServiceImpl implements AssociationService {

    private final AssociationRepository associationRepository;
    private final AssociationMapper associationMapper;

    public AssociationServiceImpl(AssociationRepository associationRepository,
                                  AssociationMapper associationMapper) {
        this.associationRepository = associationRepository;
        this.associationMapper = associationMapper;
    }

    @Override
    public Page<AssociationResponse> searchAssociations(AssociationSearchRequest request) {
        Pageable pageable = PageRequest.of(
            request.pageOrDefault(),
            request.sizeOrDefault(),
            Sort.by(Sort.Direction.ASC, "name")
        );

        Specification<Association> spec = AssociationSpecification.withFilters(
            request.city(),
            request.category(),
            request.keyword()
        );

        return associationRepository.findAll(spec, pageable)
            .map(associationMapper::toResponse);
    }

    @Override
    public AssociationDetailResponse getAssociationById(Long id) {
        Association association = associationRepository.findById(id)
            .orElseThrow(() -> ResourceNotFoundException.of("Association", id));
        return associationMapper.toDetailResponse(association);
    }

    @Override
    public AssociationDetailResponse getAssociationBySlug(String slug) {
        Association association = associationRepository.findBySlug(slug)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Association", slug, "Association not found with slug: " + slug));
        return associationMapper.toDetailResponse(association);
    }
}
