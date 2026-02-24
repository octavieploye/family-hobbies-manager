# Story S6-006: Invoice Generation -- TDD Tests Companion

> Companion to: [S6-006: Invoice Generation](./S6-006-invoice-generation.md)
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Overview

This file contains the full JUnit 5 test source code for the invoice generation story (S6-006). Tests are organized into five classes:

1. **InvoiceMapperTest** -- 4 tests for entity-to-DTO conversion
2. **InvoiceNumberGeneratorTest** -- 3 tests for sequential number generation
3. **InvoiceServiceImplTest** -- 8 tests for service business logic
4. **PaymentCompletedEventConsumerTest** -- 3 tests for Kafka consumer
5. **InvoiceControllerTest** -- 5 tests for REST endpoint integration

All tests are written TDD-style: they define the expected behavior contract before implementation.

---

## Test 1: InvoiceMapperTest

- **What**: Unit tests for the InvoiceMapper component
- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/mapper/InvoiceMapperTest.java`
- **Why**: Validates entity-to-DTO conversions for both full and summary responses

```java
package com.familyhobbies.paymentservice.mapper;

import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.InvoiceStatus;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InvoiceMapper}.
 */
class InvoiceMapperTest {

    private InvoiceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new InvoiceMapper();
    }

    private Payment buildPayment() {
        Payment payment = Payment.builder()
                .id(100L)
                .familyId(1L)
                .subscriptionId(50L)
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .status(PaymentStatus.COMPLETED)
                .paidAt(Instant.now())
                .build();
        // Manually set timestamps since @PrePersist won't run in tests
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        return payment;
    }

    private Invoice buildInvoice(Payment payment) {
        return Invoice.builder()
                .id(10L)
                .payment(payment)
                .invoiceNumber("FHM-2026-000001")
                .familyId(1L)
                .associationId(5L)
                .amountHt(new BigDecimal("150.00"))
                .taxAmount(BigDecimal.ZERO)
                .amountTtc(new BigDecimal("150.00"))
                .status(InvoiceStatus.ISSUED)
                .issuedAt(Instant.now())
                .paidAt(payment.getPaidAt())
                .currency("EUR")
                .payerName("Famille Dupont")
                .payerEmail("dupont@example.com")
                .memberName("Lucas Dupont")
                .activityName("Judo - Cours enfants")
                .associationName("Judo Club Lyon")
                .season("2025-2026")
                .pdfPath("data/invoices/FHM-2026-000001.pdf")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("should_map_invoice_to_full_response_when_all_fields_present")
    void should_map_invoice_to_full_response_when_all_fields_present() {
        // Given
        Payment payment = buildPayment();
        Invoice invoice = buildInvoice(payment);

        // When
        InvoiceResponse response = mapper.toInvoiceResponse(invoice);

        // Then
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getInvoiceNumber()).isEqualTo("FHM-2026-000001");
        assertThat(response.getPaymentId()).isEqualTo(100L);
        assertThat(response.getSubscriptionId()).isEqualTo(50L);
        assertThat(response.getFamilyId()).isEqualTo(1L);
        assertThat(response.getFamilyName()).isEqualTo("Famille Dupont");
        assertThat(response.getAssociationName()).isEqualTo("Judo Club Lyon");
        assertThat(response.getActivityName()).isEqualTo("Judo - Cours enfants");
        assertThat(response.getFamilyMemberName()).isEqualTo("Lucas Dupont");
        assertThat(response.getSeason()).isEqualTo("2025-2026");
        assertThat(response.getSubtotal())
                .isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(response.getTax())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotal())
                .isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(response.getCurrency()).isEqualTo("EUR");
        assertThat(response.getStatus()).isEqualTo("ISSUED");
        assertThat(response.getPayerEmail()).isEqualTo("dupont@example.com");
        assertThat(response.getPayerName()).isEqualTo("Famille Dupont");
    }

    @Test
    @DisplayName("should_map_invoice_to_summary_response_when_list_view")
    void should_map_invoice_to_summary_response_when_list_view() {
        // Given
        Payment payment = buildPayment();
        Invoice invoice = buildInvoice(payment);

        // When
        InvoiceSummaryResponse response =
                mapper.toInvoiceSummaryResponse(invoice);

        // Then
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getInvoiceNumber()).isEqualTo("FHM-2026-000001");
        assertThat(response.getAssociationName()).isEqualTo("Judo Club Lyon");
        assertThat(response.getActivityName()).isEqualTo("Judo - Cours enfants");
        assertThat(response.getFamilyMemberName()).isEqualTo("Lucas Dupont");
        assertThat(response.getSeason()).isEqualTo("2025-2026");
        assertThat(response.getTotal())
                .isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(response.getCurrency()).isEqualTo("EUR");
        assertThat(response.getStatus()).isEqualTo("ISSUED");
    }

    @Test
    @DisplayName("should_generate_download_url_from_invoice_id")
    void should_generate_download_url_from_invoice_id() {
        // Given
        Payment payment = buildPayment();
        Invoice invoice = buildInvoice(payment);

        // When
        InvoiceResponse response = mapper.toInvoiceResponse(invoice);

        // Then
        assertThat(response.getDownloadUrl())
                .isEqualTo("/api/v1/invoices/10/download");
    }

    @Test
    @DisplayName("should_build_line_item_with_full_description_when_all_fields")
    void should_build_line_item_with_full_description_when_all_fields() {
        // Given
        Payment payment = buildPayment();
        Invoice invoice = buildInvoice(payment);

        // When
        InvoiceResponse response = mapper.toInvoiceResponse(invoice);

        // Then
        assertThat(response.getLineItems()).hasSize(1);
        assertThat(response.getLineItems().get(0).getDescription())
                .contains("Judo - Cours enfants")
                .contains("Judo Club Lyon")
                .contains("Lucas Dupont")
                .contains("2025-2026");
        assertThat(response.getLineItems().get(0).getAmount())
                .isEqualByComparingTo(new BigDecimal("150.00"));
    }
}
```

---

## Test 2: InvoiceNumberGeneratorTest

- **What**: Unit tests for the InvoiceNumberGenerator
- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/service/InvoiceNumberGeneratorTest.java`
- **Why**: Validates the invoice number format and sequential generation

