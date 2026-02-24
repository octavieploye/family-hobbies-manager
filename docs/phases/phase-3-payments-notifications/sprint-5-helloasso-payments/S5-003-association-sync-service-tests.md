# Story S5-003: AssociationSyncService -- Failing Tests (TDD Contract)

> Companion file to [S5-003-association-sync-service.md](./S5-003-association-sync-service.md)
> Contains the full JUnit 5 test source code for `AssociationSyncServiceImplTest`.

---

## Test File

**Path**: `backend/association-service/src/test/java/com/familyhobbies/associationservice/service/AssociationSyncServiceImplTest.java`

```java
package com.familyhobbies.associationservice.service;

import com.familyhobbies.associationservice.adapter.HelloAssoClient;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoPagination;
import com.familyhobbies.associationservice.config.HelloAssoProperties;
import com.familyhobbies.associationservice.dto.SyncResultResponse;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.mapper.AssociationMapper;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.associationservice.service.impl.AssociationSyncServiceImpl;
import com.familyhobbies.common.event.HelloAssoSyncCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
 * <p>Uses Mockito to mock dependencies (HelloAssoClient, AssociationRepository,
 * KafkaTemplate) and verify the sync service logic in isolation.
 *
 * <p>17 tests covering:
 * <ul>
 *   <li>Upsert logic (4) -- create new, update existing, count unchanged, no duplicates</li>
 *   <li>Pagination (3) -- follow continuationToken, stop on empty page, stop on null token</li>
 *   <li>Sync result (2) -- accurate counts, last_synced_at set</li>
 *   <li>Kafka event (2) -- event published to correct topic, event contains counts</li>
 *   <li>Staleness check (4) -- stale after 24h, fresh within 24h, null last_synced_at, non-HelloAsso</li>
 *   <li>Single org sync (2) -- sync by slug, return null when not found</li>
 * </ul>
 *
 * <p>Required test dependencies:
 * <pre>
 * &lt;dependency&gt;
 *     &lt;groupId&gt;org.springframework.boot&lt;/groupId&gt;
 *     &lt;artifactId&gt;spring-boot-starter-test&lt;/artifactId&gt;
 *     &lt;scope&gt;test&lt;/scope&gt;
 * &lt;/dependency&gt;
 * &lt;dependency&gt;
 *     &lt;groupId&gt;org.springframework.kafka&lt;/groupId&gt;
 *     &lt;artifactId&gt;spring-kafka-test&lt;/artifactId&gt;
 *     &lt;scope&gt;test&lt;/scope&gt;
 * &lt;/dependency&gt;
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssociationSyncServiceImpl")
class AssociationSyncServiceImplTest {

    @Mock
    private HelloAssoClient helloAssoClient;

    @Mock
    private AssociationRepository associationRepository;

    @Mock
    private AssociationMapper associationMapper;

    @Mock
    private HelloAssoProperties properties;

    @Mock
    private HelloAssoProperties.SyncProperties syncProperties;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<Association> associationCaptor;

    @Captor
    private ArgumentCaptor<HelloAssoSyncCompletedEvent> eventCaptor;

    private AssociationSyncServiceImpl syncService;

    // ── Test Data ─────────────────────────────────────────────────────────

    private static HelloAssoOrganization buildOrg(String name, String slug,
                                                   String city, String zip,
                                                   String category) {
        return new HelloAssoOrganization(
                name, slug, city, zip,
                "Description de " + name,
                "https://cdn.helloasso.com/img/" + slug + ".png",
                category,
                null, null, null, null
        );
    }

    private static Association buildExistingAssociation(String name, String slug,
                                                         String city, String zip,
                                                         String category) {
        return Association.builder()
                .id(1L)
                .name(name)
                .slug(slug)
                .helloassoSlug(slug)
                .city(city)
                .postalCode(zip)
                .category(category)
                .status("ACTIVE")
                .lastSyncedAt(Instant.now().minus(48, ChronoUnit.HOURS))
                .build();
    }

    private HelloAssoDirectoryResponse buildDirectoryResponse(
            List<HelloAssoOrganization> orgs, String continuationToken) {
        HelloAssoPagination pagination = continuationToken != null
                ? new HelloAssoPagination(20, orgs.size(), 1, 1, continuationToken)
                : new HelloAssoPagination(20, orgs.size(), 1, 1, null);
        return new HelloAssoDirectoryResponse(orgs, pagination);
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        when(properties.getSync()).thenReturn(syncProperties);
        when(syncProperties.getCities()).thenReturn(List.of("Lyon", "Paris"));
        when(syncProperties.getPageSize()).thenReturn(20);

        syncService = new AssociationSyncServiceImpl(
                helloAssoClient,
                associationRepository,
                associationMapper,
                properties,
                kafkaTemplate
        );
    }

    // ── Upsert Logic Tests ────────────────────────────────────────────────

    @Nested
    @DisplayName("Upsert Logic")
    class UpsertLogic {

        @Test
        @DisplayName("should_create_new_association_when_slug_does_not_exist_locally")
        void should_create_new_association_when_slug_does_not_exist_locally() {
            // Given
            HelloAssoOrganization org = buildOrg(
                    "Association Sportive de Lyon", "as-lyon",
                    "Lyon", "69001", "Sport");

            Association newEntity = Association.builder()
                    .name("Association Sportive de Lyon")
                    .slug("as-lyon")
                    .helloassoSlug("as-lyon")
                    .city("Lyon")
                    .postalCode("69001")
                    .category("SPORT")
                    .status("ACTIVE")
                    .build();

            HelloAssoDirectoryResponse response =
                    buildDirectoryResponse(List.of(org), null);

            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.just(response))
                    .thenReturn(Mono.just(
                            buildDirectoryResponse(List.of(), null)));

            when(associationRepository.findByHelloassoSlug("as-lyon"))
                    .thenReturn(Optional.empty());
            when(associationMapper.fromHelloAsso(org)).thenReturn(newEntity);
            when(associationRepository.save(any())).thenReturn(newEntity);

            // When
            SyncResultResponse result = syncService.syncDirectory();

            // Then
            verify(associationMapper).fromHelloAsso(org);
            verify(associationMapper, never()).updateFromHelloAsso(any(), any());
            verify(associationRepository, times(2)).save(associationCaptor.capture());

            // First city (Lyon) creates the new entity
            Association saved = associationCaptor.getAllValues().get(0);
            assertThat(saved.getLastSyncedAt()).isNotNull();
            assertThat(result.getCreated()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should_update_existing_association_when_slug_exists_and_data_changed")
        void should_update_existing_association_when_slug_exists_and_data_changed() {
            // Given
            HelloAssoOrganization org = buildOrg(
                    "AS Lyon - Nouveau Nom", "as-lyon",
                    "Lyon", "69001", "Sport");

            Association existing = buildExistingAssociation(
                    "Association Sportive de Lyon", "as-lyon",
                    "Lyon", "69001", "SPORT");

            HelloAssoDirectoryResponse response =
                    buildDirectoryResponse(List.of(org), null);

            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.just(response))
                    .thenReturn(Mono.just(
                            buildDirectoryResponse(List.of(), null)));

            when(associationRepository.findByHelloassoSlug("as-lyon"))
                    .thenReturn(Optional.of(existing));
            when(associationRepository.save(any())).thenReturn(existing);

            // When
            SyncResultResponse result = syncService.syncDirectory();

            // Then -- name changed so updateFromHelloAsso should be called
            verify(associationMapper).updateFromHelloAsso(existing, org);
            verify(associationMapper, never()).fromHelloAsso(any());
            assertThat(result.getUpdated()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should_count_unchanged_when_existing_data_matches_incoming")
        void should_count_unchanged_when_existing_data_matches_incoming() {
            // Given -- existing entity has same data as incoming org
            HelloAssoOrganization org = buildOrg(
                    "Club de Danse de Paris", "club-danse-paris",
                    "Paris", "75004", "Danse");

            Association existing = buildExistingAssociation(
                    "Club de Danse de Paris", "club-danse-paris",
                    "Paris", "75004", "DANCE");

            // Match all comparable fields exactly
            existing.setDescription(org.description());
            existing.setLogoUrl(org.logo());
            existing.setWebsite(org.url());

            HelloAssoDirectoryResponse response =
                    buildDirectoryResponse(List.of(org), null);

            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.just(response))
                    .thenReturn(Mono.just(
                            buildDirectoryResponse(List.of(), null)));

            when(associationRepository.findByHelloassoSlug("club-danse-paris"))
                    .thenReturn(Optional.of(existing));
            when(associationMapper.normalizeCategory("Danse"))
                    .thenReturn("DANCE");
            when(associationRepository.save(any())).thenReturn(existing);

            // When
            SyncResultResponse result = syncService.syncDirectory();

            // Then -- no create or update, just unchanged
            verify(associationMapper, never()).fromHelloAsso(any());
            verify(associationMapper, never()).updateFromHelloAsso(any(), any());
            assertThat(result.getUnchanged()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("should_not_create_duplicate_when_same_slug_returned_across_pages")
        void should_not_create_duplicate_when_same_slug_returned_across_pages() {
            // Given -- same org appears on page 1 and page 2
            HelloAssoOrganization org = buildOrg(
                    "Club Multi-Sport", "club-multi-sport",
                    "Lyon", "69002", "Sport");

            Association existingAfterFirstSave = buildExistingAssociation(
                    "Club Multi-Sport", "club-multi-sport",
                    "Lyon", "69002", "SPORT");

            HelloAssoDirectoryResponse page1 =
                    buildDirectoryResponse(List.of(org), "page2token");
            HelloAssoDirectoryResponse page2 =
                    buildDirectoryResponse(List.of(org), null);
            HelloAssoDirectoryResponse emptyResponse =
                    buildDirectoryResponse(List.of(), null);

            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.just(page1))
                    .thenReturn(Mono.just(page2))
                    .thenReturn(Mono.just(emptyResponse))
                    .thenReturn(Mono.just(emptyResponse));

            // First call: not found -> create
            // Second call: now found -> update or unchanged
            when(associationRepository.findByHelloassoSlug("club-multi-sport"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(existingAfterFirstSave));

            Association newEntity = Association.builder()
                    .name("Club Multi-Sport")
                    .slug("club-multi-sport")
                    .helloassoSlug("club-multi-sport")
                    .build();

            when(associationMapper.fromHelloAsso(org)).thenReturn(newEntity);
            when(associationRepository.save(any()))
                    .thenReturn(newEntity)
                    .thenReturn(existingAfterFirstSave);

            // When
            SyncResultResponse result = syncService.syncDirectory();

            // Then -- fromHelloAsso called only once (first encounter)
            verify(associationMapper, times(1)).fromHelloAsso(org);
            assertThat(result.getCreated()).isEqualTo(1);
        }
    }

    // ── Pagination Tests ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Pagination")
    class Pagination {

        @Test
        @DisplayName("should_follow_continuationToken_to_fetch_all_pages")
        void should_follow_continuationToken_to_fetch_all_pages() {
            // Given -- 2 pages for Lyon, 1 page for Paris
            HelloAssoOrganization org1 = buildOrg("Org1", "org-1",
                    "Lyon", "69001", "Sport");
            HelloAssoOrganization org2 = buildOrg("Org2", "org-2",
                    "Lyon", "69002", "Sport");
            HelloAssoOrganization org3 = buildOrg("Org3", "org-3",
                    "Paris", "75001", "Danse");

            HelloAssoDirectoryResponse lyonPage1 =
                    buildDirectoryResponse(List.of(org1), "lyon-page2");
            HelloAssoDirectoryResponse lyonPage2 =
                    buildDirectoryResponse(List.of(org2), null);
            HelloAssoDirectoryResponse parisPage1 =
                    buildDirectoryResponse(List.of(org3), null);

            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.just(lyonPage1))
                    .thenReturn(Mono.just(lyonPage2))
                    .thenReturn(Mono.just(parisPage1));

            when(associationRepository.findByHelloassoSlug(anyString()))
                    .thenReturn(Optional.empty());
            when(associationMapper.fromHelloAsso(any()))
                    .thenReturn(Association.builder().build());
            when(associationRepository.save(any()))
                    .thenReturn(Association.builder().build());

            // When
            SyncResultResponse result = syncService.syncDirectory();

            // Then -- 3 search calls total (2 for Lyon, 1 for Paris)
            verify(helloAssoClient, times(3))
                    .searchOrganizations(any());
            assertThat(result.getTotalProcessed()).isEqualTo(3);
        }

        @Test
        @DisplayName("should_stop_pagination_when_response_data_is_empty")
        void should_stop_pagination_when_response_data_is_empty() {
            // Given
            HelloAssoDirectoryResponse emptyResponse =
                    buildDirectoryResponse(List.of(), null);

            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.just(emptyResponse));

            // When
            SyncResultResponse result = syncService.syncDirectory();

            // Then -- called once per city (2 cities) and stopped immediately
            verify(helloAssoClient, times(2))
                    .searchOrganizations(any());
            assertThat(result.getTotalProcessed()).isEqualTo(0);
        }

        @Test
        @DisplayName("should_stop_pagination_when_response_is_null")
        void should_stop_pagination_when_response_is_null() {
            // Given
            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.empty());

            // When
            SyncResultResponse result = syncService.syncDirectory();

            // Then
            assertThat(result.getTotalProcessed()).isEqualTo(0);
        }
    }

    // ── Sync Result Tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Sync Result")
    class SyncResult {

        @Test
        @DisplayName("should_return_accurate_counts_in_sync_result")
        void should_return_accurate_counts_in_sync_result() {
            // Given -- 2 orgs: one new, one existing-unchanged
            HelloAssoOrganization newOrg = buildOrg("New Org", "new-org",
                    "Lyon", "69001", "Sport");
            HelloAssoOrganization existingOrg = buildOrg("Old Org", "old-org",
                    "Lyon", "69002", "Sport");

            Association existingEntity = buildExistingAssociation(
                    "Old Org", "old-org", "Lyon", "69002", "SPORT");
            existingEntity.setDescription(existingOrg.description());
            existingEntity.setLogoUrl(existingOrg.logo());
            existingEntity.setWebsite(existingOrg.url());

            HelloAssoDirectoryResponse response =
                    buildDirectoryResponse(List.of(newOrg, existingOrg), null);
            HelloAssoDirectoryResponse emptyResponse =
                    buildDirectoryResponse(List.of(), null);

            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.just(response))
                    .thenReturn(Mono.just(emptyResponse));

            when(associationRepository.findByHelloassoSlug("new-org"))
                    .thenReturn(Optional.empty());
            when(associationRepository.findByHelloassoSlug("old-org"))
                    .thenReturn(Optional.of(existingEntity));
            when(associationMapper.fromHelloAsso(newOrg))
                    .thenReturn(Association.builder().build());
            when(associationMapper.normalizeCategory("Sport"))
                    .thenReturn("SPORT");
            when(associationRepository.save(any()))
                    .thenReturn(Association.builder().build());

            // When
            SyncResultResponse result = syncService.syncDirectory();

            // Then
            assertThat(result.getTotalProcessed()).isEqualTo(
                    result.getCreated() + result.getUpdated()
                            + result.getUnchanged());
            assertThat(result.getSyncedAt()).isNotNull();
            assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("should_set_last_synced_at_on_every_saved_association")
        void should_set_last_synced_at_on_every_saved_association() {
            // Given
            HelloAssoOrganization org = buildOrg("Club Lyon", "club-lyon",
                    "Lyon", "69001", "Sport");

            HelloAssoDirectoryResponse response =
                    buildDirectoryResponse(List.of(org), null);
            HelloAssoDirectoryResponse emptyResponse =
                    buildDirectoryResponse(List.of(), null);

            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.just(response))
                    .thenReturn(Mono.just(emptyResponse));

            when(associationRepository.findByHelloassoSlug("club-lyon"))
                    .thenReturn(Optional.empty());

            Association newEntity = Association.builder()
                    .slug("club-lyon")
                    .helloassoSlug("club-lyon")
                    .build();
            when(associationMapper.fromHelloAsso(org)).thenReturn(newEntity);
            when(associationRepository.save(any())).thenReturn(newEntity);

            Instant beforeSync = Instant.now();

            // When
            syncService.syncDirectory();

            // Then
            verify(associationRepository, times(2)).save(associationCaptor.capture());
            for (Association saved : associationCaptor.getAllValues()) {
                assertThat(saved.getLastSyncedAt()).isNotNull();
                assertThat(saved.getLastSyncedAt()).isAfterOrEqualTo(beforeSync);
            }
        }
    }

    // ── Kafka Event Tests ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Kafka Event")
    class KafkaEvent {

        @Test
        @DisplayName("should_publish_sync_completed_event_to_correct_topic")
        void should_publish_sync_completed_event_to_correct_topic() {
            // Given
            HelloAssoDirectoryResponse emptyResponse =
                    buildDirectoryResponse(List.of(), null);

            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.just(emptyResponse));

            // When
            syncService.syncDirectory();

            // Then
            verify(kafkaTemplate).send(
                    eq("family-hobbies.association.sync-completed"),
                    eq("sync"),
                    any(HelloAssoSyncCompletedEvent.class));
        }

        @Test
        @DisplayName("should_include_accurate_counts_in_kafka_event")
        void should_include_accurate_counts_in_kafka_event() {
            // Given
            HelloAssoOrganization org = buildOrg("Club Test", "club-test",
                    "Lyon", "69001", "Sport");

            HelloAssoDirectoryResponse response =
                    buildDirectoryResponse(List.of(org), null);
            HelloAssoDirectoryResponse emptyResponse =
                    buildDirectoryResponse(List.of(), null);

            when(helloAssoClient.searchOrganizations(any()))
                    .thenReturn(Mono.just(response))
                    .thenReturn(Mono.just(emptyResponse));

            when(associationRepository.findByHelloassoSlug("club-test"))
                    .thenReturn(Optional.empty());
            when(associationMapper.fromHelloAsso(org))
                    .thenReturn(Association.builder().build());
            when(associationRepository.save(any()))
                    .thenReturn(Association.builder().build());

            // When
            SyncResultResponse result = syncService.syncDirectory();

            // Then
            verify(kafkaTemplate).send(
                    eq("family-hobbies.association.sync-completed"),
                    eq("sync"),
                    eventCaptor.capture());

            HelloAssoSyncCompletedEvent event = eventCaptor.getValue();
            assertThat(event.getCreated()).isEqualTo(result.getCreated());
            assertThat(event.getUpdated()).isEqualTo(result.getUpdated());
            assertThat(event.getUnchanged()).isEqualTo(result.getUnchanged());
            assertThat(event.getTotalProcessed())
                    .isEqualTo(result.getTotalProcessed());
            assertThat(event.getSyncedAt()).isNotNull();
            assertThat(event.getDurationMs()).isGreaterThanOrEqualTo(0);
        }
    }

    // ── Staleness Check Tests ─────────────────────────────────────────────

    @Nested
    @DisplayName("Staleness Check")
    class StalenessCheck {

        @Test
        @DisplayName("should_return_true_when_last_synced_at_is_older_than_24_hours")
        void should_return_true_when_last_synced_at_is_older_than_24_hours() {
            // Given
            Association association = Association.builder()
                    .helloassoSlug("old-slug")
                    .lastSyncedAt(Instant.now().minus(25, ChronoUnit.HOURS))
                    .build();

            // When
            boolean stale = syncService.isStale(association);

            // Then
            assertThat(stale).isTrue();
        }

        @Test
        @DisplayName("should_return_false_when_last_synced_at_is_within_24_hours")
        void should_return_false_when_last_synced_at_is_within_24_hours() {
            // Given
            Association association = Association.builder()
                    .helloassoSlug("fresh-slug")
                    .lastSyncedAt(Instant.now().minus(12, ChronoUnit.HOURS))
                    .build();

            // When
            boolean stale = syncService.isStale(association);

            // Then
            assertThat(stale).isFalse();
        }

        @Test
        @DisplayName("should_return_true_when_last_synced_at_is_null")
        void should_return_true_when_last_synced_at_is_null() {
            // Given
            Association association = Association.builder()
                    .helloassoSlug("never-synced-slug")
                    .lastSyncedAt(null)
                    .build();

            // When
            boolean stale = syncService.isStale(association);

            // Then
            assertThat(stale).isTrue();
        }

        @Test
        @DisplayName("should_return_false_when_association_has_no_helloasso_slug")
        void should_return_false_when_association_has_no_helloasso_slug() {
            // Given -- locally-created association, not from HelloAsso
            Association association = Association.builder()
                    .helloassoSlug(null)
                    .lastSyncedAt(null)
                    .build();

            // When
            boolean stale = syncService.isStale(association);

            // Then -- non-HelloAsso associations are never "stale"
            assertThat(stale).isFalse();
        }
    }

    // ── Single Organization Sync Tests ────────────────────────────────────

    @Nested
    @DisplayName("Single Organization Sync")
    class SingleOrgSync {

        @Test
        @DisplayName("should_sync_single_organization_by_slug")
        void should_sync_single_organization_by_slug() {
            // Given
            HelloAssoOrganization org = buildOrg(
                    "Conservatoire de Toulouse",
                    "conservatoire-toulouse",
                    "Toulouse", "31000", "Musique");

            Association newEntity = Association.builder()
                    .name("Conservatoire de Toulouse")
                    .slug("conservatoire-toulouse")
                    .helloassoSlug("conservatoire-toulouse")
                    .city("Toulouse")
                    .postalCode("31000")
                    .category("MUSIC")
                    .status("ACTIVE")
                    .build();

            when(helloAssoClient.getOrganization("conservatoire-toulouse"))
                    .thenReturn(Mono.just(org));
            when(associationRepository
                    .findByHelloassoSlug("conservatoire-toulouse"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(newEntity));
            when(associationMapper.fromHelloAsso(org)).thenReturn(newEntity);
            when(associationRepository.save(any())).thenReturn(newEntity);

            // When
            Association result = syncService
                    .syncOrganization("conservatoire-toulouse");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getName())
                    .isEqualTo("Conservatoire de Toulouse");
            assertThat(result.getHelloassoSlug())
                    .isEqualTo("conservatoire-toulouse");
            verify(helloAssoClient).getOrganization("conservatoire-toulouse");
            verify(associationRepository).save(any());
        }

        @Test
        @DisplayName("should_return_null_when_organization_not_found_on_helloasso")
        void should_return_null_when_organization_not_found_on_helloasso() {
            // Given
            when(helloAssoClient.getOrganization("nonexistent-slug"))
                    .thenReturn(Mono.empty());

            // When
            Association result = syncService
                    .syncOrganization("nonexistent-slug");

            // Then
            assertThat(result).isNull();
            verify(associationRepository, never()).save(any());
        }
    }
}
```
