# Story S5-003: Implement AssociationSyncService

> 5 points | Priority: P0 | Service: association-service
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The `AssociationSyncService` bridges the HelloAsso directory data and our local `t_association` table. It consumes `HelloAssoClient` (S5-002) to fetch organization data from the HelloAsso API and upserts it into the local PostgreSQL database, ensuring the application always has a reasonably fresh local cache of association data. The sync is triggered manually via an admin-only endpoint (`POST /api/v1/admin/associations/sync`) requiring the `ADMIN` role. The service handles pagination using HelloAsso's `continuationToken` mechanism, performs deduplication by matching on `helloasso_slug`, and tracks freshness via the `last_synced_at` column. On completion, it publishes a `HelloAssoSyncCompletedEvent` to Kafka for observability and potential downstream consumers. The admin endpoint returns a `SyncResultResponse` with counts of created, updated, and unchanged associations. The sync can be configured to target specific French cities via `helloasso.sync.cities` in `application.yml`. An `AssociationMapper` handles the conversion between HelloAsso DTOs and the `Association` JPA entity.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Association JPA entity | `backend/association-service/src/main/java/com/familyhobbies/associationservice/entity/Association.java` | JPA entity mapping `t_association` table | Entity compiles and maps all columns |
| 2 | AssociationRepository | `backend/association-service/src/main/java/com/familyhobbies/associationservice/repository/AssociationRepository.java` | Spring Data JPA repository with `findByHelloassoSlug` | Query method resolves correctly |
| 3 | AssociationMapper | `backend/association-service/src/main/java/com/familyhobbies/associationservice/mapper/AssociationMapper.java` | Mapper: HelloAssoOrganization -> Association entity (create + update) | Mapping produces correct field values |
| 4 | SyncResultResponse DTO | `backend/association-service/src/main/java/com/familyhobbies/associationservice/dto/SyncResultResponse.java` | Response DTO with created/updated/unchanged counts | Serializes to expected JSON |
| 5 | HelloAssoSyncCompletedEvent | `backend/common/src/main/java/com/familyhobbies/common/event/HelloAssoSyncCompletedEvent.java` | Kafka event for sync completion | Event serializes/deserializes correctly |
| 6 | AssociationSyncService interface | `backend/association-service/src/main/java/com/familyhobbies/associationservice/service/AssociationSyncService.java` | Service interface | Compiles |
| 7 | AssociationSyncServiceImpl | `backend/association-service/src/main/java/com/familyhobbies/associationservice/service/impl/AssociationSyncServiceImpl.java` | Full sync implementation with pagination, upsert, Kafka publish | Sync creates/updates associations, publishes event |
| 8 | AdminSyncController | `backend/association-service/src/main/java/com/familyhobbies/associationservice/controller/AdminSyncController.java` | REST endpoint `POST /api/v1/admin/associations/sync` with ADMIN role | Returns 200 with sync result; 403 for non-admin |
| 9 | Failing tests (TDD contract) | `backend/association-service/src/test/java/com/familyhobbies/associationservice/service/AssociationSyncServiceImplTest.java` | JUnit 5 tests for upsert, pagination, counts, Kafka | Tests compile and verify sync contract |

---

## Task 1 Detail: Association JPA Entity

- **What**: JPA `@Entity` class mapping the `t_association` table in `familyhobbies_associations`. Maps all 19 columns from the data model doc including `helloasso_slug`, `helloasso_org_id`, `last_synced_at`, and audit timestamps.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/entity/Association.java`
- **Why**: The sync service reads and writes this entity. The repository queries it by `helloasso_slug` for deduplication. The mapper populates it from HelloAsso DTOs.
- **Content**:

```java
package com.familyhobbies.associationservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** JPA entity mapping {@code t_association} table. Columns align with
 * {@code docs/architecture/02-data-model.md} section 5.1. */
@Entity
@Table(name = "t_association")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Association {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 255, unique = true)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String address;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(length = 5)
    private String department;

    @Column(length = 100)
    private String region;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String website;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "helloasso_slug", length = 255)
    private String helloassoSlug;

    @Column(name = "helloasso_org_id", length = 100)
    private String helloassoOrgId;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles without error

---

## Task 2 Detail: AssociationRepository

