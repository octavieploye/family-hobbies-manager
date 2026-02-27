package com.familyhobbies.paymentservice.entity;

import com.familyhobbies.paymentservice.entity.enums.PaymentMethod;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JPA entity mapping to the {@code t_payment} table.
 * Tracks payment lifecycle from checkout initiation through HelloAsso to completion/failure.
 */
@Entity
@Table(name = "t_payment")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "subscription_id", nullable = false)
    private Long subscriptionId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "payment_type", length = 50)
    private String paymentType;

    @Column(name = "helloasso_checkout_id", length = 255)
    private String helloassoCheckoutId;

    @Column(name = "helloasso_payment_id", length = 255)
    private String helloassoPaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 30)
    private PaymentMethod paymentMethod;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "failed_at")
    private OffsetDateTime failedAt;

    @Column(name = "refunded_at")
    private OffsetDateTime refundedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
