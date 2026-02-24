# Story S6-002: Kafka Consumers -- Failing Tests (TDD Contract)

> Companion file for [S6-002: Implement Kafka Consumers](./S6-002-kafka-consumers.md)
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Test 1: UserEventConsumerTest

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/listener/UserEventConsumerTest.java`

```java
package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.UserDeletedEvent;
import com.familyhobbies.common.event.UserRegisteredEvent;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.repository.NotificationPreferenceRepository;
import com.familyhobbies.notificationservice.repository.NotificationRepository;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link UserEventConsumer}.
 * Uses Mockito to verify interactions with NotificationCreationService
 * and repositories without requiring Kafka infrastructure.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserEventConsumer")
class UserEventConsumerTest {

    @Mock
    private NotificationCreationService notificationCreationService;

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @InjectMocks
    private UserEventConsumer userEventConsumer;

    @Captor
    private ArgumentCaptor<Long> userIdCaptor;

    @Captor
    private ArgumentCaptor<String> emailCaptor;

    @Captor
    private ArgumentCaptor<NotificationCategory> categoryCaptor;

    @Captor
    private ArgumentCaptor<String> titleCaptor;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> variablesCaptor;

    @Nested
    @DisplayName("handleUserRegistered")
    class HandleUserRegistered {

        @Test
        @DisplayName("should_create_welcome_notification_when_user_registered")
        void should_create_welcome_notification_when_user_registered() {
            // Given
            UserRegisteredEvent event = new UserRegisteredEvent();
            event.setUserId(1L);
            event.setEmail("marie.dupont@example.fr");
            event.setFirstName("Marie");
            event.setLastName("Dupont");
            event.setRole("FAMILY");
            event.setRegisteredAt(Instant.now());

            // When
            userEventConsumer.handleUserRegistered(event);

            // Then
            verify(notificationCreationService).createNotification(
                    userIdCaptor.capture(),
                    emailCaptor.capture(),
                    categoryCaptor.capture(),
                    titleCaptor.capture(),
                    messageCaptor.capture(),
                    variablesCaptor.capture()
            );

            assertThat(userIdCaptor.getValue()).isEqualTo(1L);
            assertThat(emailCaptor.getValue()).isEqualTo("marie.dupont@example.fr");
            assertThat(categoryCaptor.getValue()).isEqualTo(NotificationCategory.WELCOME);
            assertThat(titleCaptor.getValue()).contains("Bienvenue");
            assertThat(messageCaptor.getValue()).contains("Marie");
        }

        @Test
        @DisplayName("should_pass_template_variables_when_user_registered")
        void should_pass_template_variables_when_user_registered() {
            // Given
            UserRegisteredEvent event = new UserRegisteredEvent();
            event.setUserId(2L);
            event.setEmail("pierre.martin@example.fr");
            event.setFirstName("Pierre");
            event.setLastName("Martin");
            event.setRole("FAMILY");
            event.setRegisteredAt(Instant.now());

            // When
            userEventConsumer.handleUserRegistered(event);

            // Then
            verify(notificationCreationService).createNotification(
                    any(), any(), any(), any(), any(), variablesCaptor.capture());

            Map<String, Object> variables = variablesCaptor.getValue();
            assertThat(variables).containsEntry("firstName", "Pierre");
            assertThat(variables).containsEntry("lastName", "Martin");
            assertThat(variables).containsEntry("email", "pierre.martin@example.fr");
        }
    }

    @Nested
    @DisplayName("handleUserDeleted")
    class HandleUserDeleted {

        @Test
        @DisplayName("should_delete_all_notifications_when_user_deleted")
        void should_delete_all_notifications_when_user_deleted() {
            // Given
            UserDeletedEvent event = new UserDeletedEvent();
            event.setUserId(1L);
            event.setEmail("marie.dupont@example.fr");
            event.setDeletedAt(Instant.now());
            event.setReason("User requested account deletion");

            // When
            userEventConsumer.handleUserDeleted(event);

            // Then
            verify(notificationRepository).deleteByUserId(1L);
        }

