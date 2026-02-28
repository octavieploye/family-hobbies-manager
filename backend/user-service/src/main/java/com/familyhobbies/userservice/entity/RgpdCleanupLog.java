package com.familyhobbies.userservice.entity;

import com.familyhobbies.userservice.entity.enums.CrossServiceCleanupStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Audit entity recording each RGPD data cleanup batch execution.
 *
 * <p>Tracks:
 * <ul>
 *     <li>How many users were eligible for anonymization (usersProcessed)</li>
 *     <li>How many were successfully anonymized (usersAnonymized)</li>
 *     <li>Whether cross-service cleanup (association-service, payment-service) succeeded</li>
 *     <li>Error details if any failures occurred</li>
 * </ul>
 *
 * <p>Maps to table {@code t_rgpd_cleanup_log}.
 */
@Entity
@Table(name = "t_rgpd_cleanup_log")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class RgpdCleanupLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_timestamp", nullable = false)
    private Instant executionTimestamp;

    @Column(name = "users_processed", nullable = false)
    private int usersProcessed;

    @Column(name = "users_anonymized", nullable = false)
    private int usersAnonymized;

    @Enumerated(EnumType.STRING)
    @Column(name = "cross_service_cleanup_status", nullable = false, length = 20)
    @Builder.Default
    private CrossServiceCleanupStatus crossServiceCleanupStatus = CrossServiceCleanupStatus.SUCCESS;

    @Column(name = "error_details", columnDefinition = "TEXT")
    private String errorDetails;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
