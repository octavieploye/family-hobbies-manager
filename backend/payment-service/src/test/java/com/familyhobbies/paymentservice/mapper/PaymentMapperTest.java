package com.familyhobbies.paymentservice.mapper;

import com.familyhobbies.paymentservice.dto.request.CheckoutRequest;
import com.familyhobbies.paymentservice.dto.response.CheckoutResponse;
import com.familyhobbies.paymentservice.dto.response.PaymentResponse;
import com.familyhobbies.paymentservice.entity.Invoice;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.InvoiceStatus;
import com.familyhobbies.paymentservice.entity.enums.PaymentMethod;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for PaymentMapper.
 *
 * Story: S5-004 / S5-005 -- Payment Mapping
 * Tests: 5 test methods
 */
class PaymentMapperTest {

    private PaymentMapper paymentMapper;

    @BeforeEach
    void setUp() {
        paymentMapper = new PaymentMapper();
    }

    @Test
    @DisplayName("should_mapToPayment_when_validCheckoutRequest")
    void should_mapToPayment_when_validCheckoutRequest() {
        // Given
        CheckoutRequest request = new CheckoutRequest(
                100L,
                new BigDecimal("75.50"),
                "Cotisation danse",
                "SUBSCRIPTION",
                "https://return.url",
                "https://cancel.url"
        );

        // When
        Payment payment = paymentMapper.fromCheckoutRequest(request, 1L);

        // Then
        assertThat(payment).isNotNull();
        assertThat(payment.getFamilyId()).isEqualTo(1L);
        assertThat(payment.getSubscriptionId()).isEqualTo(100L);
        assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("75.50"));
        assertThat(payment.getCurrency()).isEqualTo("EUR");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getDescription()).isEqualTo("Cotisation danse");
        assertThat(payment.getPaymentType()).isEqualTo("SUBSCRIPTION");
    }

    @Test
    @DisplayName("should_mapToCheckoutResponse_when_validPaymentAndUrl")
    void should_mapToCheckoutResponse_when_validPaymentAndUrl() {
        // Given
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Payment payment = Payment.builder()
                .id(10L)
                .subscriptionId(100L)
                .amount(new BigDecimal("50.00"))
                .status(PaymentStatus.PENDING)
                .helloassoCheckoutId("ha-checkout-789")
                .createdAt(now)
                .build();

        // When
        CheckoutResponse response = paymentMapper.toCheckoutResponse(
                payment, "https://checkout.helloasso.com/pay/789");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.paymentId()).isEqualTo(10L);
        assertThat(response.subscriptionId()).isEqualTo(100L);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.helloasso.com/pay/789");
        assertThat(response.helloassoCheckoutId()).isEqualTo("ha-checkout-789");
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("should_mapToPaymentResponse_when_paymentWithInvoice")
    void should_mapToPaymentResponse_when_paymentWithInvoice() {
        // Given
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Payment payment = Payment.builder()
                .id(10L)
                .familyId(1L)
                .subscriptionId(100L)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .status(PaymentStatus.COMPLETED)
                .paymentMethod(PaymentMethod.CARD)
                .paidAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Invoice invoice = Invoice.builder()
                .id(5L)
                .payment(payment)
                .invoiceNumber("INV-2026-001")
                .status(InvoiceStatus.ISSUED)
                .build();

        // When
        PaymentResponse response = paymentMapper.toPaymentResponse(payment, invoice);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.familyId()).isEqualTo(1L);
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.paymentMethod()).isEqualTo("CARD");
        assertThat(response.invoiceId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("should_mapToPaymentResponse_when_paymentWithoutInvoice")
    void should_mapToPaymentResponse_when_paymentWithoutInvoice() {
        // Given
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Payment payment = Payment.builder()
                .id(10L)
                .familyId(1L)
                .subscriptionId(100L)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .status(PaymentStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // When
        PaymentResponse response = paymentMapper.toPaymentResponse(payment, null);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.invoiceId()).isNull();
        assertThat(response.paymentMethod()).isNull();
    }

    @Test
    @DisplayName("should_handleNullFields_when_mappingPayment")
    void should_handleNullFields_when_mappingPayment() {
        // Given -- null request returns null
        Payment paymentFromNull = paymentMapper.fromCheckoutRequest(null, 1L);
        assertThat(paymentFromNull).isNull();

        // Given -- null payment returns null
        CheckoutResponse checkoutFromNull = paymentMapper.toCheckoutResponse(null, "https://url");
        assertThat(checkoutFromNull).isNull();

        PaymentResponse responseFromNull = paymentMapper.toPaymentResponse(null, null);
        assertThat(responseFromNull).isNull();
    }
}