```java
package com.familyhobbies.paymentservice.service;

import com.familyhobbies.paymentservice.repository.InvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Year;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InvoiceNumberGenerator}.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceNumberGeneratorTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private InvoiceNumberGenerator generator;

    @Test
    @DisplayName("should_generate_invoice_number_with_FHM_prefix_and_year_when_first_invoice")
    void should_generate_invoice_number_with_FHM_prefix_and_year_when_first_invoice() {
        // Given
        when(invoiceRepository.getNextSequenceValue()).thenReturn(1L);

        // When
        String invoiceNumber = generator.generateNextInvoiceNumber();

        // Then
        int currentYear = Year.now().getValue();
        assertThat(invoiceNumber).isEqualTo(
                String.format("FHM-%d-000001", currentYear));
    }

    @Test
    @DisplayName("should_generate_sequential_numbers_when_called_multiple_times")
    void should_generate_sequential_numbers_when_called_multiple_times() {
        // Given
        when(invoiceRepository.getNextSequenceValue())
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(3L);

        // When
        String first = generator.generateNextInvoiceNumber();
        String second = generator.generateNextInvoiceNumber();
        String third = generator.generateNextInvoiceNumber();

        // Then
        int year = Year.now().getValue();
        assertThat(first).isEqualTo(String.format("FHM-%d-000001", year));
        assertThat(second).isEqualTo(String.format("FHM-%d-000002", year));
        assertThat(third).isEqualTo(String.format("FHM-%d-000003", year));
    }

    @Test
    @DisplayName("should_pad_sequence_to_six_digits_when_large_number")
    void should_pad_sequence_to_six_digits_when_large_number() {
        // Given
        when(invoiceRepository.getNextSequenceValue()).thenReturn(123456L);

        // When
        String invoiceNumber = generator.generateNextInvoiceNumber();

        // Then
        int year = Year.now().getValue();
        assertThat(invoiceNumber).isEqualTo(
                String.format("FHM-%d-123456", year));
    }
}
```

