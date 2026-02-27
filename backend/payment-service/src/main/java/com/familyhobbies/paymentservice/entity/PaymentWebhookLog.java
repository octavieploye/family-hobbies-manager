package com.familyhobbies.paymentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * JPA entity mapping to the {@code t_payment_webhook_log} table.
 * Logs every webhook received from HelloAsso for idempotency and debugging.
 */
@Entity
@Table(name = "t_payment_webhook_log")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class PaymentWebhookLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "helloasso_event_id", nullable = false, unique = true, length = 255)
    private String helloassoEventId;

    @Column(name = "event_type", length = 50)
    private String eventType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "signature", length = 255)
    private String signature;

    @Column(name = "processed")
    @Builder.Default
    private Boolean processed = false;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;
}