        @Test
        @DisplayName("should_delete_all_preferences_when_user_deleted")
        void should_delete_all_preferences_when_user_deleted() {
            // Given
            UserDeletedEvent event = new UserDeletedEvent();
            event.setUserId(1L);
            event.setEmail("marie.dupont@example.fr");
            event.setDeletedAt(Instant.now());
            event.setReason("RGPD request");

            // When
            userEventConsumer.handleUserDeleted(event);

            // Then
            verify(preferenceRepository).deleteByUserId(1L);
            verifyNoInteractions(notificationCreationService);
        }
    }
}
```

---

## Test 2: SubscriptionEventConsumerTest

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/listener/SubscriptionEventConsumerTest.java`

```java
package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.SubscriptionCancelledEvent;
import com.familyhobbies.common.event.SubscriptionCreatedEvent;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link SubscriptionEventConsumer}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionEventConsumer")
class SubscriptionEventConsumerTest {

    @Mock
    private NotificationCreationService notificationCreationService;

    @InjectMocks
    private SubscriptionEventConsumer subscriptionEventConsumer;

    @Captor
    private ArgumentCaptor<Long> userIdCaptor;

    @Captor
    private ArgumentCaptor<NotificationCategory> categoryCaptor;

    @Captor
    private ArgumentCaptor<String> titleCaptor;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> variablesCaptor;

    @Nested
    @DisplayName("handleSubscriptionCreated")
    class HandleSubscriptionCreated {

        @Test
        @DisplayName("should_create_subscription_confirmed_notification_when_subscription_created")
        void should_create_subscription_confirmed_notification_when_subscription_created() {
            // Given
            SubscriptionCreatedEvent event = new SubscriptionCreatedEvent();
            event.setSubscriptionId(10L);
            event.setFamilyMemberId(5L);
            event.setFamilyId(1L);
            event.setAssociationId(20L);
            event.setActivityId(30L);
            event.setSeason("2025-2026");
            event.setAmount(new BigDecimal("150.00"));
            event.setCreatedAt(Instant.now());

            // When
            subscriptionEventConsumer.handleSubscriptionCreated(event);

            // Then
            verify(notificationCreationService).createNotification(
                    userIdCaptor.capture(),
                    any(),
                    categoryCaptor.capture(),
                    titleCaptor.capture(),
                    messageCaptor.capture(),
                    variablesCaptor.capture()
            );

            assertThat(userIdCaptor.getValue()).isEqualTo(1L);
            assertThat(categoryCaptor.getValue())
                    .isEqualTo(NotificationCategory.SUBSCRIPTION_CONFIRMED);
            assertThat(titleCaptor.getValue()).contains("Inscription");
            assertThat(messageCaptor.getValue()).contains("2025-2026");
            assertThat(messageCaptor.getValue()).contains("150.00");
        }

        @Test
        @DisplayName("should_pass_subscription_variables_when_subscription_created")
        void should_pass_subscription_variables_when_subscription_created() {
            // Given
            SubscriptionCreatedEvent event = new SubscriptionCreatedEvent();
            event.setSubscriptionId(10L);
            event.setFamilyMemberId(5L);
            event.setFamilyId(1L);
            event.setAssociationId(20L);
            event.setActivityId(30L);
            event.setSeason("2025-2026");
            event.setAmount(new BigDecimal("150.00"));
            event.setCreatedAt(Instant.now());

            // When
            subscriptionEventConsumer.handleSubscriptionCreated(event);

            // Then
            verify(notificationCreationService).createNotification(
                    any(), any(), any(), any(), any(), variablesCaptor.capture());

            Map<String, Object> variables = variablesCaptor.getValue();
            assertThat(variables).containsEntry("subscriptionId", 10L);
            assertThat(variables).containsEntry("activityId", 30L);
            assertThat(variables).containsEntry("season", "2025-2026");
            assertThat(variables).containsEntry("amount", new BigDecimal("150.00"));
        }
    }

    @Nested
    @DisplayName("handleSubscriptionCancelled")
    class HandleSubscriptionCancelled {

        @Test
        @DisplayName("should_create_subscription_cancelled_notification_when_subscription_cancelled")
        void should_create_subscription_cancelled_notification_when_subscription_cancelled() {
            // Given
            SubscriptionCancelledEvent event = new SubscriptionCancelledEvent();
            event.setSubscriptionId(10L);
            event.setFamilyMemberId(5L);
            event.setFamilyId(1L);
            event.setReason("Demenagement");
            event.setCancelledAt(Instant.now());

            // When
            subscriptionEventConsumer.handleSubscriptionCancelled(event);

            // Then
            verify(notificationCreationService).createNotification(
                    userIdCaptor.capture(),
                    any(),
                    categoryCaptor.capture(),
                    titleCaptor.capture(),
                    messageCaptor.capture(),
                    variablesCaptor.capture()
            );

            assertThat(userIdCaptor.getValue()).isEqualTo(1L);
            assertThat(categoryCaptor.getValue())
                    .isEqualTo(NotificationCategory.SUBSCRIPTION_CANCELLED);
            assertThat(titleCaptor.getValue()).contains("annulee");
            assertThat(messageCaptor.getValue()).contains("Demenagement");
        }
    }
}
```

