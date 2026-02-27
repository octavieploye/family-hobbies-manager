package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.UserRegisteredEvent;
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

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for UserEventConsumer.
 *
 * Story: S6-002 -- Kafka Consumers
 * Tests: 3 test methods
 */
@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock
    private NotificationCreationService notificationCreationService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserEventConsumer userEventConsumer;

    @Test
    @DisplayName("should create welcome notification when user registered")
    void should_createWelcomeNotification_when_userRegistered() {
        // given
        UserRegisteredEvent event = new UserRegisteredEvent(1L, "jean@email.com", "Jean", "Dupont");
        when(notificationCreationService.createNotification(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Notification.builder().id(1L).build());

        // when
        userEventConsumer.handleUserRegistered(event);

        // then
        verify(notificationCreationService).createNotification(
                eq(1L),
                eq(NotificationType.IN_APP),
                eq(NotificationCategory.WELCOME),
                eq("Bienvenue sur Family Hobbies Manager !"),
                anyString(),
                eq("1"),
                eq("USER")
        );
    }

    @Test
    @DisplayName("should send welcome email when user registered")
    void should_sendWelcomeEmail_when_userRegistered() {
        // given
        UserRegisteredEvent event = new UserRegisteredEvent(1L, "jean@email.com", "Jean", "Dupont");
        when(notificationCreationService.createNotification(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Notification.builder().id(1L).build());

        // when
        userEventConsumer.handleUserRegistered(event);

        // then
        verify(emailService).sendTemplatedEmail(
                eq("jean@email.com"),
                eq("WELCOME"),
                any(Map.class)
        );
    }

    @Test
    @DisplayName("should log error when email fails")
    void should_logError_when_emailFails() {
        // given
        UserRegisteredEvent event = new UserRegisteredEvent(1L, "jean@email.com", "Jean", "Dupont");
        when(notificationCreationService.createNotification(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Notification.builder().id(1L).build());
        doThrow(new RuntimeException("SMTP connection failed"))
                .when(emailService).sendTemplatedEmail(anyString(), anyString(), any(Map.class));

        // when -- should not throw, error is caught and logged
        userEventConsumer.handleUserRegistered(event);

        // then -- notification was still created
        verify(notificationCreationService).createNotification(
                eq(1L),
                eq(NotificationType.IN_APP),
                eq(NotificationCategory.WELCOME),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        );
    }
}
