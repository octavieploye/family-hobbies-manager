package com.familyhobbies.associationservice.batch.processor;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelloAssoItemProcessorTest {

    @Mock
    private AssociationRepository associationRepository;

    private HelloAssoItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new HelloAssoItemProcessor(associationRepository);
    }

    @Test
    @DisplayName("Should create new Association for unknown slug")
    void shouldCreateNewAssociation() {
        HelloAssoOrganization incoming = HelloAssoOrganization.builder()
                .name("Club de Danse Paris").slug("club-danse-paris")
                .city("Paris").zipCode("75001").category("Danse")
                .description("Cours de danse pour tous").build();

        when(associationRepository.findByHelloassoSlug("club-danse-paris"))
                .thenReturn(Optional.empty());

        Association result = processor.process(incoming);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Club de Danse Paris");
        assertThat(result.getCity()).isEqualTo("Paris");
        assertThat(result.getPostalCode()).isEqualTo("75001");
        assertThat(result.getHelloassoSlug()).isEqualTo("club-danse-paris");
        assertThat(result.getStatus()).isEqualTo(AssociationStatus.ACTIVE);
        assertThat(result.getLastSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should update existing Association when HelloAsso data changed")
    void shouldUpdateExistingAssociation() {
        Association existing = new Association();
        existing.setId(42L);
        existing.setHelloassoSlug("club-danse-paris");
        existing.setName("Old Name");
        existing.setLastSyncedAt(OffsetDateTime.now().minusDays(1));

        HelloAssoOrganization incoming = HelloAssoOrganization.builder()
                .name("Club de Danse Paris Updated").slug("club-danse-paris")
                .city("Paris").zipCode("75001").category("DANCE")
                .updatedDate(OffsetDateTime.now()).build();

        when(associationRepository.findByHelloassoSlug("club-danse-paris"))
                .thenReturn(Optional.of(existing));

        Association result = processor.process(incoming);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(42L);
        assertThat(result.getName()).isEqualTo("Club de Danse Paris Updated");
    }

    @Test
    @DisplayName("Should return null for unchanged Association (skip)")
    void shouldReturnNullForUnchangedAssociation() {
        Association existing = new Association();
        existing.setId(42L);
        existing.setHelloassoSlug("club-danse-paris");
        existing.setLastSyncedAt(OffsetDateTime.now());

        HelloAssoOrganization incoming = HelloAssoOrganization.builder()
                .name("Club de Danse Paris").slug("club-danse-paris")
                .updatedDate(OffsetDateTime.now().minusDays(1)).build();

        when(associationRepository.findByHelloassoSlug("club-danse-paris"))
                .thenReturn(Optional.of(existing));

        Association result = processor.process(incoming);
        assertThat(result).isNull();
    }
}
