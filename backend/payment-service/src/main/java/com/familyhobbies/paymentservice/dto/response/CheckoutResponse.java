package com.familyhobbies.paymentservice.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response DTO returned after initiating a HelloAsso checkout session.
 */
public record CheckoutResponse(
        Long paymentId,
        Long subscriptionId,
        BigDecimal amount,
        String status,
        String checkoutUrl,
        String helloassoCheckoutId,
        OffsetDateTime createdAt
) {}
