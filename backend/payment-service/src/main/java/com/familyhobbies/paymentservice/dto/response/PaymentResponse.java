package com.familyhobbies.paymentservice.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response DTO for a payment entity, optionally including invoice data.
 */
public record PaymentResponse(
        Long id,
        Long familyId,
        Long subscriptionId,
        BigDecimal amount,
        String currency,
        String status,
        String paymentMethod,
        OffsetDateTime paidAt,
        Long invoiceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
