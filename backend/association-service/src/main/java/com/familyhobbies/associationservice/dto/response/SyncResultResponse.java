package com.familyhobbies.associationservice.dto.response;

import java.time.Instant;

/**
 * Response DTO for the admin sync endpoint.
 * Reports the outcome of a HelloAsso directory sync operation.
 *
 * @param created        number of new associations created
 * @param updated        number of existing associations updated
 * @param unchanged      number of associations that had no changes
 * @param totalProcessed total number of organizations processed from HelloAsso
 * @param syncedAt       timestamp when the sync completed
 * @param durationMs     duration of the sync operation in milliseconds
 */
public record SyncResultResponse(
    int created,
    int updated,
    int unchanged,
    int totalProcessed,
    Instant syncedAt,
    long durationMs
) {}
