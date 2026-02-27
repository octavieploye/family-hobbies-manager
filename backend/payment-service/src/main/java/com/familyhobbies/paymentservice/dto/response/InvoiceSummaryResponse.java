package com.familyhobbies.paymentservice.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Summary response DTO for invoice list views.
 */
public record InvoiceSummaryResponse(
        Long id,
        String invoiceNumber,
        String status,
        String buyerName,
        BigDecimal amount,
        BigDecimal totalAmount,
        OffsetDateTime issuedAt
) {}