---

## Test 3: PaymentEventConsumerTest

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/listener/PaymentEventConsumerTest.java`

```java
package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.PaymentCompletedEvent;
import com.familyhobbies.common.event.PaymentFailedEvent;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link PaymentEventConsumer}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventConsumer")
class PaymentEventConsumerTest {

    @Mock
    private NotificationCreationService notificationCreationService;

    @InjectMocks
    private PaymentEventConsumer paymentEventConsumer;

    @Captor
    private ArgumentCaptor<Long> userIdCaptor;

    @Captor
    private ArgumentCaptor<NotificationCategory> categoryCaptor;

    @Captor
    private ArgumentCaptor<String> titleCaptor;

    @Captor
    private ArgumentCaptor<String> messageCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> variablesCaptor;

    @Nested
    @DisplayName("handlePaymentCompleted")
    class HandlePaymentCompleted {

        @Test
        @DisplayName("should_create_payment_success_notification_when_payment_completed")
        void should_create_payment_success_notification_when_payment_completed() {
            // Given
            PaymentCompletedEvent event = new PaymentCompletedEvent();
            event.setPaymentId(100L);
            event.setSubscriptionId(10L);
            event.setFamilyId(1L);
            event.setAmount(new BigDecimal("75.50"));
            event.setCurrency("EUR");
            event.setPaymentMethod("CARD");
            event.setPaidAt(Instant.now());

            // When
            paymentEventConsumer.handlePaymentCompleted(event);

            // Then
            verify(notificationCreationService).createNotification(
                    userIdCaptor.capture(),
                    any(),
                    categoryCaptor.capture(),
                    titleCaptor.capture(),
                    messageCaptor.capture(),
                    variablesCaptor.capture()
            );

            assertThat(userIdCaptor.getValue()).isEqualTo(1L);
            assertThat(categoryCaptor.getValue())
                    .isEqualTo(NotificationCategory.PAYMENT_SUCCESS);
            assertThat(titleCaptor.getValue()).contains("Paiement");
            assertThat(messageCaptor.getValue()).contains("75.50");
            assertThat(messageCaptor.getValue()).contains("EUR");
        }

