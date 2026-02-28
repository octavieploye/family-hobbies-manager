package com.familyhobbies.paymentservice.batch.writer;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import com.familyhobbies.paymentservice.event.PaymentEventPublisher;
import com.familyhobbies.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationWriterTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    private PaymentReconciliationWriter writer;

    @BeforeEach
    void setUp() {
        writer = new PaymentReconciliationWriter(paymentRepository, paymentEventPublisher);
    }

    @Test
    @DisplayName("Should save payment and publish completed event for COMPLETED status")
    void shouldPublishCompletedEvent() throws Exception {
        Payment payment = buildPayment(PaymentStatus.COMPLETED);
        payment.setPaidAt(OffsetDateTime.now());

        writer.write(new Chunk<>(payment));

        verify(paymentRepository).save(payment);
        verify(paymentEventPublisher).publishPaymentCompleted(payment);
        verify(paymentEventPublisher, never()).publishPaymentFailed(any(Payment.class));
    }

    @Test
    @DisplayName("Should save payment and publish failed event for FAILED status")
    void shouldPublishFailedEvent() throws Exception {
        Payment payment = buildPayment(PaymentStatus.FAILED);
        payment.setFailedAt(OffsetDateTime.now());

        writer.write(new Chunk<>(payment));

        verify(paymentRepository).save(payment);
        verify(paymentEventPublisher).publishPaymentFailed(payment);
        verify(paymentEventPublisher, never()).publishPaymentCompleted(any(Payment.class));
    }

    @Test
    @DisplayName("Should save payment but not publish event for REFUNDED status")
    void shouldNotPublishEventForRefunded() throws Exception {
        Payment payment = buildPayment(PaymentStatus.REFUNDED);

        writer.write(new Chunk<>(payment));

        verify(paymentRepository).save(payment);
        verify(paymentEventPublisher, never()).publishPaymentCompleted(any(Payment.class));
        verify(paymentEventPublisher, never()).publishPaymentFailed(any(Payment.class));
    }

    private Payment buildPayment(PaymentStatus status) {
        return Payment.builder()
                .id(1L)
                .familyId(100L)
                .subscriptionId(200L)
                .amount(BigDecimal.valueOf(50))
                .status(status)
                .helloassoCheckoutId("checkout-1")
                .build();
    }
}
