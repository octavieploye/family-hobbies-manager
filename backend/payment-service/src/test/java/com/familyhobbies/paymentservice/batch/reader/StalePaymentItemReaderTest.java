package com.familyhobbies.paymentservice.batch.reader;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StalePaymentItemReaderTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("Should return stale payments one by one")
    void shouldReturnStalePayments() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-28T10:00:00Z"), ZoneId.of("UTC"));
        StalePaymentItemReader reader = new StalePaymentItemReader(paymentRepository, fixedClock);

        Payment p1 = buildPayment(1L, "checkout-1");
        Payment p2 = buildPayment(2L, "checkout-2");

        when(paymentRepository.findByStatusAndCreatedAtBefore(
                eq(PaymentStatus.PENDING), any(OffsetDateTime.class)))
                .thenReturn(List.of(p1, p2));

        assertThat(reader.read()).isEqualTo(p1);
        assertThat(reader.read()).isEqualTo(p2);
        assertThat(reader.read()).isNull();
    }

    @Test
    @DisplayName("Should return null immediately when no stale payments")
    void shouldReturnNullWhenEmpty() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-28T10:00:00Z"), ZoneId.of("UTC"));
        StalePaymentItemReader reader = new StalePaymentItemReader(paymentRepository, fixedClock);

        when(paymentRepository.findByStatusAndCreatedAtBefore(
                eq(PaymentStatus.PENDING), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        assertThat(reader.read()).isNull();
    }

    @Test
    @DisplayName("Should only query database once (on first read)")
    void shouldQueryDatabaseOnce() throws Exception {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-02-28T10:00:00Z"), ZoneId.of("UTC"));
        StalePaymentItemReader reader = new StalePaymentItemReader(paymentRepository, fixedClock);

        Payment p1 = buildPayment(1L, "checkout-1");

        when(paymentRepository.findByStatusAndCreatedAtBefore(
                eq(PaymentStatus.PENDING), any(OffsetDateTime.class)))
                .thenReturn(List.of(p1));

        reader.read();
        reader.read(); // Should return null, not re-query

        org.mockito.Mockito.verify(paymentRepository, org.mockito.Mockito.times(1))
                .findByStatusAndCreatedAtBefore(any(), any());
    }

    private Payment buildPayment(Long id, String checkoutId) {
        return Payment.builder()
                .id(id)
                .familyId(100L)
                .subscriptionId(200L)
                .amount(BigDecimal.valueOf(50))
                .status(PaymentStatus.PENDING)
                .helloassoCheckoutId(checkoutId)
                .build();
    }
}
