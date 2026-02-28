package com.familyhobbies.userservice.entity;

import com.familyhobbies.userservice.entity.enums.ConsentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity mapping to the {@code t_consent_record} table.
 * Tracks RGPD consent decisions with full audit trail.
 *
 * Append-only design: each consent change creates a new record,
 * never updates existing ones. This provides a complete history
 * of consent changes for compliance auditing.
 */
@Entity
@Table(name = "t_consent_record")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30)
    private ConsentType consentType;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "version", nullable = false, length = 20)
    @Builder.Default
    private String version = "1.0";

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Sets consentedAt and createdAt to the current instant before initial persist.
     */
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.consentedAt == null) {
            this.consentedAt = now;
        }
        this.createdAt = now;
    }
}