---

## Test 3: InvoiceServiceImplTest

- **What**: Unit tests for InvoiceServiceImpl with mocked dependencies
- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/service/impl/InvoiceServiceImplTest.java`
- **Why**: Validates generation flow, idempotency, error handling, and query methods

```java
package com.familyhobbies.paymentservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.paymentservice.adapter.InvoicePdfGenerator;
import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.enums.InvoiceStatus;
import com.familyhobbies.paymentservice.enums.PaymentStatus;
import com.familyhobbies.paymentservice.mapper.InvoiceMapper;
import com.familyhobbies.paymentservice.repository.InvoiceRepository;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import com.familyhobbies.paymentservice.service.InvoiceNumberGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InvoiceServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceNumberGenerator invoiceNumberGenerator;

    @Mock
    private InvoicePdfGenerator invoicePdfGenerator;

    @Spy
    private InvoiceMapper invoiceMapper = new InvoiceMapper();

    @InjectMocks
    private InvoiceServiceImpl service;

    private Payment buildCompletedPayment() {
        Payment payment = Payment.builder()
                .id(100L)
                .familyId(1L)
                .subscriptionId(50L)
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .status(PaymentStatus.COMPLETED)
                .paidAt(Instant.now())
                .description("Cotisation annuelle - Judo Club Lyon")
                .build();
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        return payment;
    }

    private Invoice buildInvoice(Payment payment) {
        return Invoice.builder()
                .id(10L)
                .payment(payment)
                .invoiceNumber("FHM-2026-000001")
                .familyId(payment.getFamilyId())
                .associationId(5L)
                .amountHt(payment.getAmount())
                .taxAmount(BigDecimal.ZERO)
                .amountTtc(payment.getAmount())
                .status(InvoiceStatus.ISSUED)
                .issuedAt(Instant.now())
                .paidAt(payment.getPaidAt())
                .currency("EUR")
                .payerName("Famille Dupont")
                .season("2025-2026")
                .pdfPath("data/invoices/FHM-2026-000001.pdf")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("generateInvoice")
    class GenerateInvoice {

        @Test
        @DisplayName("should_create_invoice_with_correct_data_when_payment_exists")
        void should_create_invoice_with_correct_data_when_payment_exists() {
            // Given
            Payment payment = buildCompletedPayment();
            when(invoiceRepository.findByPaymentId(100L))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findById(100L))
                    .thenReturn(Optional.of(payment));
            when(invoiceNumberGenerator.generateNextInvoiceNumber())
                    .thenReturn("FHM-2026-000001");
            when(invoicePdfGenerator.generatePdf(any(Invoice.class)))
                    .thenReturn("data/invoices/FHM-2026-000001.pdf");
            when(invoiceRepository.save(any(Invoice.class)))
                    .thenAnswer(inv -> {
                        Invoice saved = inv.getArgument(0);
                        saved.setId(10L);
                        saved.setCreatedAt(Instant.now());
                        saved.setUpdatedAt(Instant.now());
                        return saved;
                    });

            // When
            InvoiceResponse response = service.generateInvoice(100L);

            // Then
            ArgumentCaptor<Invoice> captor =
                    ArgumentCaptor.forClass(Invoice.class);
            verify(invoiceRepository).save(captor.capture());
            Invoice saved = captor.getValue();

            assertThat(saved.getInvoiceNumber()).isEqualTo("FHM-2026-000001");
            assertThat(saved.getFamilyId()).isEqualTo(1L);
            assertThat(saved.getAmountHt())
                    .isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(saved.getTaxAmount())
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getAmountTtc())
                    .isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(saved.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
            assertThat(saved.getPdfPath())
                    .isEqualTo("data/invoices/FHM-2026-000001.pdf");
        }

        @Test
        @DisplayName("should_throw_IllegalStateException_when_invoice_already_exists")
        void should_throw_IllegalStateException_when_invoice_already_exists() {
            // Given
            Payment payment = buildCompletedPayment();
            Invoice existing = buildInvoice(payment);
            when(invoiceRepository.findByPaymentId(100L))
                    .thenReturn(Optional.of(existing));

            // When / Then
            assertThatThrownBy(() -> service.generateInvoice(100L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("100");

            verify(paymentRepository, never()).findById(any());
            verify(invoiceRepository, never()).save(any());
        }

        @Test
        @DisplayName("should_throw_ResourceNotFoundException_when_payment_not_found")
        void should_throw_ResourceNotFoundException_when_payment_not_found() {
            // Given
            when(invoiceRepository.findByPaymentId(999L))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findById(999L))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.generateInvoice(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("should_set_tax_to_zero_when_association_loi_1901")
        void should_set_tax_to_zero_when_association_loi_1901() {
            // Given
            Payment payment = buildCompletedPayment();
            when(invoiceRepository.findByPaymentId(100L))
                    .thenReturn(Optional.empty());
            when(paymentRepository.findById(100L))
                    .thenReturn(Optional.of(payment));
            when(invoiceNumberGenerator.generateNextInvoiceNumber())
                    .thenReturn("FHM-2026-000002");
            when(invoicePdfGenerator.generatePdf(any(Invoice.class)))
                    .thenReturn("data/invoices/FHM-2026-000002.pdf");
            when(invoiceRepository.save(any(Invoice.class)))
                    .thenAnswer(inv -> {
                        Invoice saved = inv.getArgument(0);
                        saved.setId(11L);
                        saved.setCreatedAt(Instant.now());
                        saved.setUpdatedAt(Instant.now());
                        return saved;
                    });

            // When
            service.generateInvoice(100L);

            // Then
            ArgumentCaptor<Invoice> captor =
                    ArgumentCaptor.forClass(Invoice.class);
            verify(invoiceRepository).save(captor.capture());
            Invoice saved = captor.getValue();
            assertThat(saved.getTaxAmount())
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(saved.getAmountHt())
                    .isEqualByComparingTo(saved.getAmountTtc());
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        @DisplayName("should_return_invoice_response_when_found")
        void should_return_invoice_response_when_found() {
            // Given
            Payment payment = buildCompletedPayment();
            Invoice invoice = buildInvoice(payment);
            when(invoiceRepository.findById(10L))
                    .thenReturn(Optional.of(invoice));

            // When
            InvoiceResponse response = service.findById(10L);

            // Then
            assertThat(response.getInvoiceNumber())
                    .isEqualTo("FHM-2026-000001");
        }

        @Test
        @DisplayName("should_throw_ResourceNotFoundException_when_not_found")
        void should_throw_ResourceNotFoundException_when_not_found() {
            // Given
            when(invoiceRepository.findById(999L))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.findById(999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    @Nested
    @DisplayName("findByFamilyId")
    class FindByFamilyId {

        @Test
        @DisplayName("should_return_paginated_summaries_when_family_has_invoices")
        void should_return_paginated_summaries_when_family_has_invoices() {
            // Given
            Payment payment = buildCompletedPayment();
            Invoice invoice = buildInvoice(payment);
            Pageable pageable = PageRequest.of(0, 20);
            Page<Invoice> page =
                    new PageImpl<>(List.of(invoice), pageable, 1);
            when(invoiceRepository.findByFamilyIdWithFilters(
                    eq(1L), eq(null), eq(null), eq(null), eq(null),
                    any(Pageable.class))).thenReturn(page);

            // When
            Page<InvoiceSummaryResponse> result =
                    service.findByFamilyId(1L, null, null, null, null, pageable);

            // Then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getInvoiceNumber())
                    .isEqualTo("FHM-2026-000001");
        }
    }

    @Nested
    @DisplayName("downloadInvoice")
    class DownloadInvoice {

        @Test
        @DisplayName("should_throw_ResourceNotFoundException_when_invoice_not_found")
        void should_throw_ResourceNotFoundException_when_invoice_not_found() {
            // Given
            when(invoiceRepository.findById(999L))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.downloadInvoice(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
```

---

## Test 4: PaymentCompletedEventConsumerTest

- **What**: Unit tests for the Kafka consumer
- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/listener/PaymentCompletedEventConsumerTest.java`
- **Why**: Validates event consumption, idempotency handling, and error propagation

```java
package com.familyhobbies.paymentservice.listener;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.service.InvoiceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PaymentCompletedEventConsumer}.
 */
@ExtendWith(MockitoExtension.class)
class PaymentCompletedEventConsumerTest {

    @Mock
    private InvoiceService invoiceService;

    @InjectMocks
    private PaymentCompletedEventConsumer consumer;

    private PaymentCompletedEvent buildEvent() {
        return new PaymentCompletedEvent(
                100L,       // paymentId
                50L,        // subscriptionId
                1L,         // familyId
                new BigDecimal("150.00"),
                "EUR",
                "CARD",
                Instant.now());
    }

    @Test
    @DisplayName("should_call_generateInvoice_when_payment_completed_event_received")
    void should_call_generateInvoice_when_payment_completed_event_received() {
        // Given
        PaymentCompletedEvent event = buildEvent();
        when(invoiceService.generateInvoice(100L))
                .thenReturn(InvoiceResponse.builder()
                        .id(10L)
                        .invoiceNumber("FHM-2026-000001")
                        .build());

        // When
        consumer.onPaymentCompleted(event);

        // Then
        verify(invoiceService).generateInvoice(100L);
    }

    @Test
    @DisplayName("should_not_throw_when_duplicate_invoice_detected")
    void should_not_throw_when_duplicate_invoice_detected() {
        // Given
        PaymentCompletedEvent event = buildEvent();
        doThrow(new IllegalStateException(
                "Facture deja generee pour le paiement 100"))
                .when(invoiceService).generateInvoice(100L);

        // When -- should not throw (handled gracefully)
        consumer.onPaymentCompleted(event);

        // Then
        verify(invoiceService).generateInvoice(100L);
    }

    @Test
    @DisplayName("should_propagate_exception_when_unexpected_error_occurs")
    void should_propagate_exception_when_unexpected_error_occurs() {
        // Given
        PaymentCompletedEvent event = buildEvent();
        doThrow(new RuntimeException("Database connection lost"))
                .when(invoiceService).generateInvoice(100L);

        // When / Then
        assertThatThrownBy(() -> consumer.onPaymentCompleted(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database connection lost");
    }
}
```

---

## Test 5: InvoiceControllerTest

- **What**: WebMvcTest integration tests for InvoiceController
- **Where**: `backend/payment-service/src/test/java/com/familyhobbies/paymentservice/controller/InvoiceControllerTest.java`
- **Why**: Validates HTTP status codes, content types, and response shapes

```java
package com.familyhobbies.paymentservice.controller;

import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.paymentservice.dto.response.InvoiceResponse;
import com.familyhobbies.paymentservice.dto.response.InvoiceSummaryResponse;
import com.familyhobbies.paymentservice.dto.response.LineItemResponse;
import com.familyhobbies.paymentservice.service.InvoiceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvcTest for {@link InvoiceController}.
 */
@WebMvcTest(InvoiceController.class)
class InvoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InvoiceService invoiceService;

    private static final Long USER_ID = 42L;
    private static final String X_USER_ID = "X-User-Id";

    @Test
    @DisplayName("should_return_200_with_invoice_when_GET_by_id")
    void should_return_200_with_invoice_when_GET_by_id() throws Exception {
        // Given
        InvoiceResponse response = InvoiceResponse.builder()
                .id(10L)
                .invoiceNumber("FHM-2026-000001")
                .paymentId(100L)
                .subscriptionId(50L)
                .familyId(1L)
                .familyName("Famille Dupont")
                .associationName("Judo Club Lyon")
                .activityName("Judo - Cours enfants")
                .familyMemberName("Lucas Dupont")
                .season("2025-2026")
                .lineItems(List.of(LineItemResponse.builder()
                        .description("Judo - Cours enfants - Judo Club Lyon")
                        .amount(new BigDecimal("150.00"))
                        .build()))
                .subtotal(new BigDecimal("150.00"))
                .tax(BigDecimal.ZERO)
                .total(new BigDecimal("150.00"))
                .currency("EUR")
                .status("ISSUED")
                .issuedAt(Instant.now())
                .paidAt(Instant.now())
                .payerEmail("dupont@example.com")
                .payerName("Famille Dupont")
                .downloadUrl("/api/v1/invoices/10/download")
                .createdAt(Instant.now())
                .build();
        when(invoiceService.findById(10L)).thenReturn(response);

        // When / Then
        mockMvc.perform(get("/api/v1/invoices/10")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber",
                        is("FHM-2026-000001")))
                .andExpect(jsonPath("$.familyName",
                        is("Famille Dupont")))
                .andExpect(jsonPath("$.lineItems", hasSize(1)))
                .andExpect(jsonPath("$.total", is(150.00)))
                .andExpect(jsonPath("$.currency", is("EUR")));
    }

    @Test
    @DisplayName("should_return_404_when_GET_invoice_not_found")
    void should_return_404_when_GET_invoice_not_found() throws Exception {
        // Given
        when(invoiceService.findById(999L))
                .thenThrow(new ResourceNotFoundException(
                        "Facture non trouvee: 999"));

        // When / Then
        mockMvc.perform(get("/api/v1/invoices/999")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_return_200_with_paginated_summaries_when_GET_family_invoices")
    void should_return_200_with_paginated_summaries_when_GET_family_invoices()
            throws Exception {
        // Given
        List<InvoiceSummaryResponse> summaries = List.of(
                InvoiceSummaryResponse.builder()
                        .id(10L)
                        .invoiceNumber("FHM-2026-000001")
                        .associationName("Judo Club Lyon")
                        .activityName("Judo - Cours enfants")
                        .familyMemberName("Lucas Dupont")
                        .season("2025-2026")
                        .total(new BigDecimal("150.00"))
                        .currency("EUR")
                        .status("ISSUED")
                        .issuedAt(Instant.now())
                        .downloadUrl("/api/v1/invoices/10/download")
                        .build());
        Page<InvoiceSummaryResponse> page =
                new PageImpl<>(summaries, PageRequest.of(0, 20), 1);
        when(invoiceService.findByFamilyId(
                eq(1L), any(), any(), any(), any(), any()))
                .thenReturn(page);

        // When / Then
        mockMvc.perform(get("/api/v1/invoices/family/1")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].invoiceNumber",
                        is("FHM-2026-000001")))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("should_return_200_with_pdf_when_GET_download")
    void should_return_200_with_pdf_when_GET_download() throws Exception {
        // Given
        byte[] pdfContent = "%PDF-1.4 fake content".getBytes();
        when(invoiceService.downloadInvoice(10L)).thenReturn(pdfContent);

        // When / Then
        mockMvc.perform(get("/api/v1/invoices/10/download")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"facture-10.pdf\""));
    }

    @Test
    @DisplayName("should_return_404_when_GET_download_pdf_not_found")
    void should_return_404_when_GET_download_pdf_not_found() throws Exception {
        // Given
        when(invoiceService.downloadInvoice(999L))
                .thenThrow(new ResourceNotFoundException(
                        "Facture non trouvee: 999"));

        // When / Then
        mockMvc.perform(get("/api/v1/invoices/999/download")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isNotFound());
    }
}
```

---

## Test Summary

| Test Class | Test Count | Category |
|-----------|-----------|----------|
| InvoiceMapperTest | 4 | Unit -- mapper logic |
| InvoiceNumberGeneratorTest | 3 | Unit -- number generation |
| InvoiceServiceImplTest | 8 | Unit -- service business logic |
| PaymentCompletedEventConsumerTest | 3 | Unit -- Kafka consumer |
| InvoiceControllerTest | 5 | Integration -- WebMvcTest |
| **Total** | **23** | |
