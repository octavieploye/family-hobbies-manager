package com.familyhobbies.paymentservice.controller;

import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for invoice operations.
 * All endpoints require authentication (JWT validated by gateway).
 */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Invoice retrieval and PDF download")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    /**
     * Gets a single invoice by ID.
     * GET /api/v1/invoices/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get invoice by ID",
               description = "Returns a single invoice by its ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invoice found"),
        @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<InvoiceResponse> getInvoice(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        InvoiceResponse response = invoiceService.getInvoice(id, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Downloads an invoice as PDF.
     * GET /api/v1/invoices/{id}/download
     */
    @GetMapping("/{id}/download")
    @Operation(summary = "Download invoice PDF",
               description = "Downloads an invoice as a PDF file")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "PDF file returned"),
        @ApiResponse(responseCode = "404", description = "Invoice not found")
    })
    public ResponseEntity<byte[]> downloadInvoicePdf(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        byte[] pdfBytes = invoiceService.downloadInvoicePdf(id, userId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"")
                .body(pdfBytes);
    }

    /**
     * Gets all invoices for a given payment.
     * GET /api/v1/invoices/payment/{paymentId}
     */
    @GetMapping("/payment/{paymentId}")
    @Operation(summary = "Get invoices by payment",
               description = "Returns all invoices associated with a specific payment")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invoices list returned")
    })
    public ResponseEntity<List<InvoiceSummaryResponse>> getInvoicesByPayment(
            @PathVariable Long paymentId) {
        List<InvoiceSummaryResponse> invoices = invoiceService.getInvoicesByPayment(paymentId);
        return ResponseEntity.ok(invoices);
    }

    /**
     * Gets invoices for a user, paginated.
     * GET /api/v1/invoices/user/{userId}
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get invoices by user",
               description = "Returns paginated invoices for a specific user")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Invoices page returned")
    })
    public ResponseEntity<Page<InvoiceSummaryResponse>> getInvoicesByUser(
            @PathVariable Long userId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        Page<InvoiceSummaryResponse> invoices = invoiceService.getInvoicesByUser(
                userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt")));
        return ResponseEntity.ok(invoices);
    }
}
