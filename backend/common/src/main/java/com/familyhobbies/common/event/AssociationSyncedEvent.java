package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Event published when an association is synced from HelloAsso.
 * Consumed by services that need to react to association data updates.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssociationSyncedEvent extends DomainEvent {

    private Long associationId;
    private String helloAssoSlug;
    private String name;
    private String status;

    public AssociationSyncedEvent(Long associationId, String helloAssoSlug,
                                   String name, String status) {
        super("ASSOCIATION_SYNCED");
        this.associationId = associationId;
        this.helloAssoSlug = helloAssoSlug;
        this.name = name;
        this.status = status;
    }
}
