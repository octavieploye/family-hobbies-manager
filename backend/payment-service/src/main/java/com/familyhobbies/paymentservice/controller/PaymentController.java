package com.familyhobbies.paymentservice.controller;

import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import com.familyhobbies.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * REST controller for payment operations.
 * All endpoints require authentication (JWT validated by gateway).
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment checkout, status, and family payment history")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Initiates a HelloAsso checkout session.
     * POST /api/v1/payments/checkout
     */
    @PostMapping("/checkout")
    @Operation(summary = "Initiate checkout",
               description = "Creates a HelloAsso checkout session and returns the checkout URL")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Checkout session created"),
        @ApiResponse(responseCode = "400", description = "Invalid checkout data"),
        @ApiResponse(responseCode = "502", description = "HelloAsso API unavailable")
    })
    public ResponseEntity<CheckoutResponse> initiateCheckout(
            @Valid @RequestBody CheckoutRequest request,
            @RequestHeader("X-User-Id") Long familyId) {
        CheckoutResponse response = paymentService.initiateCheckout(request, familyId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Gets a single payment by ID.
     * GET /api/v1/payments/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID",
               description = "Returns a single payment with optional invoice details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment found"),
        @ApiResponse(responseCode = "404", description = "Payment not found")
    })
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long familyId,
            @RequestParam(required = false, defaultValue = "false") boolean includeInvoice) {
        PaymentResponse response = paymentService.getPayment(id, familyId, includeInvoice);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists payments for a family with optional filters.
     * GET /api/v1/payments/family/{familyId}?status=COMPLETED&from=...&to=...&page=0&size=20
     */
    @GetMapping("/family/{familyId}")
    @Operation(summary = "Get payments by family",
               description = "Lists payments for a family with optional status and date range filters")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payments list returned")
    })
    public ResponseEntity<Page<PaymentResponse>> getPaymentsByFamily(
            @PathVariable Long familyId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        Page<PaymentResponse> payments = paymentService.getPaymentsByFamily(
                familyId, status, from, to,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ResponseEntity.ok(payments);
    }
}
