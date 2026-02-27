package com.familyhobbies.paymentservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Full response DTO for an invoice entity.
 */
public record InvoiceResponse(
        Long id,
        Long paymentId,
        String invoiceNumber,
        String status,
        String buyerName,
        String buyerEmail,
        String description,
        BigDecimal amount,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String currency,
        OffsetDateTime issuedAt,
        LocalDate dueDate,
        OffsetDateTime createdAt
) {}