        @Test
        @DisplayName("should_pass_payment_variables_when_payment_completed")
        void should_pass_payment_variables_when_payment_completed() {
            // Given
            PaymentCompletedEvent event = new PaymentCompletedEvent();
            event.setPaymentId(100L);
            event.setSubscriptionId(10L);
            event.setFamilyId(1L);
            event.setAmount(new BigDecimal("75.50"));
            event.setCurrency("EUR");
            event.setPaymentMethod("CARD");
            event.setPaidAt(Instant.now());

            // When
            paymentEventConsumer.handlePaymentCompleted(event);

            // Then
            verify(notificationCreationService).createNotification(
                    any(), any(), any(), any(), any(), variablesCaptor.capture());

            Map<String, Object> variables = variablesCaptor.getValue();
            assertThat(variables).containsEntry("paymentId", 100L);
            assertThat(variables).containsEntry("amount", new BigDecimal("75.50"));
            assertThat(variables).containsEntry("currency", "EUR");
            assertThat(variables).containsEntry("paymentMethod", "CARD");
        }
    }

    @Nested
    @DisplayName("handlePaymentFailed")
    class HandlePaymentFailed {

        @Test
        @DisplayName("should_create_payment_failed_notification_when_payment_failed")
        void should_create_payment_failed_notification_when_payment_failed() {
            // Given
            PaymentFailedEvent event = new PaymentFailedEvent();
            event.setPaymentId(101L);
            event.setSubscriptionId(10L);
            event.setFamilyId(1L);
            event.setAmount(new BigDecimal("75.50"));
            event.setFailureReason("Carte refusee");
            event.setFailedAt(Instant.now());

            // When
            paymentEventConsumer.handlePaymentFailed(event);

            // Then
            verify(notificationCreationService).createNotification(
                    userIdCaptor.capture(),
                    any(),
                    categoryCaptor.capture(),
                    titleCaptor.capture(),
                    messageCaptor.capture(),
                    variablesCaptor.capture()
            );

            assertThat(userIdCaptor.getValue()).isEqualTo(1L);
            assertThat(categoryCaptor.getValue())
                    .isEqualTo(NotificationCategory.PAYMENT_FAILED);
            assertThat(titleCaptor.getValue()).contains("Echec");
            assertThat(messageCaptor.getValue()).contains("Carte refusee");
            assertThat(messageCaptor.getValue()).contains("reessayer");
        }
    }
}
```

---

## Test 4: AttendanceEventConsumerTest

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/listener/AttendanceEventConsumerTest.java`

```java
package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.AttendanceMarkedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link AttendanceEventConsumer}.
 * Verifies that the consumer handles events without errors.
 * Currently only logs -- no side effects to verify.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttendanceEventConsumer")
class AttendanceEventConsumerTest {

    @InjectMocks
    private AttendanceEventConsumer attendanceEventConsumer;

    @Test
    @DisplayName("should_not_throw_when_attendance_event_received")
    void should_not_throw_when_attendance_event_received() {
        // Given
        AttendanceMarkedEvent event = new AttendanceMarkedEvent();
        event.setAttendanceId(1L);
        event.setSubscriptionId(10L);
        event.setSessionId(20L);
        event.setFamilyMemberId(5L);
        event.setStatus("PRESENT");
        event.setSessionDate(LocalDate.of(2026, 3, 15));
        event.setMarkedAt(Instant.now());
        event.setMarkedBy(99L);

        // When / Then
        assertThatCode(() -> attendanceEventConsumer.handleAttendanceMarked(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should_not_throw_when_attendance_event_has_absent_status")
    void should_not_throw_when_attendance_event_has_absent_status() {
        // Given
        AttendanceMarkedEvent event = new AttendanceMarkedEvent();
        event.setAttendanceId(2L);
        event.setSubscriptionId(10L);
        event.setSessionId(21L);
        event.setFamilyMemberId(5L);
        event.setStatus("ABSENT");
        event.setSessionDate(LocalDate.of(2026, 3, 22));
        event.setMarkedAt(Instant.now());
        event.setMarkedBy(99L);

        // When / Then
        assertThatCode(() -> attendanceEventConsumer.handleAttendanceMarked(event))
                .doesNotThrowAnyException();
    }
}
```

---

## Test 5: NotificationCreationServiceImplTest

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/service/impl/NotificationCreationServiceImplTest.java`

```java
package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.enums.NotificationType;
import com.familyhobbies.notificationservice.repository.NotificationPreferenceRepository;
import com.familyhobbies.notificationservice.repository.NotificationRepository;
import com.familyhobbies.notificationservice.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationCreationServiceImpl}.
 * Verifies preference-aware notification creation and email triggering.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationCreationServiceImpl")
class NotificationCreationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationCreationServiceImpl notificationCreationService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private static final Long USER_ID = 1L;
    private static final String EMAIL = "marie.dupont@example.fr";
    private static final NotificationCategory CATEGORY = NotificationCategory.WELCOME;
    private static final String TITLE = "Bienvenue";
    private static final String MESSAGE = "Bienvenue sur Family Hobbies Manager !";
    private static final Map<String, Object> VARIABLES = Map.of("firstName", "Marie");

    @Nested
    @DisplayName("Default Preferences (no preference row)")
    class DefaultPreferences {

        @Test
        @DisplayName("should_create_in_app_notification_and_send_email_when_no_preference_exists")
        void should_create_in_app_notification_and_send_email_when_no_preference_exists() {
            // Given -- no preference row exists (defaults to both enabled)
            when(preferenceRepository.findByUserIdAndCategory(USER_ID, CATEGORY))
                    .thenReturn(Optional.empty());

            // When
            notificationCreationService.createNotification(
                    USER_ID, EMAIL, CATEGORY, TITLE, MESSAGE, VARIABLES);

            // Then
            verify(notificationRepository).save(notificationCaptor.capture());
            Notification saved = notificationCaptor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getType()).isEqualTo(NotificationType.IN_APP);
            assertThat(saved.getCategory()).isEqualTo(CATEGORY);
            assertThat(saved.getTitle()).isEqualTo(TITLE);
            assertThat(saved.getMessage()).isEqualTo(MESSAGE);

            verify(emailService).sendEmail(EMAIL, CATEGORY, VARIABLES);
        }
    }

    @Nested
    @DisplayName("Email Disabled")
    class EmailDisabled {

        @Test
        @DisplayName("should_create_in_app_notification_without_email_when_email_disabled")
        void should_create_in_app_notification_without_email_when_email_disabled() {
            // Given
            NotificationPreference pref = NotificationPreference.builder()
                    .userId(USER_ID)
                    .category(CATEGORY)
                    .emailEnabled(false)
                    .inAppEnabled(true)
                    .build();
            when(preferenceRepository.findByUserIdAndCategory(USER_ID, CATEGORY))
                    .thenReturn(Optional.of(pref));

            // When
            notificationCreationService.createNotification(
                    USER_ID, EMAIL, CATEGORY, TITLE, MESSAGE, VARIABLES);

            // Then
            verify(notificationRepository).save(notificationCaptor.capture());
            assertThat(notificationCaptor.getValue().getType())
                    .isEqualTo(NotificationType.IN_APP);

            verify(emailService, never()).sendEmail(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("In-App Disabled")
    class InAppDisabled {

        @Test
        @DisplayName("should_send_email_only_when_in_app_disabled_and_email_enabled")
        void should_send_email_only_when_in_app_disabled_and_email_enabled() {
            // Given
            NotificationPreference pref = NotificationPreference.builder()
                    .userId(USER_ID)
                    .category(CATEGORY)
                    .emailEnabled(true)
                    .inAppEnabled(false)
                    .build();
            when(preferenceRepository.findByUserIdAndCategory(USER_ID, CATEGORY))
                    .thenReturn(Optional.of(pref));

            // When
            notificationCreationService.createNotification(
                    USER_ID, EMAIL, CATEGORY, TITLE, MESSAGE, VARIABLES);

            // Then
            verify(emailService).sendEmail(EMAIL, CATEGORY, VARIABLES);
            verify(notificationRepository).save(notificationCaptor.capture());
            assertThat(notificationCaptor.getValue().getType())
                    .isEqualTo(NotificationType.EMAIL);
            assertThat(notificationCaptor.getValue().getEmailSent()).isTrue();
        }
    }

    @Nested
    @DisplayName("Both Channels Disabled")
    class BothDisabled {

        @Test
        @DisplayName("should_skip_notification_and_email_when_both_channels_disabled")
        void should_skip_notification_and_email_when_both_channels_disabled() {
            // Given
            NotificationPreference pref = NotificationPreference.builder()
                    .userId(USER_ID)
                    .category(CATEGORY)
                    .emailEnabled(false)
                    .inAppEnabled(false)
                    .build();
            when(preferenceRepository.findByUserIdAndCategory(USER_ID, CATEGORY))
                    .thenReturn(Optional.of(pref));

            // When
            notificationCreationService.createNotification(
                    USER_ID, EMAIL, CATEGORY, TITLE, MESSAGE, VARIABLES);

            // Then
            verify(notificationRepository, never()).save(any());
            verify(emailService, never()).sendEmail(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Email Failure Handling")
    class EmailFailure {

        @Test
        @DisplayName("should_still_create_in_app_notification_when_email_sending_fails")
        void should_still_create_in_app_notification_when_email_sending_fails() {
            // Given -- no preference row (defaults to both enabled)
            when(preferenceRepository.findByUserIdAndCategory(USER_ID, CATEGORY))
                    .thenReturn(Optional.empty());
            doThrow(new RuntimeException("SMTP connection refused"))
                    .when(emailService).sendEmail(any(), any(), any());

            // When
            notificationCreationService.createNotification(
                    USER_ID, EMAIL, CATEGORY, TITLE, MESSAGE, VARIABLES);

            // Then -- notification still created despite email failure
            verify(notificationRepository).save(notificationCaptor.capture());
            Notification saved = notificationCaptor.getValue();
            assertThat(saved.getType()).isEqualTo(NotificationType.IN_APP);
            assertThat(saved.getEmailSent()).isFalse();
        }

        @Test
        @DisplayName("should_not_send_email_when_email_address_is_null")
        void should_not_send_email_when_email_address_is_null() {
            // Given
            when(preferenceRepository.findByUserIdAndCategory(USER_ID, CATEGORY))
                    .thenReturn(Optional.empty());

            // When
            notificationCreationService.createNotification(
                    USER_ID, null, CATEGORY, TITLE, MESSAGE, VARIABLES);

            // Then
            verify(notificationRepository).save(notificationCaptor.capture());
            verify(emailService, never()).sendEmail(any(), any(), any());
        }
    }
}
```

---

## Test 6: EmailServiceImplTest

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/service/impl/EmailServiceImplTest.java`

```java
package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.notificationservice.entity.EmailTemplate;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.repository.EmailTemplateRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmailServiceImpl}.
 * Verifies template resolution, Thymeleaf rendering, and email sending.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl")
class EmailServiceImplTest {

    @Mock
    private EmailTemplateRepository emailTemplateRepository;

    @Mock
    private JavaMailSender mailSender;

    @Captor
    private ArgumentCaptor<MimeMessage> mimeMessageCaptor;

    private EmailServiceImpl emailService;
    private SpringTemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        templateEngine = new SpringTemplateEngine();
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        templateEngine.setTemplateResolver(resolver);

        emailService = new EmailServiceImpl(
                emailTemplateRepository, templateEngine, mailSender);
        ReflectionTestUtils.setField(emailService, "fromAddress",
                "noreply@familyhobbies.fr");
        ReflectionTestUtils.setField(emailService, "fromName",
                "Family Hobbies Manager");
    }

    @Nested
    @DisplayName("Successful Email Sending")
    class SuccessfulSending {

        @Test
        @DisplayName("should_send_email_when_active_template_found")
        void should_send_email_when_active_template_found() {
            // Given
            EmailTemplate template = EmailTemplate.builder()
                    .code("WELCOME")
                    .subjectTemplate("Bienvenue [[${firstName}]] !")
                    .bodyTemplate("<html><body>Bonjour [[${firstName}]] [[${lastName}]]"
                            + "</body></html>")
                    .active(true)
                    .build();
            when(emailTemplateRepository.findByCodeAndActiveTrue("WELCOME"))
                    .thenReturn(Optional.of(template));

            MimeMessage mockMimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mockMimeMessage);

            Map<String, Object> variables = Map.of(
                    "firstName", "Marie",
                    "lastName", "Dupont"
            );

            // When
            emailService.sendEmail("marie.dupont@example.fr",
                    NotificationCategory.WELCOME, variables);

            // Then
            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    @Nested
    @DisplayName("Template Not Found")
    class TemplateNotFound {

        @Test
        @DisplayName("should_not_send_email_when_no_active_template_exists")
        void should_not_send_email_when_no_active_template_exists() {
            // Given
            when(emailTemplateRepository.findByCodeAndActiveTrue("WELCOME"))
                    .thenReturn(Optional.empty());

            // When
            emailService.sendEmail("marie.dupont@example.fr",
                    NotificationCategory.WELCOME, Map.of());

            // Then
            verify(mailSender, never()).send(any(MimeMessage.class));
            verify(mailSender, never()).createMimeMessage();
        }

        @Test
        @DisplayName("should_not_throw_when_template_not_found")
        void should_not_throw_when_template_not_found() {
            // Given
            when(emailTemplateRepository.findByCodeAndActiveTrue("SYSTEM"))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatCode(() -> emailService.sendEmail(
                    "test@example.fr", NotificationCategory.SYSTEM, Map.of()))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        @DisplayName("should_not_throw_when_mail_sender_fails")
        void should_not_throw_when_mail_sender_fails() {
            // Given
            EmailTemplate template = EmailTemplate.builder()
                    .code("PAYMENT_SUCCESS")
                    .subjectTemplate("Paiement confirme")
                    .bodyTemplate("<html><body>Merci</body></html>")
                    .active(true)
                    .build();
            when(emailTemplateRepository.findByCodeAndActiveTrue("PAYMENT_SUCCESS"))
                    .thenReturn(Optional.of(template));

            MimeMessage mockMimeMessage = mock(MimeMessage.class);
            when(mailSender.createMimeMessage()).thenReturn(mockMimeMessage);
            // Simulate SMTP failure on helper.setFrom() by making send() throw
            // (MimeMessageHelper wraps the mock, send() is the observable side effect)
            org.mockito.Mockito.doThrow(
                            new org.springframework.mail.MailSendException("SMTP timeout"))
                    .when(mailSender).send(any(MimeMessage.class));

            // When / Then
            assertThatCode(() -> emailService.sendEmail(
                    "test@example.fr", NotificationCategory.PAYMENT_SUCCESS, Map.of()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should_use_category_name_as_template_code_when_resolving_template")
        void should_use_category_name_as_template_code_when_resolving_template() {
            // Given
            when(emailTemplateRepository.findByCodeAndActiveTrue("PAYMENT_FAILED"))
                    .thenReturn(Optional.empty());

            // When
            emailService.sendEmail("test@example.fr",
                    NotificationCategory.PAYMENT_FAILED, Map.of());

            // Then -- verify the correct code was used for lookup
            verify(emailTemplateRepository)
                    .findByCodeAndActiveTrue("PAYMENT_FAILED");
        }
    }
}
```

---

## Test Summary

| Test Class | Test Count | What It Verifies |
|------------|-----------|------------------|
| `UserEventConsumerTest` | 4 | Welcome notification, template variables, RGPD cleanup (notifications + preferences) |
| `SubscriptionEventConsumerTest` | 3 | Subscription confirmed/cancelled notifications, variable passing |
| `PaymentEventConsumerTest` | 3 | Payment success/failure notifications, variable passing |
| `AttendanceEventConsumerTest` | 2 | No-exception handling for present and absent statuses |
| `NotificationCreationServiceImplTest` | 6 | Default preferences, email disabled, in-app disabled, both disabled, email failure, null email |
| `EmailServiceImplTest` | 5 | Successful send, template not found (x2), mail sender failure, category-to-code mapping |
| **Total** | **23** | |
