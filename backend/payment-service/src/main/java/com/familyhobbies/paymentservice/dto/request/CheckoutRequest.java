package com.familyhobbies.paymentservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Request DTO for initiating a HelloAsso checkout session.
 */
public record CheckoutRequest(
        @NotNull Long subscriptionId,
        @NotNull @Positive BigDecimal amount,
        String description,
        String paymentType,
        @NotBlank String returnUrl,
        @NotBlank String cancelUrl
) {}
