package com.familyhobbies.paymentservice.mapper;

import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import org.springframework.stereotype.Component;

/**
 * Maps between Invoice entities and DTOs.
 * Manual mapper (no MapStruct) for full control and transparency.
 */
@Component
public class InvoiceMapper {

    /**
     * Maps an Invoice entity to a full InvoiceResponse DTO.
     *
     * @param invoice the invoice entity
     * @return the full invoice response DTO, or null if the invoice is null
     */
    public InvoiceResponse toResponse(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getPayment() != null ? invoice.getPayment().getId() : null,
                invoice.getInvoiceNumber(),
                invoice.getStatus() != null ? invoice.getStatus().name() : null,
                invoice.getBuyerName(),
                invoice.getBuyerEmail(),
                invoice.getDescription(),
                invoice.getAmount(),
                invoice.getTaxRate(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getIssuedAt(),
                invoice.getDueDate(),
                invoice.getCreatedAt()
        );
    }

    /**
     * Maps an Invoice entity to a summary InvoiceSummaryResponse DTO.
     *
     * @param invoice the invoice entity
     * @return the summary invoice response DTO, or null if the invoice is null
     */
    public InvoiceSummaryResponse toSummaryResponse(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        return new InvoiceSummaryResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getStatus() != null ? invoice.getStatus().name() : null,
                invoice.getBuyerName(),
                invoice.getAmount(),
                invoice.getTotalAmount(),
                invoice.getIssuedAt()
        );
    }
}
