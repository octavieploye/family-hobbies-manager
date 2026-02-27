package com.familyhobbies.paymentservice.service;

import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for invoice operations.
 */
public interface InvoiceService {

    /**
     * Creates an invoice from a completed payment.
     *
     * @param payment the completed payment entity
     * @return the created invoice entity
     */
    Invoice createInvoice(Payment payment);

    /**
     * Retrieves a single invoice by ID.
     *
     * @param invoiceId the invoice ID
     * @param userId    the user ID for access context (future authorization)
     * @return the full invoice response DTO
     */
    InvoiceResponse getInvoice(Long invoiceId, Long userId);

    /**
     * Retrieves all invoices for a given payment.
     *
     * @param paymentId the payment ID
     * @return list of invoice summary DTOs
     */
    List<InvoiceSummaryResponse> getInvoicesByPayment(Long paymentId);

    /**
     * Retrieves invoices for a user, paginated.
     *
     * @param userId   the user ID (maps to buyer email in current implementation)
     * @param pageable pagination parameters
     * @return a page of invoice summary DTOs
     */
    Page<InvoiceSummaryResponse> getInvoicesByUser(Long userId, Pageable pageable);

    /**
     * Generates and returns a PDF for the given invoice.
     *
     * @param invoiceId the invoice ID
     * @param userId    the user ID for access context (future authorization)
     * @return the PDF as a byte array
     */
    byte[] downloadInvoicePdf(Long invoiceId, Long userId);
}
