package com.familyhobbies.paymentservice.service;

import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;

/**
 * Service interface for payment operations.
 */
public interface PaymentService {

    /**
     * Initiates a HelloAsso checkout session and creates a payment record.
     *
     * @param request  the checkout request DTO
     * @param familyId the ID of the family initiating payment
     * @return the checkout response with redirect URL
     */
    CheckoutResponse initiateCheckout(CheckoutRequest request, Long familyId);

    /**
     * Retrieves a single payment by ID, verifying family ownership.
     *
     * @param paymentId      the payment ID
     * @param familyId       the family ID for ownership verification
     * @param includeInvoice whether to include invoice data in the response
     * @return the payment response DTO
     */
    PaymentResponse getPayment(Long paymentId, Long familyId, boolean includeInvoice);

    /**
     * Lists payments for a family with optional filtering.
     *
     * @param familyId     the family ID
     * @param statusFilter optional status filter
     * @param fromDate     optional start date filter
     * @param toDate       optional end date filter
     * @param pageable     pagination parameters
     * @return a page of payment responses
     */
    Page<PaymentResponse> getPaymentsByFamily(Long familyId, PaymentStatus statusFilter,
                                               OffsetDateTime fromDate, OffsetDateTime toDate,
                                               Pageable pageable);
}
