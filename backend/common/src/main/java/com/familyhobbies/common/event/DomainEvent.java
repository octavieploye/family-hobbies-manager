package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base class for all Kafka domain events.
 * Every event carries a unique ID, an event type, a timestamp, and a version.
 * Events are immutable after creation — no setters exposed.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class DomainEvent {

    private UUID eventId;
    private String eventType;
    private Instant occurredAt;
    private int version;

    /**
     * Creates a new DomainEvent with auto-generated eventId, current timestamp,
     * and default version 1.
     *
     * @param eventType the type identifier for this event (e.g., "USER_REGISTERED")
     */
    protected DomainEvent(String eventType) {
        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.occurredAt = Instant.now();
        this.version = 1;
    }
}
