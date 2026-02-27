package com.familyhobbies.paymentservice.entity.enums;

/**
 * Lifecycle statuses for a payment.
 */
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    COMPLETED,
    FAILED,
    REFUNDED,
    CANCELLED
}
