package com.familyhobbies.associationservice.service.impl;

import com.familyhobbies.associationservice.adapter.HelloAssoClient;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoPagination;
import com.familyhobbies.associationservice.dto.response.SyncResultResponse;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.mapper.AssociationMapper;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.common.config.HelloAssoProperties;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AssociationSyncServiceImpl}.
 *
 * Story: S5-003 -- AssociationSyncService
 * Tests: 17 test methods
 */
@ExtendWith(MockitoExtension.class)
class AssociationSyncServiceImplTest {

    @Mock
    private HelloAssoClient helloAssoClient;

    @Mock
    private AssociationRepository associationRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AssociationMapper associationMapper;
    private HelloAssoProperties properties;
    private AssociationSyncServiceImpl syncService;

    @BeforeEach
    void setUp() {
        associationMapper = new AssociationMapper();
        properties = new HelloAssoProperties();
        properties.setBaseUrl("http://localhost/v5");
        properties.setClientId("test-id");
        properties.setClientSecret("test-secret");
        properties.setTokenUrl("http://localhost/oauth2/token");

        HelloAssoProperties.Sync sync = new HelloAssoProperties.Sync();
        sync.setCities(List.of("Paris", "Lyon"));
        sync.setPageSize(20);
        properties.setSync(sync);

        syncService = new AssociationSyncServiceImpl(
            helloAssoClient, associationRepository, associationMapper,
            properties, kafkaTemplate
        );
    }

    // ── syncDirectory — creation ───────────────────────────────────────

    @Test
    @DisplayName("should_createNewAssociation_when_slugNotInDatabase")
    void should_createNewAssociation_when_slugNotInDatabase() {
        HelloAssoOrganization org = buildOrganization("club-paris", "Club Paris", "Paris", "sport");
        HelloAssoDirectoryResponse response = buildDirectoryResponse(List.of(org), 1, 1);

        when(helloAssoClient.searchOrganizations(any())).thenReturn(Mono.just(response));
        when(associationRepository.findByHelloassoSlug("club-paris")).thenReturn(Optional.empty());
        when(associationRepository.save(any(Association.class))).thenAnswer(i -> i.getArgument(0));

        SyncResultResponse result = syncService.syncDirectory();

        assertThat(result.created()).isGreaterThanOrEqualTo(1);
        verify(associationRepository, times(result.created())).save(any(Association.class));
    }

    @Test
    @DisplayName("should_reportCreatedCount_when_multipleNewOrganizations")
    void should_reportCreatedCount_when_multipleNewOrganizations() {
        HelloAssoOrganization org1 = buildOrganization("club-a", "Club A", "Paris", "sport");
        HelloAssoOrganization org2 = buildOrganization("club-b", "Club B", "Paris", "dance");
        HelloAssoDirectoryResponse response = buildDirectoryResponse(List.of(org1, org2), 1, 1);

        when(helloAssoClient.searchOrganizations(any())).thenReturn(Mono.just(response));
        when(associationRepository.findByHelloassoSlug(anyString())).thenReturn(Optional.empty());
        when(associationRepository.save(any(Association.class))).thenAnswer(i -> i.getArgument(0));

        SyncResultResponse result = syncService.syncDirectory();

        // Two cities, each returns 2 orgs = 4 total created
        assertThat(result.created()).isEqualTo(4);
    }

    // ── syncDirectory — update ─────────────────────────────────────────

