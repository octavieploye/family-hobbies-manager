package com.familyhobbies.associationservice.service.impl;

import com.familyhobbies.associationservice.dto.request.AssociationSearchRequest;
import com.familyhobbies.associationservice.dto.response.AssociationDetailResponse;
import com.familyhobbies.associationservice.dto.response.AssociationResponse;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.mapper.AssociationMapper;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AssociationServiceImpl.
 *
 * Story: S2-003 -- Association Entity + Search
 * Tests: 4 test methods
 *
 * Uses @ExtendWith(MockitoExtension.class) -- no Spring context loaded.
 * Mocks: AssociationRepository, AssociationMapper.
 */
@ExtendWith(MockitoExtension.class)
class AssociationServiceImplTest {

    @Mock
    private AssociationRepository associationRepository;

    @Mock
    private AssociationMapper associationMapper;

    @InjectMocks
    private AssociationServiceImpl associationService;

    private Association testAssociation;
    private AssociationResponse testResponse;
    private AssociationDetailResponse testDetailResponse;

    @BeforeEach
    void setUp() {
        testAssociation = Association.builder()
            .id(1L)
            .name("Lyon Natation Metropole")
            .slug("lyon-natation-metropole")
            .description("Club de natation proposant des cours pour tous niveaux")
            .address("12 Rue des Bains")
            .city("Lyon")
            .postalCode("69003")
            .department("Rhone")
            .region("Auvergne-Rhone-Alpes")
            .phone("+33 4 72 33 45 67")
            .email("contact@lyon-natation.fr")
            .website("https://www.lyon-natation-metropole.fr")
            .logoUrl("https://cdn.familyhobbies.fr/logos/lyon-natation.png")
            .helloassoSlug("lyon-natation-metropole")
            .category(AssociationCategory.SPORT)
            .status(AssociationStatus.ACTIVE)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        testResponse = new AssociationResponse(
            1L, "Lyon Natation Metropole", "lyon-natation-metropole",
            "Lyon", "69003", AssociationCategory.SPORT,
            AssociationStatus.ACTIVE,
            "https://cdn.familyhobbies.fr/logos/lyon-natation.png"
        );

        testDetailResponse = new AssociationDetailResponse(
            1L, "Lyon Natation Metropole", "lyon-natation-metropole",
            "Club de natation proposant des cours pour tous niveaux",
            "12 Rue des Bains", "Lyon", "69003", "Rhone",
            "Auvergne-Rhone-Alpes", "+33 4 72 33 45 67",
            "contact@lyon-natation.fr", "https://www.lyon-natation-metropole.fr",
            "https://cdn.familyhobbies.fr/logos/lyon-natation.png",
            "lyon-natation-metropole", AssociationCategory.SPORT,
            AssociationStatus.ACTIVE, null,
            testAssociation.getCreatedAt(), testAssociation.getUpdatedAt()
        );
    }

    @Test
    @DisplayName("should_returnPaginatedResults_when_searchWithFilters")
    @SuppressWarnings("unchecked")
    void should_returnPaginatedResults_when_searchWithFilters() {
        // Given
        AssociationSearchRequest request = new AssociationSearchRequest(
            "Lyon", AssociationCategory.SPORT, null, 0, 20
        );

        Page<Association> entityPage = new PageImpl<>(List.of(testAssociation));
        when(associationRepository.findAll(any(Specification.class), any(Pageable.class)))
            .thenReturn(entityPage);
        when(associationMapper.toResponse(testAssociation)).thenReturn(testResponse);

        // When
        Page<AssociationResponse> result = associationService.searchAssociations(request);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Lyon Natation Metropole");
        assertThat(result.getContent().get(0).city()).isEqualTo("Lyon");
        assertThat(result.getContent().get(0).category()).isEqualTo(AssociationCategory.SPORT);
    }

    @Test
    @DisplayName("should_returnAssociationDetail_when_validId")
    void should_returnAssociationDetail_when_validId() {
        // Given
        when(associationRepository.findById(1L)).thenReturn(Optional.of(testAssociation));
        when(associationMapper.toDetailResponse(testAssociation)).thenReturn(testDetailResponse);

        // When
        AssociationDetailResponse result = associationService.getAssociationById(1L);

        // Then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Lyon Natation Metropole");
        assertThat(result.slug()).isEqualTo("lyon-natation-metropole");
        assertThat(result.city()).isEqualTo("Lyon");
        assertThat(result.category()).isEqualTo(AssociationCategory.SPORT);
    }

    @Test
    @DisplayName("should_throwResourceNotFoundException_when_idNotFound")
    void should_throwResourceNotFoundException_when_idNotFound() {
        // Given
        when(associationRepository.findById(999L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> associationService.getAssociationById(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("Association not found with id: 999");
    }

    @Test
    @DisplayName("should_returnAssociationDetail_when_validSlug")
    void should_returnAssociationDetail_when_validSlug() {
        // Given
        when(associationRepository.findBySlug("lyon-natation-metropole"))
            .thenReturn(Optional.of(testAssociation));
        when(associationMapper.toDetailResponse(testAssociation)).thenReturn(testDetailResponse);

        // When
        AssociationDetailResponse result = associationService.getAssociationBySlug("lyon-natation-metropole");

        // Then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Lyon Natation Metropole");
        assertThat(result.slug()).isEqualTo("lyon-natation-metropole");
    }
}
