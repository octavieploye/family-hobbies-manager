package com.familyhobbies.paymentservice.listener;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.InvoiceStatus;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import com.familyhobbies.paymentservice.service.InvoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PaymentCompletedEventConsumer.
 *
 * Story: S6-006 -- Kafka Invoice Creation Consumer
 * Tests: 4 test methods
 *
 * Verifies that the consumer correctly loads payments and creates invoices,
 * and handles error cases gracefully (non-fatal).
 */
@ExtendWith(MockitoExtension.class)
class PaymentCompletedEventConsumerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceService invoiceService;

    @InjectMocks
    private PaymentCompletedEventConsumer consumer;

    private PaymentCompletedEvent testEvent;
    private Payment testPayment;
    private static final Long PAYMENT_ID = 10L;
    private static final Long FAMILY_ID = 1L;

    @BeforeEach
    void setUp() {
        testEvent = new PaymentCompletedEvent(
                PAYMENT_ID,
                200L,
                FAMILY_ID,
                new BigDecimal("75.00"),
                "EUR",
                "CARD",
                Instant.now()
        );

        testPayment = Payment.builder()
                .id(PAYMENT_ID)
                .familyId(FAMILY_ID)
                .subscriptionId(200L)
                .amount(new BigDecimal("75.00"))
                .currency("EUR")
                .status(PaymentStatus.COMPLETED)
                .description("Cotisation annuelle danse")
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    @Test
    @DisplayName("should_createInvoice_when_paymentCompletedEventReceived")
    void should_createInvoice_when_paymentCompletedEventReceived() {
        // Given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(testPayment));
        Invoice createdInvoice = Invoice.builder()
                .id(1L)
                .payment(testPayment)
                .invoiceNumber("FHM-2026-000001")
                .status(InvoiceStatus.ISSUED)
                .build();
        when(invoiceService.createInvoice(testPayment)).thenReturn(createdInvoice);

        // When
        consumer.onPaymentCompleted(testEvent);

        // Then
        verify(invoiceService).createInvoice(testPayment);
    }

    @Test
    @DisplayName("should_loadPayment_when_processingEvent")
    void should_loadPayment_when_processingEvent() {
        // Given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(testPayment));
        when(invoiceService.createInvoice(any(Payment.class))).thenReturn(
                Invoice.builder().id(1L).build());

        // When
        consumer.onPaymentCompleted(testEvent);

        // Then
        verify(paymentRepository).findById(PAYMENT_ID);
    }

    @Test
    @DisplayName("should_logError_when_paymentNotFound")
    void should_logError_when_paymentNotFound() {
        // Given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.empty());

        // When
        consumer.onPaymentCompleted(testEvent);

        // Then -- should not create invoice when payment not found
        verify(invoiceService, never()).createInvoice(any(Payment.class));
    }

    @Test
    @DisplayName("should_notThrow_when_invoiceCreationFails")
    void should_notThrow_when_invoiceCreationFails() {
        // Given
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(testPayment));
        when(invoiceService.createInvoice(testPayment))
                .thenThrow(new RuntimeException("Database error"));

        // When / Then -- should not propagate the exception
        assertThatCode(() -> consumer.onPaymentCompleted(testEvent))
                .doesNotThrowAnyException();
    }
}
