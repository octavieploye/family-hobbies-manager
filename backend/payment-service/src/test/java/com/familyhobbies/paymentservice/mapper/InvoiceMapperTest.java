package com.familyhobbies.paymentservice.mapper;

import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.InvoiceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for InvoiceMapper.
 *
 * Story: S6-006 -- Invoice Mapping
 * Tests: 3 test methods
 */
class InvoiceMapperTest {

    private InvoiceMapper invoiceMapper;

    @BeforeEach
    void setUp() {
        invoiceMapper = new InvoiceMapper();
    }

    @Test
    @DisplayName("should_mapToResponse_when_validInvoice")
    void should_mapToResponse_when_validInvoice() {
        // Given
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Payment payment = Payment.builder().id(10L).build();
        Invoice invoice = Invoice.builder()
                .id(1L)
                .payment(payment)
                .invoiceNumber("FHM-2026-000001")
                .status(InvoiceStatus.ISSUED)
                .buyerName("Famille Dupont")
                .buyerEmail("dupont@example.com")
                .description("Cotisation annuelle danse")
                .amount(new BigDecimal("75.00"))
                .taxRate(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(new BigDecimal("75.00"))
                .currency("EUR")
                .issuedAt(now)
                .dueDate(LocalDate.of(2026, 3, 31))
                .createdAt(now)
                .build();

        // When
        InvoiceResponse response = invoiceMapper.toResponse(invoice);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.paymentId()).isEqualTo(10L);
        assertThat(response.invoiceNumber()).isEqualTo("FHM-2026-000001");
        assertThat(response.status()).isEqualTo("ISSUED");
        assertThat(response.buyerName()).isEqualTo("Famille Dupont");
        assertThat(response.buyerEmail()).isEqualTo("dupont@example.com");
        assertThat(response.description()).isEqualTo("Cotisation annuelle danse");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(response.taxRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.taxAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(response.currency()).isEqualTo("EUR");
        assertThat(response.issuedAt()).isEqualTo(now);
        assertThat(response.dueDate()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("should_mapToSummaryResponse_when_validInvoice")
    void should_mapToSummaryResponse_when_validInvoice() {
        // Given
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Invoice invoice = Invoice.builder()
                .id(2L)
                .invoiceNumber("FHM-2026-000002")
                .status(InvoiceStatus.ISSUED)
                .buyerName("Famille Martin")
                .amount(new BigDecimal("120.00"))
                .totalAmount(new BigDecimal("120.00"))
                .issuedAt(now)
                .build();

        // When
        InvoiceSummaryResponse response = invoiceMapper.toSummaryResponse(invoice);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.invoiceNumber()).isEqualTo("FHM-2026-000002");
        assertThat(response.status()).isEqualTo("ISSUED");
        assertThat(response.buyerName()).isEqualTo("Famille Martin");
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(response.issuedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("should_handleNullFields_when_mapping")
    void should_handleNullFields_when_mapping() {
        // Given -- null invoice returns null
        InvoiceResponse responseFromNull = invoiceMapper.toResponse(null);
        assertThat(responseFromNull).isNull();

        InvoiceSummaryResponse summaryFromNull = invoiceMapper.toSummaryResponse(null);
        assertThat(summaryFromNull).isNull();

        // Given -- invoice with null fields
        Invoice emptyInvoice = Invoice.builder()
                .id(3L)
                .status(InvoiceStatus.DRAFT)
                .build();

        // When
        InvoiceResponse response = invoiceMapper.toResponse(emptyInvoice);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.paymentId()).isNull();
        assertThat(response.invoiceNumber()).isNull();
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.buyerName()).isNull();
        assertThat(response.buyerEmail()).isNull();
        assertThat(response.description()).isNull();
        assertThat(response.amount()).isNull();
    }
}
