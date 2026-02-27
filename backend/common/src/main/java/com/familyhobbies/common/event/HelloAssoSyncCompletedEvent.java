package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when HelloAsso directory sync completes.
 * Used for monitoring and admin notifications.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HelloAssoSyncCompletedEvent extends DomainEvent {

    private int created;
    private int updated;
    private int unchanged;
    private int totalProcessed;
    private Instant syncedAt;
    private long durationMs;
    private Long triggeredByUserId;

    public HelloAssoSyncCompletedEvent(int created, int updated, int unchanged,
                                        int totalProcessed, Instant syncedAt,
                                        long durationMs, Long triggeredByUserId) {
        super("HELLOASSO_SYNC_COMPLETED");
        this.created = created;
        this.updated = updated;
        this.unchanged = unchanged;
        this.totalProcessed = totalProcessed;
        this.syncedAt = syncedAt;
        this.durationMs = durationMs;
        this.triggeredByUserId = triggeredByUserId;
    }
}