- **What**: Spring Data JPA repository interface for the `Association` entity. Provides `findByHelloassoSlug()` used by the sync service for deduplication, plus search methods for the frontend.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/repository/AssociationRepository.java`
- **Why**: The sync service calls `findByHelloassoSlug()` on every incoming HelloAsso organization to decide whether to create or update. The admin endpoint and service layer also use search methods.
- **Content**:

```java
package com.familyhobbies.associationservice.repository;

import com.familyhobbies.associationservice.entity.Association;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Spring Data JPA repository for {@link Association}. */
@Repository
public interface AssociationRepository extends JpaRepository<Association, Long> {

    /** Find by HelloAsso slug (upsert deduplication). */
    Optional<Association> findByHelloassoSlug(String helloassoSlug);

    /** Find by application slug (URL-friendly identifier). */
    Optional<Association> findBySlug(String slug);

    /** Search by city and/or category with pagination (both nullable). */
    @Query("""
            SELECT a FROM Association a
            WHERE (:city IS NULL OR LOWER(a.city) = LOWER(:city))
              AND (:category IS NULL OR a.category = :category)
              AND a.status = 'ACTIVE'
            ORDER BY a.name ASC
            """)
    Page<Association> searchByCityAndCategory(
            @Param("city") String city,
            @Param("category") String category,
            Pageable pageable);

    /** Count HelloAsso-synced associations (reporting). */
    long countByHelloassoSlugIsNotNull();
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles

---

## Task 3 Detail: AssociationMapper

- **What**: Spring `@Component` mapper that converts `HelloAssoOrganization` DTOs into `Association` entities. Provides two methods: `fromHelloAsso()` for creating a new entity, and `updateFromHelloAsso()` for updating an existing entity in place. Handles field name mapping and category normalization.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/mapper/AssociationMapper.java`
- **Why**: Isolates the mapping logic from the sync service. Called by `AssociationSyncServiceImpl.upsertAssociation()` for every organization fetched from HelloAsso.
- **Content**:

```java
package com.familyhobbies.associationservice.mapper;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.entity.Association;
import org.springframework.stereotype.Component;

/** Maps HelloAsso API DTOs to {@link Association} JPA entities.
 * Normalizes categories to uppercase (e.g., "Sport" -> "SPORT").
 * Reuses HelloAsso slugs as application slugs. */
@Component
public class AssociationMapper {

    /**
     * Creates a new {@link Association} entity from a HelloAsso organization.
     * Used when no existing association matches the HelloAsso slug.
     *
     * @param org the HelloAsso organization data
     * @return a new Association entity (not yet persisted)
     */
    public Association fromHelloAsso(HelloAssoOrganization org) {
        return Association.builder()
                .name(org.name())
                .slug(org.slug())
                .helloassoSlug(org.slug())
                .description(org.description())
                .city(org.city() != null ? org.city() : "Inconnue")
                .postalCode(org.postalCode() != null ? org.postalCode() : "00000")
                .logoUrl(org.logo())
                .website(org.url())
                .category(normalizeCategory(org.category()))
                .status("ACTIVE")
                .build();
    }

    /**
     * Updates an existing {@link Association} entity with fresh data from
     * HelloAsso. Does NOT change the entity's {@code id}, {@code slug},
     * {@code createdAt}, or any locally-managed fields.
     *
     * @param existing the existing entity to update
     * @param org      the fresh HelloAsso organization data
     */
    public void updateFromHelloAsso(Association existing,
                                     HelloAssoOrganization org) {
        existing.setName(org.name());
        existing.setDescription(org.description());
        if (org.city() != null) {
            existing.setCity(org.city());
        }
        if (org.postalCode() != null) {
            existing.setPostalCode(org.postalCode());
        }
        existing.setLogoUrl(org.logo());
        existing.setWebsite(org.url());
        existing.setCategory(normalizeCategory(org.category()));
    }

