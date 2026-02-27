package com.familyhobbies.paymentservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient.HelloAssoCheckoutResponse;
import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import com.familyhobbies.paymentservice.mapper.PaymentMapper;
import com.familyhobbies.paymentservice.repository.InvoiceRepository;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import com.familyhobbies.paymentservice.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Implementation of {@link PaymentService}.
 * Orchestrates checkout initiation, payment retrieval, and family-scoped listing.
 */
@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentMapper paymentMapper;
    private final HelloAssoCheckoutClient checkoutClient;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              InvoiceRepository invoiceRepository,
                              PaymentMapper paymentMapper,
                              HelloAssoCheckoutClient checkoutClient) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentMapper = paymentMapper;
        this.checkoutClient = checkoutClient;
    }

    @Override
    @Transactional
    public CheckoutResponse initiateCheckout(CheckoutRequest request, Long familyId) {
        // Check for duplicate pending payment for the same subscription
        if (paymentRepository.existsBySubscriptionIdAndStatus(
                request.subscriptionId(), PaymentStatus.PENDING)) {
            throw new ConflictException(
                    "A pending payment already exists for subscription: " + request.subscriptionId());
        }

        // Create payment entity
        Payment payment = paymentMapper.fromCheckoutRequest(request, familyId);
        payment = paymentRepository.save(payment);

        // Convert amount to cents for HelloAsso
        int amountCents = request.amount().multiply(java.math.BigDecimal.valueOf(100)).intValue();

        // Call HelloAsso to initiate checkout
        HelloAssoCheckoutResponse helloAssoResponse = checkoutClient.initiateCheckout(
                "default-org",
                amountCents,
                request.description(),
                request.cancelUrl(),
                request.cancelUrl(),
                request.returnUrl());

        // Update payment with HelloAsso checkout ID
        payment.setHelloassoCheckoutId(helloAssoResponse.id());
        payment = paymentRepository.save(payment);

        log.info("Checkout initiated: paymentId={}, checkoutId={}", payment.getId(), helloAssoResponse.id());

        return paymentMapper.toCheckoutResponse(payment, helloAssoResponse.redirectUrl());
    }

    @Override
    public PaymentResponse getPayment(Long paymentId, Long familyId, boolean includeInvoice) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Payment", paymentId));

        if (!payment.getFamilyId().equals(familyId)) {
            throw new ForbiddenException(
                    "Payment does not belong to family: " + familyId);
        }

        Invoice invoice = null;
        if (includeInvoice) {
            invoice = invoiceRepository.findByPaymentId(paymentId).orElse(null);
        }

        return paymentMapper.toPaymentResponse(payment, invoice);
    }

    @Override
    public Page<PaymentResponse> getPaymentsByFamily(Long familyId, PaymentStatus statusFilter,
                                                      OffsetDateTime fromDate, OffsetDateTime toDate,
                                                      Pageable pageable) {
        return paymentRepository.findByFamilyIdWithFilters(familyId, statusFilter, fromDate, toDate, pageable)
                .map(payment -> paymentMapper.toPaymentResponse(payment, null));
    }
}
