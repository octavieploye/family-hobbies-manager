package com.familyhobbies.associationservice.service;

import com.familyhobbies.associationservice.dto.response.SyncResultResponse;
import com.familyhobbies.associationservice.entity.Association;

/**
 * Service interface for synchronizing association data from the HelloAsso directory.
 */
public interface AssociationSyncService {

    /**
     * Syncs the full HelloAsso directory by iterating configured cities and paginating results.
     * Creates new associations, updates existing ones, and publishes a sync event to Kafka.
     *
     * @return a summary of the sync operation
     */
    SyncResultResponse syncDirectory();

    /**
     * Syncs a single organization from HelloAsso by its slug.
     *
     * @param slug the HelloAsso organization slug
     * @return a summary of the sync operation (1 created or 1 updated)
     */
    SyncResultResponse syncOrganization(String slug);

    /**
     * Checks if an association's HelloAsso data is stale (older than 24 hours).
     *
     * @param association the association to check
     * @return true if the data is stale or has never been synced
     */
    boolean isStale(Association association);
}