    /**
     * Normalizes a HelloAsso category string to our uppercase convention.
     *
     * <p>Examples:
     * <ul>
     *   <li>"Sport" -> "SPORT"</li>
     *   <li>"Danse" -> "DANSE"</li>
     *   <li>"Arts martiaux" -> "MARTIAL_ARTS"</li>
     *   <li>null -> "OTHER"</li>
     * </ul>
     */
    String normalizeCategory(String helloAssoCategory) {
        if (helloAssoCategory == null || helloAssoCategory.isBlank()) {
            return "OTHER";
        }

        String lower = helloAssoCategory.toLowerCase().trim();

        return switch (lower) {
            case "sport" -> "SPORT";
            case "danse" -> "DANCE";
            case "musique" -> "MUSIC";
            case "theatre", "th\u00e9\u00e2tre" -> "THEATER";
            case "art", "arts plastiques" -> "ART";
            case "arts martiaux" -> "MARTIAL_ARTS";
            case "bien-etre", "bien-\u00eatre", "wellness" -> "WELLNESS";
            default -> "OTHER";
        };
    }
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles; unit test `normalizeCategory("Sport")` returns `"SPORT"`

---

## Task 4 Detail: SyncResultResponse DTO

- **What**: Response DTO returned by the admin sync endpoint containing the counts of created, updated, and unchanged associations, plus metadata about the sync operation.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/dto/SyncResultResponse.java`
- **Why**: Returned by `POST /api/v1/admin/associations/sync` so the admin can see what happened during the sync. Also used as the payload for the Kafka event.
- **Content**:

```java
package com.familyhobbies.associationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Response DTO for the admin association sync endpoint.
 *
 * <p>Contains counts of how many associations were created, updated,
 * or left unchanged during the sync operation, plus metadata like
 * duration and timestamp.
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "created": 12,
 *   "updated": 45,
 *   "unchanged": 85,
 *   "totalProcessed": 142,
 *   "syncedAt": "2026-02-24T14:30:00Z",
 *   "durationMs": 4523
 * }
 * </pre>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncResultResponse {

    /** Number of new associations created during this sync. */
    private int created;

    /** Number of existing associations updated with fresh HelloAsso data. */
    private int updated;

    /** Number of associations that matched but had no data changes. */
    private int unchanged;

    /** Total organizations processed from HelloAsso API (created + updated + unchanged). */
    private int totalProcessed;

    /** Timestamp when the sync completed. */
    private Instant syncedAt;

    /** Duration of the sync operation in milliseconds. */
    private long durationMs;
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles; Jackson serializes all fields

---

## Task 5 Detail: HelloAssoSyncCompletedEvent

- **What**: Kafka event published when an association sync operation completes. Contains the sync summary (created, updated, unchanged counts) for observability and potential downstream processing by notification-service.
- **Where**: `backend/common/src/main/java/com/familyhobbies/common/event/HelloAssoSyncCompletedEvent.java`
- **Why**: Published by `AssociationSyncServiceImpl` after sync completion. Lives in `common` module because Kafka events are shared contracts. Follows the same pattern as `UserRegisteredEvent` in the codebase.
- **Content**:

```java
package com.familyhobbies.common.event;

import java.time.Instant;

/**
 * Published by association-service when a HelloAsso directory sync completes.
 *
 * <p>Consumed by notification-service for admin alerting and observability.
 *
 * <p>Topic: {@code family-hobbies.association.sync-completed}
 *
 * <p>Follows the same POJO pattern as {@link UserRegisteredEvent}:
 * default no-arg constructor for Jackson deserialization, explicit
 * getters/setters, and a full-arg constructor.
 */
public class HelloAssoSyncCompletedEvent {

    /** Number of new associations created during this sync. */
    private int created;

    /** Number of existing associations updated. */
    private int updated;

    /** Number of associations unchanged. */
    private int unchanged;

    /** Total organizations processed. */
    private int totalProcessed;

    /** Timestamp when the sync completed. */
    private Instant syncedAt;

    /** Duration of the sync in milliseconds. */
    private long durationMs;

    /** Which user (admin) triggered the sync. Null for scheduled syncs. */
    private Long triggeredByUserId;

    public HelloAssoSyncCompletedEvent() {
        // Default constructor required by Jackson deserialization
    }

    public HelloAssoSyncCompletedEvent(int created, int updated, int unchanged,
                                        int totalProcessed, Instant syncedAt,
                                        long durationMs, Long triggeredByUserId) {
        this.created = created;
        this.updated = updated;
        this.unchanged = unchanged;
        this.totalProcessed = totalProcessed;
        this.syncedAt = syncedAt;
        this.durationMs = durationMs;
        this.triggeredByUserId = triggeredByUserId;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getUnchanged() {
        return unchanged;
    }

    public void setUnchanged(int unchanged) {
        this.unchanged = unchanged;
    }

    public int getTotalProcessed() {
        return totalProcessed;
    }

    public void setTotalProcessed(int totalProcessed) {
        this.totalProcessed = totalProcessed;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(Instant syncedAt) {
        this.syncedAt = syncedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }

    public Long getTriggeredByUserId() {
        return triggeredByUserId;
    }

    public void setTriggeredByUserId(Long triggeredByUserId) {
        this.triggeredByUserId = triggeredByUserId;
    }

    @Override
    public String toString() {
        return "HelloAssoSyncCompletedEvent{" +
                "created=" + created +
                ", updated=" + updated +
                ", unchanged=" + unchanged +
                ", totalProcessed=" + totalProcessed +
                ", syncedAt=" + syncedAt +
                ", durationMs=" + durationMs +
                ", triggeredByUserId=" + triggeredByUserId +
                '}';
    }
}
```

- **Verify**: `mvn compile -pl backend/common` -> compiles; Jackson round-trip serialization works

---

## Task 6 Detail: AssociationSyncService Interface

- **What**: Service interface defining the sync operations contract. Implemented by `AssociationSyncServiceImpl`.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/service/AssociationSyncService.java`
- **Why**: Follows the interface/impl pattern used across the project. Allows mocking in controller tests and future alternative implementations (e.g., scheduled sync).
- **Content**:

```java
package com.familyhobbies.associationservice.service;

import com.familyhobbies.associationservice.dto.SyncResultResponse;
import com.familyhobbies.associationservice.entity.Association;

/**
 * Service interface for synchronizing association data from HelloAsso API
 * into the local database.
 *
 * <p>Operations:
 * <ul>
 *   <li>{@link #syncDirectory} -- full directory sync with configurable city filters</li>
 *   <li>{@link #syncOrganization} -- single organization sync by slug</li>
 *   <li>{@link #isStale} -- check if an association needs refreshing</li>
 * </ul>
 */
public interface AssociationSyncService {

    /**
     * Performs a full directory sync from HelloAsso API.
     * Fetches organizations for all configured cities, handles pagination,
     * upserts into the local database, and publishes a Kafka event on completion.
     *
     * @return sync result with created/updated/unchanged counts
     */
    SyncResultResponse syncDirectory();

    /**
     * Syncs a single organization by its HelloAsso slug.
     * Called on-demand when a stale association is requested.
     *
     * @param helloassoSlug the HelloAsso organization slug
     * @return the created or updated association, or null if not found on HelloAsso
     */
    Association syncOrganization(String helloassoSlug);

    /**
     * Checks whether an association's cached data is stale
     * (older than the configured threshold, default 24 hours).
     *
     * @param association the association to check
     * @return true if the data should be refreshed from HelloAsso
     */
    boolean isStale(Association association);
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles

---

## Task 7 Detail: AssociationSyncServiceImpl

- **What**: Full implementation of `AssociationSyncService`. Calls `HelloAssoClient.searchOrganizations()` for each configured city, handles pagination via `continuationToken`, upserts each organization into `t_association` (create if new, update if exists by `helloasso_slug`), tracks created/updated/unchanged counts, sets `last_synced_at` on every synced record, and publishes a `HelloAssoSyncCompletedEvent` to Kafka on completion.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/service/impl/AssociationSyncServiceImpl.java`
- **Why**: Core business logic for keeping local association data in sync with HelloAsso. Consumed by `AdminSyncController` (Task 8) and will be consumed by the nightly batch job in production.
- **Content**:

```java
package com.familyhobbies.associationservice.service.impl;

import com.familyhobbies.associationservice.adapter.HelloAssoClient;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.config.HelloAssoProperties;
import com.familyhobbies.associationservice.dto.SyncResultResponse;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.mapper.AssociationMapper;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.associationservice.service.AssociationSyncService;
import com.familyhobbies.common.event.HelloAssoSyncCompletedEvent;
import com.familyhobbies.errorhandling.exception.container.KafkaPublishException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Implementation of {@link AssociationSyncService}.
 *
 * <p>Sync strategy:
 * <ol>
 *   <li>For each configured city in {@code helloasso.sync.cities}:</li>
 *   <li>Call {@code POST /directory/organizations} with city filter</li>
 *   <li>Page through results using {@code continuationToken}</li>
 *   <li>For each organization: upsert into {@code t_association}
 *       (match on {@code helloasso_slug})</li>
 *   <li>Track created/updated/unchanged counts</li>
 *   <li>Set {@code last_synced_at = now()} on every synced record</li>
 *   <li>Publish {@link HelloAssoSyncCompletedEvent} to Kafka</li>
 * </ol>
 *
 * <p>Deduplication: an organization is considered "existing" if a record with
 * the same {@code helloasso_slug} already exists in the database. If it exists,
 * the mapper updates the mutable fields. If not, a new entity is created.
 */
@Service
public class AssociationSyncServiceImpl implements AssociationSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(AssociationSyncServiceImpl.class);

    private static final String SYNC_TOPIC =
            "family-hobbies.association.sync-completed";
    private static final int STALE_THRESHOLD_HOURS = 24;

    private final HelloAssoClient helloAssoClient;
    private final AssociationRepository associationRepository;
    private final AssociationMapper associationMapper;
    private final HelloAssoProperties properties;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AssociationSyncServiceImpl(
            HelloAssoClient helloAssoClient,
            AssociationRepository associationRepository,
            AssociationMapper associationMapper,
            HelloAssoProperties properties,
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.helloAssoClient = helloAssoClient;
        this.associationRepository = associationRepository;
        this.associationMapper = associationMapper;
        this.properties = properties;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Iterates over all configured cities, fetches all pages from
     * HelloAsso for each city, and upserts every organization found.
     */
    @Override
    @Transactional
    public SyncResultResponse syncDirectory() {
        long startTime = System.currentTimeMillis();
        log.info("Starting HelloAsso directory sync for cities: {}",
                properties.getSync().getCities());

        int created = 0;
        int updated = 0;
        int unchanged = 0;

        for (String city : properties.getSync().getCities()) {
            log.debug("Syncing associations for city: {}", city);
            String continuationToken = null;

            do {
                HelloAssoDirectoryRequest request =
                        HelloAssoDirectoryRequest.builder()
                                .city(city)
                                .pageSize(properties.getSync().getPageSize())
                                .continuationToken(continuationToken)
                                .build();

                HelloAssoDirectoryResponse response = helloAssoClient
                        .searchOrganizations(request)
                        .block();

                if (response == null
                        || response.data() == null
                        || response.data().isEmpty()) {
                    log.debug("No more results for city: {}", city);
                    break;
                }

                for (HelloAssoOrganization org : response.data()) {
                    UpsertResult result = upsertAssociation(org);
                    switch (result) {
                        case CREATED -> created++;
                        case UPDATED -> updated++;
                        case UNCHANGED -> unchanged++;
                    }
                }

                continuationToken = response.pagination() != null
                        ? response.pagination().continuationToken()
                        : null;

            } while (continuationToken != null);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        int totalProcessed = created + updated + unchanged;
        Instant syncedAt = Instant.now();

        log.info("HelloAsso sync completed in {}ms: {} created, {} updated, "
                        + "{} unchanged, {} total",
                durationMs, created, updated, unchanged, totalProcessed);

        SyncResultResponse result = SyncResultResponse.builder()
                .created(created)
                .updated(updated)
                .unchanged(unchanged)
                .totalProcessed(totalProcessed)
                .syncedAt(syncedAt)
                .durationMs(durationMs)
                .build();

        publishSyncCompletedEvent(result);

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Association syncOrganization(String helloassoSlug) {
        log.debug("Syncing single organization: slug={}", helloassoSlug);

        HelloAssoOrganization org = helloAssoClient
                .getOrganization(helloassoSlug)
                .block();

        if (org == null) {
            log.warn("Organization not found on HelloAsso: slug={}",
                    helloassoSlug);
            return null;
        }

        UpsertResult result = upsertAssociation(org);
        log.debug("Single org sync result: slug={}, result={}",
                helloassoSlug, result);

        return associationRepository.findByHelloassoSlug(helloassoSlug)
                .orElse(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isStale(Association association) {
        if (association.getHelloassoSlug() == null) {
            return false;
        }
        if (association.getLastSyncedAt() == null) {
            return true;
        }
        return association.getLastSyncedAt()
                .isBefore(Instant.now().minus(STALE_THRESHOLD_HOURS,
                        ChronoUnit.HOURS));
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /**
     * Upserts a single HelloAsso organization into the local database.
     *
     * <p>Match strategy: find by {@code helloasso_slug}. If found, compare
     * fields and update if changed. If not found, create a new record.
     *
     * @return the upsert outcome (CREATED, UPDATED, or UNCHANGED)
     */
    private UpsertResult upsertAssociation(HelloAssoOrganization org) {
        Optional<Association> existing = associationRepository
                .findByHelloassoSlug(org.slug());

        Association association;
        UpsertResult result;

        if (existing.isPresent()) {
            association = existing.get();
            boolean changed = hasChanges(association, org);
            if (changed) {
                associationMapper.updateFromHelloAsso(association, org);
                result = UpsertResult.UPDATED;
            } else {
                result = UpsertResult.UNCHANGED;
            }
        } else {
            association = associationMapper.fromHelloAsso(org);
            result = UpsertResult.CREATED;
        }

        association.setLastSyncedAt(Instant.now());
        associationRepository.save(association);

        return result;
    }

    /**
     * Compares the current entity fields with the incoming HelloAsso data
     * to determine if an update is needed.
     */
    private boolean hasChanges(Association existing,
                                HelloAssoOrganization incoming) {
        if (!nullSafeEquals(existing.getName(), incoming.name())) return true;
        if (!nullSafeEquals(existing.getDescription(),
                incoming.description())) return true;
        if (!nullSafeEquals(existing.getCity(), incoming.city())) return true;
        if (!nullSafeEquals(existing.getPostalCode(),
                incoming.postalCode())) return true;
        if (!nullSafeEquals(existing.getLogoUrl(), incoming.logo())) return true;
        if (!nullSafeEquals(existing.getWebsite(), incoming.url())) return true;
        String normalizedCategory =
                associationMapper.normalizeCategory(incoming.category());
        return !nullSafeEquals(existing.getCategory(), normalizedCategory);
    }

    private boolean nullSafeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Publishes a {@link HelloAssoSyncCompletedEvent} to Kafka.
     * On failure, logs the error but does NOT throw -- the sync itself
     * succeeded and the data is persisted. Kafka failure is non-fatal here.
     */
    private void publishSyncCompletedEvent(SyncResultResponse result) {
        try {
            HelloAssoSyncCompletedEvent event =
                    new HelloAssoSyncCompletedEvent(
                            result.getCreated(),
                            result.getUpdated(),
                            result.getUnchanged(),
                            result.getTotalProcessed(),
                            result.getSyncedAt(),
                            result.getDurationMs(),
                            null // triggeredByUserId set by controller
                    );

            kafkaTemplate.send(SYNC_TOPIC, "sync", event);
            log.info("Published HelloAssoSyncCompletedEvent to topic: {}",
                    SYNC_TOPIC);
        } catch (Exception ex) {
            log.error("Failed to publish sync completed event to Kafka. "
                    + "Sync data is persisted but event was not published.",
                    ex);
        }
    }

    /**
     * Internal enum representing the outcome of an upsert operation.
     */
    private enum UpsertResult {
        CREATED,
        UPDATED,
        UNCHANGED
    }
}
```

- **Verify**: `mvn compile -pl backend/association-service` -> compiles; run `AssociationSyncServiceImplTest` -> all tests pass

---

## Task 8 Detail: AdminSyncController

- **What**: REST controller exposing `POST /api/v1/admin/associations/sync` with `@PreAuthorize("hasRole('ADMIN')")`. Triggers a full directory sync via `AssociationSyncService` and returns the `SyncResultResponse`.
- **Where**: `backend/association-service/src/main/java/com/familyhobbies/associationservice/controller/AdminSyncController.java`
- **Why**: Provides the admin-triggered sync entry point. The `ADMIN` role restriction ensures only platform administrators can trigger a potentially expensive full sync operation.
- **Content**:

```java
package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.dto.SyncResultResponse;
import com.familyhobbies.associationservice.service.AssociationSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only controller for managing HelloAsso association synchronization.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/admin/associations/sync} -- triggers a full
 *       directory sync from HelloAsso API into the local database</li>
 * </ul>
 *
 * <p>Authorization: requires the {@code ADMIN} role. Non-admin requests
 * receive a 403 Forbidden response via Spring Security.
 *
 * <p>This endpoint is intentionally admin-only because a full directory sync
 * makes multiple paginated API calls to HelloAsso and may take several seconds.
 */
@RestController
@RequestMapping("/api/v1/admin/associations")
public class AdminSyncController {

    private static final Logger log =
            LoggerFactory.getLogger(AdminSyncController.class);

    private final AssociationSyncService syncService;

    public AdminSyncController(AssociationSyncService syncService) {
        this.syncService = syncService;
    }

    /**
     * Triggers a full HelloAsso directory sync.
     *
     * <p>Fetches all associations from HelloAsso for the configured cities,
     * upserts them into the local database, and returns a summary with
     * created/updated/unchanged counts.
     *
     * <p><b>Authorization:</b> ADMIN role required.
     *
     * <p><b>Response:</b>
     * <ul>
     *   <li>200 OK -- sync completed successfully, body contains
     *       {@link SyncResultResponse}</li>
     *   <li>403 Forbidden -- caller does not have ADMIN role</li>
     *   <li>502 Bad Gateway -- HelloAsso API failure during sync</li>
     * </ul>
     *
     * @return sync result with created/updated/unchanged counts
     */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SyncResultResponse> triggerSync() {
        log.info("Admin triggered HelloAsso directory sync");

        SyncResultResponse result = syncService.syncDirectory();

        log.info("Sync completed: {} created, {} updated, {} unchanged",
                result.getCreated(), result.getUpdated(),
                result.getUnchanged());

        return ResponseEntity.ok(result);
    }
}
```

- **Verify**: `curl -X POST http://localhost:8082/api/v1/admin/associations/sync -H "Authorization: Bearer {admin-jwt}"` -> 200 with sync counts; without ADMIN role -> 403

---

## Failing Tests (TDD Contract)

> **File split**: The full test source code (17 tests, ~650 lines) is in the companion file
> **[S5-003-association-sync-service-tests.md](./S5-003-association-sync-service-tests.md)** to stay
> under the 1000-line file limit.

**Test file**: `backend/association-service/src/test/java/com/familyhobbies/associationservice/service/AssociationSyncServiceImplTest.java`

**Test categories (17 tests total)**:

| Category | Tests | What They Verify |
|----------|-------|------------------|
| Upsert Logic | 4 | Create new, update existing, count unchanged, no duplicates |
| Pagination | 3 | Follow continuationToken, stop on empty, stop on null |
| Sync Result | 2 | Accurate counts (created/updated/unchanged), last_synced_at set |
| Kafka Event | 2 | Event published to correct topic, event contains counts |
| Staleness Check | 4 | Stale after 24h, fresh within 24h, null last_synced_at, non-HelloAsso |
| Single Org Sync | 2 | Sync by slug, return null when not found |

### Required Test Dependencies

```xml
<!-- Standard Spring Boot test starter + Mockito (already in most projects) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<!-- Kafka test support -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Acceptance Criteria Checklist

- [ ] Manual sync via `POST /api/v1/admin/associations/sync` creates/updates associations from HelloAsso
- [ ] Admin-only endpoint -- 403 returned for non-ADMIN callers
- [ ] No duplicates created -- deduplication by `helloasso_slug`
- [ ] Pagination handled via `continuationToken` -- all pages processed
- [ ] `last_synced_at` updated on every synced record
- [ ] Sync result returned with accurate created/updated/unchanged counts
- [ ] `HelloAssoSyncCompletedEvent` published to Kafka topic `family-hobbies.association.sync-completed`
- [ ] Category normalization: HelloAsso "Sport" -> "SPORT", "Danse" -> "DANCE", etc.
- [ ] Staleness check: returns true when `last_synced_at` older than 24 hours
- [ ] Single organization sync works via `syncOrganization(slug)`
- [ ] Configured cities come from `helloasso.sync.cities` in application.yml
- [ ] All 17 JUnit 5 tests pass green
