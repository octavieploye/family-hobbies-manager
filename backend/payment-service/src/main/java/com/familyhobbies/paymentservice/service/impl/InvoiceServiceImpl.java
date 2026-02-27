package com.familyhobbies.paymentservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.paymentservice.adapter.InvoicePdfGenerator;
import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.InvoiceStatus;
import com.familyhobbies.paymentservice.mapper.InvoiceMapper;
import com.familyhobbies.paymentservice.repository.InvoiceRepository;
import com.familyhobbies.paymentservice.service.InvoiceNumberGenerator;
import com.familyhobbies.paymentservice.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of {@link InvoiceService}.
 * Orchestrates invoice creation from payments, retrieval, and PDF generation.
 */
@Service
@Transactional(readOnly = true)
public class InvoiceServiceImpl implements InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceServiceImpl.class);

    private final InvoiceRepository invoiceRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final InvoicePdfGenerator invoicePdfGenerator;
    private final InvoiceMapper invoiceMapper;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository,
                              InvoiceNumberGenerator invoiceNumberGenerator,
                              InvoicePdfGenerator invoicePdfGenerator,
                              InvoiceMapper invoiceMapper) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceNumberGenerator = invoiceNumberGenerator;
        this.invoicePdfGenerator = invoicePdfGenerator;
        this.invoiceMapper = invoiceMapper;
    }

    @Override
    @Transactional
    public Invoice createInvoice(Payment payment) {
        String invoiceNumber = invoiceNumberGenerator.generate();

        BigDecimal amount = payment.getAmount();
        BigDecimal taxRate = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = amount != null ? amount.add(taxAmount) : BigDecimal.ZERO;

        Invoice invoice = Invoice.builder()
                .payment(payment)
                .invoiceNumber(invoiceNumber)
                .status(InvoiceStatus.ISSUED)
                .issuedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .buyerName("Famille " + payment.getFamilyId())
                .description(payment.getDescription())
                .amount(amount)
                .taxRate(taxRate)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .currency(payment.getCurrency())
                .build();

        invoice = invoiceRepository.save(invoice);
        log.info("Invoice created: invoiceId={}, invoiceNumber={}, paymentId={}",
                invoice.getId(), invoiceNumber, payment.getId());

        return invoice;
    }

    @Override
    public InvoiceResponse getInvoice(Long invoiceId, Long userId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Invoice", invoiceId));

        return invoiceMapper.toResponse(invoice);
    }

    @Override
    public List<InvoiceSummaryResponse> getInvoicesByPayment(Long paymentId) {
        return invoiceRepository.findAllByPaymentId(paymentId).stream()
                .map(invoiceMapper::toSummaryResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<InvoiceSummaryResponse> getInvoicesByUser(Long userId, Pageable pageable) {
        // For MVP, userId is mapped to a buyer email pattern.
        // In production, this would use a proper user-to-email lookup.
        String buyerEmail = "user-" + userId + "@familyhobbies.com";
        return invoiceRepository.findByBuyerEmailOrderByIssuedAtDesc(buyerEmail, pageable)
                .map(invoiceMapper::toSummaryResponse);
    }

    @Override
    public byte[] downloadInvoicePdf(Long invoiceId, Long userId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> ResourceNotFoundException.of("Invoice", invoiceId));

        return invoicePdfGenerator.generatePdf(invoice);
    }
}
