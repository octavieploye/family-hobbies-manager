package com.familyhobbies.paymentservice.batch.processor;

import com.familyhobbies.errorhandling.exception.container.ExternalApiException;
import com.familyhobbies.paymentservice.adapter.HelloAssoCheckoutClient;
import com.familyhobbies.paymentservice.dto.helloasso.HelloAssoCheckoutStatusResponse;
import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationProcessorTest {

    @Mock
    private HelloAssoCheckoutClient helloAssoCheckoutClient;

    private PaymentReconciliationProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PaymentReconciliationProcessor(helloAssoCheckoutClient);
    }

    @Test
    @DisplayName("Should set COMPLETED when HelloAsso returns Authorized")
    void shouldSetCompletedForAuthorized() throws Exception {
        Payment payment = buildPayment("checkout-1");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                1L, "Authorized", BigDecimal.valueOf(50), OffsetDateTime.now());

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-1")).thenReturn(response);

        Payment result = processor.process(payment);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("Should set COMPLETED when HelloAsso returns Registered")
    void shouldSetCompletedForRegistered() throws Exception {
        Payment payment = buildPayment("checkout-2");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                2L, "Registered", BigDecimal.valueOf(50), OffsetDateTime.now());

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-2")).thenReturn(response);

        Payment result = processor.process(payment);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should set FAILED when HelloAsso returns Refused")
    void shouldSetFailedForRefused() throws Exception {
        Payment payment = buildPayment("checkout-3");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                3L, "Refused", BigDecimal.valueOf(50), null);

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-3")).thenReturn(response);

        Payment result = processor.process(payment);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.getFailedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should set REFUNDED when HelloAsso returns Refunded")
    void shouldSetRefundedForRefunded() throws Exception {
        Payment payment = buildPayment("checkout-4");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                4L, "Refunded", BigDecimal.valueOf(50), null);

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-4")).thenReturn(response);

        Payment result = processor.process(payment);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(result.getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should return null (skip) when HelloAsso returns Pending")
    void shouldReturnNullForPending() throws Exception {
        Payment payment = buildPayment("checkout-5");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                5L, "Pending", BigDecimal.valueOf(50), null);

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-5")).thenReturn(response);

        Payment result = processor.process(payment);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null (skip) for unknown HelloAsso state")
    void shouldReturnNullForUnknownState() throws Exception {
        Payment payment = buildPayment("checkout-6");
        HelloAssoCheckoutStatusResponse response = new HelloAssoCheckoutStatusResponse(
                6L, "Unknown", BigDecimal.valueOf(50), null);

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-6")).thenReturn(response);

        Payment result = processor.process(payment);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should propagate ExternalApiException when HelloAsso API is unavailable")
    void shouldPropagateExternalApiException() {
        Payment payment = buildPayment("checkout-7");

        when(helloAssoCheckoutClient.getCheckoutStatus("checkout-7"))
                .thenThrow(new ExternalApiException("API down", "HelloAsso", 503));

        assertThatThrownBy(() -> processor.process(payment))
                .isInstanceOf(ExternalApiException.class);
    }

    private Payment buildPayment(String checkoutId) {
        return Payment.builder()
                .id(1L)
                .familyId(100L)
                .subscriptionId(200L)
                .amount(BigDecimal.valueOf(50))
                .status(PaymentStatus.PENDING)
                .helloassoCheckoutId(checkoutId)
                .build();
    }
}
