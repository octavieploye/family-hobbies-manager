package com.familyhobbies.paymentservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.paymentservice.adapter.InvoicePdfGenerator;
import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.InvoiceStatus;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import com.familyhobbies.paymentservice.mapper.InvoiceMapper;
import com.familyhobbies.paymentservice.repository.InvoiceRepository;
import com.familyhobbies.paymentservice.service.InvoiceNumberGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InvoiceServiceImpl.
 *
 * Story: S6-006 -- Invoice Generation Service
 * Tests: 6 test methods
 *
 * Uses @ExtendWith(MockitoExtension.class) -- no Spring context loaded.
 * Mocks: InvoiceRepository, InvoiceNumberGenerator, InvoicePdfGenerator.
 * Spy: InvoiceMapper (real mapping logic).
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceNumberGenerator invoiceNumberGenerator;

    @Mock
    private InvoicePdfGenerator invoicePdfGenerator;

    @Spy
    private InvoiceMapper invoiceMapper = new InvoiceMapper();

    @InjectMocks
    private InvoiceServiceImpl invoiceService;

    private Payment testPayment;
    private Invoice testInvoice;
    private static final Long PAYMENT_ID = 10L;
    private static final Long INVOICE_ID = 1L;
    private static final Long FAMILY_ID = 1L;
    private static final Long USER_ID = 100L;

    @BeforeEach
    void setUp() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        testPayment = Payment.builder()
                .id(PAYMENT_ID)
                .familyId(FAMILY_ID)
                .subscriptionId(200L)
                .amount(new BigDecimal("75.00"))
                .currency("EUR")
                .status(PaymentStatus.COMPLETED)
                .description("Cotisation annuelle danse")
                .createdAt(now)
                .updatedAt(now)
                .build();

        testInvoice = Invoice.builder()
                .id(INVOICE_ID)
                .payment(testPayment)
                .invoiceNumber("FHM-2026-000001")
                .status(InvoiceStatus.ISSUED)
                .buyerName("Famille 1")
                .description("Cotisation annuelle danse")
                .amount(new BigDecimal("75.00"))
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("75.00"))
                .currency("EUR")
                .issuedAt(now)
                .createdAt(now)
                .build();
    }

    @Test
    @DisplayName("should_createInvoice_when_paymentCompleted")
    void should_createInvoice_when_paymentCompleted() {
        // Given
        when(invoiceNumberGenerator.generate()).thenReturn("FHM-2026-000001");
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(INVOICE_ID);
            return inv;
        });

        // When
        Invoice result = invoiceService.createInvoice(testPayment);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(INVOICE_ID);
        assertThat(result.getPayment()).isEqualTo(testPayment);
        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(result.getCurrency()).isEqualTo("EUR");
        assertThat(result.getDescription()).isEqualTo("Cotisation annuelle danse");

        verify(invoiceRepository).save(any(Invoice.class));
    }

    @Test
    @DisplayName("should_setInvoiceNumber_when_creating")
    void should_setInvoiceNumber_when_creating() {
        // Given
        when(invoiceNumberGenerator.generate()).thenReturn("FHM-2026-000042");
        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        when(invoiceRepository.save(invoiceCaptor.capture())).thenAnswer(invocation -> {
            Invoice inv = invocation.getArgument(0);
            inv.setId(INVOICE_ID);
            return inv;
        });

        // When
        invoiceService.createInvoice(testPayment);

        // Then
        Invoice saved = invoiceCaptor.getValue();
        assertThat(saved.getInvoiceNumber()).isEqualTo("FHM-2026-000042");

        verify(invoiceNumberGenerator).generate();
    }

    @Test
    @DisplayName("should_returnInvoice_when_validId")
    void should_returnInvoice_when_validId() {
        // Given
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(testInvoice));

        // When
        InvoiceResponse response = invoiceService.getInvoice(INVOICE_ID, USER_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(INVOICE_ID);
        assertThat(response.paymentId()).isEqualTo(PAYMENT_ID);
        assertThat(response.invoiceNumber()).isEqualTo("FHM-2026-000001");
        assertThat(response.status()).isEqualTo("ISSUED");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    @DisplayName("should_throwNotFoundException_when_invalidId")
    void should_throwNotFoundException_when_invalidId() {
        // Given
        when(invoiceRepository.findById(999L)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> invoiceService.getInvoice(999L, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Invoice not found with id: 999");
    }

    @Test
    @DisplayName("should_returnPdfBytes_when_downloadRequested")
    void should_returnPdfBytes_when_downloadRequested() {
        // Given
        byte[] pdfContent = new byte[]{0x25, 0x50, 0x44, 0x46}; // %PDF header
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(testInvoice));
        when(invoicePdfGenerator.generatePdf(testInvoice)).thenReturn(pdfContent);

        // When
        byte[] result = invoiceService.downloadInvoicePdf(INVOICE_ID, USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(pdfContent);
        verify(invoicePdfGenerator).generatePdf(testInvoice);
    }

    @Test
    @DisplayName("should_returnInvoicesByPayment_when_queried")
    void should_returnInvoicesByPayment_when_queried() {
        // Given
        when(invoiceRepository.findAllByPaymentId(PAYMENT_ID))
                .thenReturn(List.of(testInvoice));

        // When
        List<InvoiceSummaryResponse> result = invoiceService.getInvoicesByPayment(PAYMENT_ID);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(INVOICE_ID);
        assertThat(result.get(0).invoiceNumber()).isEqualTo("FHM-2026-000001");
        assertThat(result.get(0).status()).isEqualTo("ISSUED");
        assertThat(result.get(0).buyerName()).isEqualTo("Famille 1");
    }
}