    @Test
    @DisplayName("should_updateExistingAssociation_when_dataHasChanged")
    void should_updateExistingAssociation_when_dataHasChanged() {
        HelloAssoOrganization org = buildOrganization("club-paris", "Club Paris Updated", "Paris", "sport");
        HelloAssoDirectoryResponse response = buildDirectoryResponse(List.of(org), 1, 1);

        Association existing = buildExistingAssociation("club-paris", "Club Paris Old", "Paris");
        when(helloAssoClient.searchOrganizations(any())).thenReturn(Mono.just(response));
        when(associationRepository.findByHelloassoSlug("club-paris")).thenReturn(Optional.of(existing));
        when(associationRepository.save(any(Association.class))).thenAnswer(i -> i.getArgument(0));

        SyncResultResponse result = syncService.syncDirectory();

        // Updated across both cities
        assertThat(result.updated()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("should_notSave_when_existingAssociationHasNoChanges")
    void should_notSave_when_existingAssociationHasNoChanges() {
        HelloAssoOrganization org = buildOrganization("club-paris", "Club Paris", "Paris", "sport");
        HelloAssoDirectoryResponse response = buildDirectoryResponse(List.of(org), 1, 1);

        Association existing = buildExistingAssociation("club-paris", "Club Paris", "Paris");
        existing.setPostalCode(null);
        existing.setDepartment(null);
        existing.setRegion(null);
        existing.setWebsite(null);
        existing.setLogoUrl(null);
        existing.setDescription(null);
        existing.setCategory(AssociationCategory.SPORT);

        when(helloAssoClient.searchOrganizations(any())).thenReturn(Mono.just(response));
        when(associationRepository.findByHelloassoSlug("club-paris")).thenReturn(Optional.of(existing));

        SyncResultResponse result = syncService.syncDirectory();

        // Unchanged across both cities
        assertThat(result.unchanged()).isGreaterThanOrEqualTo(1);
    }

    // ── syncDirectory — pagination ─────────────────────────────────────

    @Test
    @DisplayName("should_paginateThroughAllPages_when_multiplePages")
    void should_paginateThroughAllPages_when_multiplePages() {
        HelloAssoOrganization org1 = buildOrganization("club-page1", "Club Page1", "Paris", "sport");
        HelloAssoOrganization org2 = buildOrganization("club-page2", "Club Page2", "Paris", "dance");

        HelloAssoDirectoryResponse page1 = buildDirectoryResponse(List.of(org1), 2, 0);
        HelloAssoDirectoryResponse page2 = buildDirectoryResponse(List.of(org2), 2, 1);

        when(helloAssoClient.searchOrganizations(any()))
            .thenReturn(Mono.just(page1))
            .thenReturn(Mono.just(page2))
            .thenReturn(Mono.just(page1))
            .thenReturn(Mono.just(page2));
        when(associationRepository.findByHelloassoSlug(anyString())).thenReturn(Optional.empty());
        when(associationRepository.save(any(Association.class))).thenAnswer(i -> i.getArgument(0));

        SyncResultResponse result = syncService.syncDirectory();

        // 2 cities x 2 pages = 4 API calls minimum
        assertThat(result.totalProcessed()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("should_stopPaginating_when_emptyResponse")
    void should_stopPaginating_when_emptyResponse() {
        HelloAssoDirectoryResponse emptyResponse = new HelloAssoDirectoryResponse(List.of(), null);

        when(helloAssoClient.searchOrganizations(any())).thenReturn(Mono.just(emptyResponse));

        SyncResultResponse result = syncService.syncDirectory();

        assertThat(result.totalProcessed()).isEqualTo(0);
    }

    // ── syncDirectory — event publishing ───────────────────────────────

    @Test
    @DisplayName("should_publishKafkaEvent_when_syncCompletes")
    void should_publishKafkaEvent_when_syncCompletes() {
        HelloAssoDirectoryResponse emptyResponse = new HelloAssoDirectoryResponse(List.of(), null);
        when(helloAssoClient.searchOrganizations(any())).thenReturn(Mono.just(emptyResponse));

        syncService.syncDirectory();

        verify(kafkaTemplate).send(eq("helloasso-sync-completed"), any());
    }

    @Test
    @DisplayName("should_completeSyncEvenIfKafkaFails_when_publishThrows")
    void should_completeSyncEvenIfKafkaFails_when_publishThrows() {
        HelloAssoDirectoryResponse emptyResponse = new HelloAssoDirectoryResponse(List.of(), null);
        when(helloAssoClient.searchOrganizations(any())).thenReturn(Mono.just(emptyResponse));
        when(kafkaTemplate.send(anyString(), any())).thenThrow(new RuntimeException("Kafka down"));

        SyncResultResponse result = syncService.syncDirectory();

        assertThat(result).isNotNull();
        assertThat(result.totalProcessed()).isEqualTo(0);
    }

    // ── syncDirectory — slug handling ──────────────────────────────────

    @Test
    @DisplayName("should_skipOrganization_when_slugIsNull")
    void should_skipOrganization_when_slugIsNull() {
        HelloAssoOrganization orgWithoutSlug = new HelloAssoOrganization(
            "No Slug Club", null, null, "Paris", null, null, null, null, null, null, null, null, null);
        HelloAssoDirectoryResponse response = buildDirectoryResponse(List.of(orgWithoutSlug), 1, 1);

        when(helloAssoClient.searchOrganizations(any())).thenReturn(Mono.just(response));

        SyncResultResponse result = syncService.syncDirectory();

        verify(associationRepository, never()).save(any());
        assertThat(result.unchanged()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("should_skipOrganization_when_slugIsBlank")
    void should_skipOrganization_when_slugIsBlank() {
        HelloAssoOrganization orgBlankSlug = new HelloAssoOrganization(
            "Blank Slug Club", "  ", null, "Paris", null, null, null, null, null, null, null, null, null);
        HelloAssoDirectoryResponse response = buildDirectoryResponse(List.of(orgBlankSlug), 1, 1);

        when(helloAssoClient.searchOrganizations(any())).thenReturn(Mono.just(response));

        SyncResultResponse result = syncService.syncDirectory();

        verify(associationRepository, never()).save(any());
    }

    // ── syncOrganization ───────────────────────────────────────────────

    @Test
    @DisplayName("should_createAssociation_when_syncSingleNewOrganization")
    void should_createAssociation_when_syncSingleNewOrganization() {
        HelloAssoOrganization org = buildOrganization("new-club", "New Club", "Nantes", "musique");
        when(helloAssoClient.getOrganization("new-club")).thenReturn(Mono.just(org));
        when(associationRepository.findByHelloassoSlug("new-club")).thenReturn(Optional.empty());
        when(associationRepository.save(any(Association.class))).thenAnswer(i -> i.getArgument(0));

        SyncResultResponse result = syncService.syncOrganization("new-club");

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(0);
        assertThat(result.totalProcessed()).isEqualTo(1);
    }

    @Test
    @DisplayName("should_updateAssociation_when_syncSingleExistingOrganization")
    void should_updateAssociation_when_syncSingleExistingOrganization() {
        HelloAssoOrganization org = buildOrganization("existing-club", "Updated Name", "Lyon", "sport");
        Association existing = buildExistingAssociation("existing-club", "Old Name", "Lyon");

        when(helloAssoClient.getOrganization("existing-club")).thenReturn(Mono.just(org));
        when(associationRepository.findByHelloassoSlug("existing-club")).thenReturn(Optional.of(existing));
        when(associationRepository.save(any(Association.class))).thenAnswer(i -> i.getArgument(0));

        SyncResultResponse result = syncService.syncOrganization("existing-club");

        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(0);
    }

    @Test
    @DisplayName("should_throwExternalApiException_when_helloAssoReturnsNull")
    void should_throwExternalApiException_when_helloAssoReturnsNull() {
        when(helloAssoClient.getOrganization("ghost")).thenReturn(Mono.empty());

        assertThatThrownBy(() -> syncService.syncOrganization("ghost"))
            .isInstanceOf(ExternalApiException.class)
            .hasMessageContaining("null");
    }

    // ── isStale ────────────────────────────────────────────────────────

    @Test
    @DisplayName("should_returnTrue_when_associationNeverSynced")
    void should_returnTrue_when_associationNeverSynced() {
        Association association = Association.builder()
            .lastSyncedAt(null)
            .build();

        assertThat(syncService.isStale(association)).isTrue();
    }

    @Test
    @DisplayName("should_returnTrue_when_lastSyncOlderThan24Hours")
    void should_returnTrue_when_lastSyncOlderThan24Hours() {
        Association association = Association.builder()
            .lastSyncedAt(OffsetDateTime.now().minusHours(25))
            .build();

        assertThat(syncService.isStale(association)).isTrue();
    }

    @Test
    @DisplayName("should_returnFalse_when_lastSyncWithin24Hours")
    void should_returnFalse_when_lastSyncWithin24Hours() {
        Association association = Association.builder()
            .lastSyncedAt(OffsetDateTime.now().minusHours(1))
            .build();

        assertThat(syncService.isStale(association)).isFalse();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private HelloAssoOrganization buildOrganization(String slug, String name, String city, String category) {
        return new HelloAssoOrganization(
            name, slug, null, city, null, null, null, null, null, category, null, null, null);
    }

    private HelloAssoDirectoryResponse buildDirectoryResponse(List<HelloAssoOrganization> orgs,
                                                                int totalPages, int pageIndex) {
        HelloAssoPagination pagination = new HelloAssoPagination(
            pageIndex, 20, orgs.size() * totalPages, totalPages, null);
        return new HelloAssoDirectoryResponse(orgs, pagination);
    }

    private Association buildExistingAssociation(String helloassoSlug, String name, String city) {
        return Association.builder()
            .id(1L)
            .name(name)
            .slug(helloassoSlug)
            .helloassoSlug(helloassoSlug)
            .city(city)
            .category(AssociationCategory.SPORT)
            .status(AssociationStatus.ACTIVE)
            .lastSyncedAt(OffsetDateTime.now().minusHours(25))
            .build();
    }
}
