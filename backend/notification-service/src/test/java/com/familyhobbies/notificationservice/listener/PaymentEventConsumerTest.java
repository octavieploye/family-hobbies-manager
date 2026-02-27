package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import com.familyhobbies.notificationservice.entity.enums.NotificationType;
import com.familyhobbies.notificationservice.service.EmailService;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PaymentEventConsumer.
 *
 * Story: S6-002 -- Kafka Consumers
 * Tests: 4 test methods
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private NotificationCreationService notificationCreationService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PaymentEventConsumer paymentEventConsumer;

    @Test
    @DisplayName("should create notification when payment completed")
    void should_createNotification_when_paymentCompleted() {
        // given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                1L, 10L, 100L, new BigDecimal("50.00"), "EUR", "CB", Instant.now());
        when(notificationCreationService.createNotification(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Notification.builder().id(1L).build());

        // when
        paymentEventConsumer.handlePaymentCompleted(event);

        // then
        verify(notificationCreationService).createNotification(
                eq(100L),
                eq(NotificationType.IN_APP),
                eq(NotificationCategory.PAYMENT),
                eq("Paiement confirme"),
                anyString(),
                eq("1"),
                eq("PAYMENT")
        );
    }

    @Test
    @DisplayName("should send email when payment completed")
    void should_sendEmail_when_paymentCompleted() {
        // given
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                1L, 10L, 100L, new BigDecimal("50.00"), "EUR", "CB", Instant.now());
        when(notificationCreationService.createNotification(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Notification.builder().id(1L).build());

        // when
        paymentEventConsumer.handlePaymentCompleted(event);

        // then
        verify(emailService).sendTemplatedEmail(
                isNull(),
                eq("PAYMENT_COMPLETED"),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("should create notification when payment failed")
    void should_createNotification_when_paymentFailed() {
        // given
        PaymentFailedEvent event = new PaymentFailedEvent(
                2L, 10L, 100L, "Insufficient funds", Instant.now());
        when(notificationCreationService.createNotification(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Notification.builder().id(2L).build());

        // when
        paymentEventConsumer.handlePaymentFailed(event);

        // then
        verify(notificationCreationService).createNotification(
                eq(100L),
                eq(NotificationType.IN_APP),
                eq(NotificationCategory.PAYMENT),
                eq("Echec de paiement"),
                anyString(),
                eq("2"),
                eq("PAYMENT")
        );
    }

    @Test
    @DisplayName("should send email when payment failed")
    void should_sendEmail_when_paymentFailed() {
        // given
        PaymentFailedEvent event = new PaymentFailedEvent(
                2L, 10L, 100L, "Insufficient funds", Instant.now());
        when(notificationCreationService.createNotification(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Notification.builder().id(2L).build());

        // when
        paymentEventConsumer.handlePaymentFailed(event);

        // then
        verify(emailService).sendTemplatedEmail(
                isNull(),
                eq("PAYMENT_FAILED"),
                any(Map.class)
        );
    }
}
