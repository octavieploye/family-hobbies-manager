package com.familyhobbies.associationservice.batch.processor;

import com.familyhobbies.associationservice.adapter.dto.HelloAssoOrganization;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maps HelloAsso organization DTOs to local Association entities.
 *
 * <p>Processing logic:
 * <ol>
 *   <li>Look up existing Association by {@code helloassoSlug}</li>
 *   <li>If found and not modified since last sync: return {@code null} (skip)</li>
 *   <li>If found and modified: update fields, set {@code lastSyncedAt}</li>
 *   <li>If not found: create new Association entity</li>
 * </ol>
 */
@Component
@StepScope
public class HelloAssoItemProcessor
        implements ItemProcessor<HelloAssoOrganization, Association> {

    private static final Logger log =
            LoggerFactory.getLogger(HelloAssoItemProcessor.class);

    private final AssociationRepository associationRepository;

    private final AtomicInteger newCount = new AtomicInteger(0);
    private final AtomicInteger updatedCount = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);

    public HelloAssoItemProcessor(AssociationRepository associationRepository) {
        this.associationRepository = associationRepository;
    }

    @Override
    public Association process(HelloAssoOrganization helloAssoOrg) {
        Optional<Association> existingOpt = associationRepository
                .findByHelloassoSlug(helloAssoOrg.slug());

        if (existingOpt.isPresent()) {
            Association existing = existingOpt.get();

            if (isUnchanged(existing, helloAssoOrg)) {
                skippedCount.incrementAndGet();
                log.trace("Skipping unchanged association: slug={}",
                        helloAssoOrg.slug());
                return null;
            }

            mapFields(helloAssoOrg, existing);
            existing.setLastSyncedAt(OffsetDateTime.now());
            updatedCount.incrementAndGet();
            log.debug("Updating association: slug={}, name={}",
                    helloAssoOrg.slug(), helloAssoOrg.name());
            return existing;
        }

        Association newAssociation = new Association();
        mapFields(helloAssoOrg, newAssociation);
        newAssociation.setHelloassoSlug(helloAssoOrg.slug());
        newAssociation.setSlug(helloAssoOrg.slug());
        newAssociation.setLastSyncedAt(OffsetDateTime.now());
        newAssociation.setStatus(AssociationStatus.ACTIVE);
        newCount.incrementAndGet();
        log.info("New association discovered: slug={}, name={}, city={}",
                helloAssoOrg.slug(), helloAssoOrg.name(), helloAssoOrg.city());
        return newAssociation;
    }

    private boolean isUnchanged(Association existing, HelloAssoOrganization incoming) {
        if (existing.getLastSyncedAt() == null) {
            return false;
        }
        if (incoming.updatedDate() == null) {
            return true;
        }
        return !incoming.updatedDate().isAfter(existing.getLastSyncedAt());
    }

    private void mapFields(HelloAssoOrganization source, Association target) {
        target.setName(source.name());
        target.setDescription(source.description());
        target.setCity(source.city());
        target.setPostalCode(source.zipCode());
        target.setLogoUrl(source.logo());
        target.setWebsite(source.url());
        if (source.category() != null) {
            try {
                target.setCategory(AssociationCategory.valueOf(source.category().toUpperCase()));
            } catch (IllegalArgumentException e) {
                target.setCategory(AssociationCategory.OTHER);
            }
        } else {
            target.setCategory(AssociationCategory.OTHER);
        }
    }

    public int getNewCount() {
        return newCount.get();
    }

    public int getUpdatedCount() {
        return updatedCount.get();
    }

    public int getSkippedCount() {
        return skippedCount.get();
    }
}
