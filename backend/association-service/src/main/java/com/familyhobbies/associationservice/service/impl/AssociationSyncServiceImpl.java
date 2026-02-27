package com.familyhobbies.associationservice.service.impl;

import com.familyhobbies.associationservice.adapter.HelloAssoClient;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryRequest;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoDirectoryResponse;
import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.dto.response.SyncResultResponse;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.mapper.AssociationMapper;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.associationservice.service.AssociationSyncService;
import com.familyhobbies.common.config.HelloAssoProperties;
import com.familyhobbies.common.event.HelloAssoSyncCompletedEvent;
import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of {@link AssociationSyncService}.
 * Synchronizes HelloAsso directory data into the local association database.
 * <p>
 * Iterates configured cities, paginates the HelloAsso directory API,
 * and upserts associations. Publishes a Kafka event on completion.
 */
@Service
public class AssociationSyncServiceImpl implements AssociationSyncService {

    private static final Logger log = LoggerFactory.getLogger(AssociationSyncServiceImpl.class);
    private static final long STALE_THRESHOLD_HOURS = 24;
    private static final String SYNC_TOPIC = "helloasso-sync-completed";

    private final HelloAssoClient helloAssoClient;
    private final AssociationRepository associationRepository;
    private final AssociationMapper associationMapper;
    private final HelloAssoProperties properties;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AssociationSyncServiceImpl(HelloAssoClient helloAssoClient,
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

    @Override
    @Transactional
    public SyncResultResponse syncDirectory() {
        log.info("Starting HelloAsso directory sync for cities: {}", properties.getSync().getCities());
        long startTime = System.currentTimeMillis();

        AtomicInteger created = new AtomicInteger(0);
        AtomicInteger updated = new AtomicInteger(0);
        AtomicInteger unchanged = new AtomicInteger(0);

        for (String city : properties.getSync().getCities()) {
            syncCity(city, created, updated, unchanged);
        }

        long durationMs = System.currentTimeMillis() - startTime;
        int totalProcessed = created.get() + updated.get() + unchanged.get();
        Instant syncedAt = Instant.now();

        SyncResultResponse result = new SyncResultResponse(
            created.get(), updated.get(), unchanged.get(),
            totalProcessed, syncedAt, durationMs
        );

        publishSyncEvent(result);

        log.info("HelloAsso directory sync completed: created={}, updated={}, unchanged={}, duration={}ms",
            created.get(), updated.get(), unchanged.get(), durationMs);

        return result;
    }

    @Override
    @Transactional
    public SyncResultResponse syncOrganization(String slug) {
        log.info("Syncing single HelloAsso organization: {}", slug);
        long startTime = System.currentTimeMillis();

        HelloAssoOrganization org = helloAssoClient.getOrganization(slug).block();

        if (org == null) {
            throw new ExternalApiException(
                "HelloAsso returned null for organization: " + slug,
                "HelloAsso", 0);
        }

        UpsertResult upsertResult = upsertAssociation(org);

        long durationMs = System.currentTimeMillis() - startTime;
        Instant syncedAt = Instant.now();

        int created = upsertResult == UpsertResult.CREATED ? 1 : 0;
        int updated = upsertResult == UpsertResult.UPDATED ? 1 : 0;
        int unchanged = upsertResult == UpsertResult.UNCHANGED ? 1 : 0;

        return new SyncResultResponse(created, updated, unchanged, 1, syncedAt, durationMs);
    }

    @Override
    public boolean isStale(Association association) {
        if (association == null || association.getLastSyncedAt() == null) {
            return true;
        }
        return association.getLastSyncedAt()
            .isBefore(OffsetDateTime.now().minusHours(STALE_THRESHOLD_HOURS));
    }

    private void syncCity(String city, AtomicInteger created, AtomicInteger updated, AtomicInteger unchanged) {
        log.debug("Syncing city: {}", city);
        int pageIndex = 0;
        boolean hasMore = true;

        while (hasMore) {
            HelloAssoDirectoryRequest request = HelloAssoDirectoryRequest.builder()
                .city(city)
                .pageIndex(pageIndex)
                .pageSize(properties.getSync().getPageSize())
                .build();

            HelloAssoDirectoryResponse response = helloAssoClient.searchOrganizations(request).block();

            if (response == null || response.data() == null || response.data().isEmpty()) {
                hasMore = false;
                continue;
            }

            List<HelloAssoOrganization> organizations = response.data();
            for (HelloAssoOrganization org : organizations) {
                UpsertResult result = upsertAssociation(org);
                switch (result) {
                    case CREATED -> created.incrementAndGet();
                    case UPDATED -> updated.incrementAndGet();
                    case UNCHANGED -> unchanged.incrementAndGet();
                }
            }

            hasMore = response.pagination() != null
                && pageIndex < response.pagination().totalPages() - 1;
            pageIndex++;
        }
    }

    private UpsertResult upsertAssociation(HelloAssoOrganization org) {
        if (org.slug() == null || org.slug().isBlank()) {
            log.warn("Skipping organization with null/empty slug: {}", org.name());
            return UpsertResult.UNCHANGED;
        }

        Optional<Association> existing = associationRepository.findByHelloassoSlug(org.slug());

        if (existing.isPresent()) {
            Association entity = existing.get();
            if (hasChanges(entity, org)) {
                associationMapper.updateFromHelloAsso(entity, org);
                associationRepository.save(entity);
                return UpsertResult.UPDATED;
            }
            return UpsertResult.UNCHANGED;
        }

        Association newEntity = associationMapper.fromHelloAsso(org);
        associationRepository.save(newEntity);
        return UpsertResult.CREATED;
    }

    private boolean hasChanges(Association entity, HelloAssoOrganization org) {
        return !Objects.equals(entity.getName(), org.name())
            || !Objects.equals(entity.getDescription(), org.description())
            || !Objects.equals(entity.getCity(), org.city())
            || !Objects.equals(entity.getPostalCode(), org.zipCode())
            || !Objects.equals(entity.getDepartment(), org.department())
            || !Objects.equals(entity.getRegion(), org.region())
            || !Objects.equals(entity.getWebsite(), org.url())
            || !Objects.equals(entity.getLogoUrl(), org.logo())
            || entity.getCategory() != associationMapper.normalizeCategory(org.category());
    }

    private void publishSyncEvent(SyncResultResponse result) {
        try {
            HelloAssoSyncCompletedEvent event = new HelloAssoSyncCompletedEvent(
                result.created(), result.updated(), result.unchanged(),
                result.totalProcessed(), result.syncedAt(), result.durationMs(), null
            );
            kafkaTemplate.send(SYNC_TOPIC, event);
            log.debug("Published HelloAssoSyncCompletedEvent to Kafka topic '{}'", SYNC_TOPIC);
        } catch (Exception e) {
            log.error("Failed to publish sync event to Kafka: {}", e.getMessage(), e);
        }
    }

    private enum UpsertResult {
        CREATED,
        UPDATED,
        UNCHANGED
    }
}
