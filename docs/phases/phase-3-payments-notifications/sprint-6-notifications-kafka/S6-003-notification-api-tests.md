# Story S6-003: Notification API -- TDD Tests Companion

> Companion to: [S6-003: Notification API](./S6-003-notification-api.md)
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Overview

This file contains the full JUnit 5 test source code for the notification API story (S6-003). Tests are organized into three classes:

1. **NotificationMapperTest** -- 5 tests for entity-to-DTO conversion
2. **NotificationServiceImplTest** -- 12 tests for service business logic
3. **NotificationControllerTest** -- 8 tests for REST endpoint integration

All tests are written TDD-style: they define the expected behavior contract before implementation. They should compile but fail until the production code is implemented.

---

## Test 1: NotificationMapperTest

- **What**: Unit tests for the NotificationMapper component
- **Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/mapper/NotificationMapperTest.java`
- **Why**: Validates entity-to-DTO conversions and default preference population

```java
package com.familyhobbies.notificationservice.mapper;

import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse.CategoryPreference;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NotificationMapper}.
 */
class NotificationMapperTest {

    private NotificationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new NotificationMapper();
    }

    @Test
    @DisplayName("should_map_notification_entity_to_response_when_all_fields_present")
    void should_map_notification_entity_to_response_when_all_fields_present() {
        // Given
        Instant now = Instant.now();
        Notification entity = Notification.builder()
                .id(1L)
                .userId(42L)
                .type(NotificationType.IN_APP)
                .category(NotificationCategory.PAYMENT_SUCCESS)
                .title("Paiement confirme")
                .message("Votre paiement de 150,00 EUR a ete confirme.")
                .read(true)
                .readAt(now)
                .createdAt(now.minusSeconds(3600))
                .build();

        // When
        NotificationResponse response = mapper.toNotificationResponse(entity);

        // Then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(42L);
        assertThat(response.getCategory()).isEqualTo("PAYMENT_SUCCESS");
        assertThat(response.getTitle()).isEqualTo("Paiement confirme");
        assertThat(response.getMessage()).isEqualTo(
                "Votre paiement de 150,00 EUR a ete confirme.");
        assertThat(response.isRead()).isTrue();
        assertThat(response.getReadAt()).isEqualTo(now);
        assertThat(response.getCreatedAt()).isEqualTo(now.minusSeconds(3600));
    }

    @Test
    @DisplayName("should_map_unread_notification_with_null_readAt_when_not_read")
    void should_map_unread_notification_with_null_readAt_when_not_read() {
        // Given
        Notification entity = Notification.builder()
                .id(2L)
                .userId(42L)
                .type(NotificationType.IN_APP)
                .category(NotificationCategory.WELCOME)
                .title("Bienvenue")
                .message("Bienvenue dans Family Hobbies Manager !")
                .read(false)
                .readAt(null)
                .createdAt(Instant.now())
                .build();

        // When
        NotificationResponse response = mapper.toNotificationResponse(entity);

        // Then
        assertThat(response.isRead()).isFalse();
        assertThat(response.getReadAt()).isNull();
    }

    @Test
    @DisplayName("should_return_all_categories_with_defaults_when_no_preferences_exist")
    void should_return_all_categories_with_defaults_when_no_preferences_exist() {
        // Given
        Long userId = 42L;
        List<NotificationPreference> emptyPreferences = Collections.emptyList();

        // When
        NotificationPreferenceResponse response =
                mapper.toPreferenceResponse(userId, emptyPreferences);

        // Then
        assertThat(response.getUserId()).isEqualTo(42L);
        assertThat(response.getCategories()).hasSize(
                NotificationCategory.values().length);

        // All defaults should be true/true
        for (NotificationCategory cat : NotificationCategory.values()) {
            CategoryPreference pref = response.getCategories().get(cat.name());
            assertThat(pref).isNotNull();
            assertThat(pref.isEmailEnabled()).isTrue();
            assertThat(pref.isInAppEnabled()).isTrue();
        }
    }

    @Test
    @DisplayName("should_merge_user_preferences_with_defaults_when_partial_preferences")
    void should_merge_user_preferences_with_defaults_when_partial_preferences() {
        // Given
        Long userId = 42L;
        List<NotificationPreference> partialPreferences = List.of(
                NotificationPreference.builder()
                        .userId(userId)
                        .category(NotificationCategory.PAYMENT_SUCCESS)
                        .emailEnabled(true)
                        .inAppEnabled(false)
                        .build(),
                NotificationPreference.builder()
                        .userId(userId)
                        .category(NotificationCategory.ATTENDANCE_REMINDER)
                        .emailEnabled(false)
                        .inAppEnabled(true)
                        .build()
        );

        // When
        NotificationPreferenceResponse response =
                mapper.toPreferenceResponse(userId, partialPreferences);

        // Then -- all 7 categories present
        assertThat(response.getCategories()).hasSize(
                NotificationCategory.values().length);

        // Explicit preference: PAYMENT_SUCCESS
        CategoryPreference paymentPref =
                response.getCategories().get("PAYMENT_SUCCESS");
        assertThat(paymentPref.isEmailEnabled()).isTrue();
        assertThat(paymentPref.isInAppEnabled()).isFalse();

        // Explicit preference: ATTENDANCE_REMINDER
        CategoryPreference attendancePref =
                response.getCategories().get("ATTENDANCE_REMINDER");
        assertThat(attendancePref.isEmailEnabled()).isFalse();
        assertThat(attendancePref.isInAppEnabled()).isTrue();

        // Default: WELCOME (no explicit preference)
        CategoryPreference welcomePref =
                response.getCategories().get("WELCOME");
        assertThat(welcomePref.isEmailEnabled()).isTrue();
        assertThat(welcomePref.isInAppEnabled()).isTrue();
    }

    @Test
    @DisplayName("should_use_explicit_preferences_for_all_categories_when_fully_configured")
    void should_use_explicit_preferences_for_all_categories_when_fully_configured() {
        // Given
        Long userId = 42L;
        List<NotificationPreference> fullPreferences = List.of(
                buildPref(userId, NotificationCategory.WELCOME, false, true),
                buildPref(userId, NotificationCategory.PAYMENT_SUCCESS, true, true),
                buildPref(userId, NotificationCategory.PAYMENT_FAILED, true, false),
                buildPref(userId, NotificationCategory.SUBSCRIPTION_CONFIRMED, false, false),
                buildPref(userId, NotificationCategory.SUBSCRIPTION_CANCELLED, true, true),
                buildPref(userId, NotificationCategory.ATTENDANCE_REMINDER, false, true),
                buildPref(userId, NotificationCategory.SYSTEM, true, true)
        );

        // When
        NotificationPreferenceResponse response =
                mapper.toPreferenceResponse(userId, fullPreferences);

        // Then
        assertThat(response.getCategories()).hasSize(7);

        CategoryPreference welcome =
                response.getCategories().get("WELCOME");
        assertThat(welcome.isEmailEnabled()).isFalse();
        assertThat(welcome.isInAppEnabled()).isTrue();

        CategoryPreference subscriptionConfirmed =
                response.getCategories().get("SUBSCRIPTION_CONFIRMED");
        assertThat(subscriptionConfirmed.isEmailEnabled()).isFalse();
        assertThat(subscriptionConfirmed.isInAppEnabled()).isFalse();
    }

    private NotificationPreference buildPref(Long userId,
                                              NotificationCategory category,
                                              boolean email, boolean inApp) {
        return NotificationPreference.builder()
                .userId(userId)
                .category(category)
                .emailEnabled(email)
                .inAppEnabled(inApp)
                .build();
    }
}
```

---

## Test 2: NotificationServiceImplTest

- **What**: Unit tests for NotificationServiceImpl with mocked repositories
- **Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/service/impl/NotificationServiceImplTest.java`
- **Why**: Validates business logic: ownership checks, mark-as-read idempotency, upsert semantics, error cases

```java
package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.notificationservice.dto.request.NotificationPreferenceRequest;
import com.familyhobbies.notificationservice.dto.response.MarkAllReadResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.enums.NotificationType;
import com.familyhobbies.notificationservice.mapper.NotificationMapper;
import com.familyhobbies.notificationservice.repository.NotificationPreferenceRepository;
import com.familyhobbies.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NotificationServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Spy
    private NotificationMapper notificationMapper = new NotificationMapper();

    @InjectMocks
    private NotificationServiceImpl service;

    private static final Long USER_ID = 42L;
    private static final Long OTHER_USER_ID = 99L;

    private Notification buildNotification(Long id, Long userId,
                                            NotificationCategory category,
                                            boolean read) {
        return Notification.builder()
                .id(id)
                .userId(userId)
                .type(NotificationType.IN_APP)
                .category(category)
                .title("Test notification " + id)
                .message("Message for notification " + id)
                .read(read)
                .readAt(read ? Instant.now() : null)
                .createdAt(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("getMyNotifications")
    class GetMyNotifications {

        @Test
        @DisplayName("should_return_paginated_notifications_when_user_has_notifications")
        void should_return_paginated_notifications_when_user_has_notifications() {
            // Given
            Pageable pageable = PageRequest.of(0, 20);
            List<Notification> notifications = List.of(
                    buildNotification(1L, USER_ID, NotificationCategory.WELCOME, false),
                    buildNotification(2L, USER_ID,
                            NotificationCategory.PAYMENT_SUCCESS, true));
            Page<Notification> page = new PageImpl<>(notifications, pageable, 2);

            when(notificationRepository.findByUserIdWithFilters(
                    eq(USER_ID), eq(null), eq(null), eq(null), eq(null),
                    any(Pageable.class))).thenReturn(page);

            // When
            Page<NotificationResponse> result =
                    service.getMyNotifications(
                            USER_ID, null, null, null, null, pageable);

            // Then
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getCategory())
                    .isEqualTo("WELCOME");
        }
    }

    @Nested
    @DisplayName("getUnreadCount")
    class GetUnreadCount {

        @Test
        @DisplayName("should_return_unread_count_when_user_has_unread_notifications")
        void should_return_unread_count_when_user_has_unread_notifications() {
            // Given
            when(notificationRepository.countByUserIdAndReadFalse(USER_ID))
                    .thenReturn(5L);

            // When
            long count = service.getUnreadCount(USER_ID);

            // Then
            assertThat(count).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("markAsRead")
    class MarkAsRead {

        @Test
        @DisplayName("should_mark_notification_as_read_when_user_owns_notification")
        void should_mark_notification_as_read_when_user_owns_notification() {
            // Given
            Notification notification = buildNotification(
                    1L, USER_ID, NotificationCategory.WELCOME, false);
            when(notificationRepository.findById(1L))
                    .thenReturn(Optional.of(notification));
            when(notificationRepository.save(any(Notification.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // When
            NotificationResponse response = service.markAsRead(1L, USER_ID);

            // Then
            assertThat(response.isRead()).isTrue();
            assertThat(response.getReadAt()).isNotNull();
            verify(notificationRepository).save(notification);
        }

        @Test
        @DisplayName("should_throw_ResourceNotFoundException_when_notification_not_found")
        void should_throw_ResourceNotFoundException_when_notification_not_found() {
            // Given
            when(notificationRepository.findById(999L))
                    .thenReturn(Optional.empty());

            // When / Then
            assertThatThrownBy(() -> service.markAsRead(999L, USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");
        }

        @Test
        @DisplayName("should_throw_ForbiddenException_when_user_does_not_own_notification")
        void should_throw_ForbiddenException_when_user_does_not_own_notification() {
            // Given
            Notification notification = buildNotification(
                    1L, OTHER_USER_ID, NotificationCategory.WELCOME, false);
            when(notificationRepository.findById(1L))
                    .thenReturn(Optional.of(notification));

            // When / Then
            assertThatThrownBy(() -> service.markAsRead(1L, USER_ID))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("1");
        }

        @Test
        @DisplayName("should_not_update_when_notification_already_read")
        void should_not_update_when_notification_already_read() {
            // Given
            Instant originalReadAt = Instant.now().minusSeconds(3600);
            Notification notification = buildNotification(
                    1L, USER_ID, NotificationCategory.WELCOME, true);
            notification.setReadAt(originalReadAt);
            when(notificationRepository.findById(1L))
                    .thenReturn(Optional.of(notification));

            // When
            NotificationResponse response = service.markAsRead(1L, USER_ID);

            // Then -- already read, no save call
            assertThat(response.isRead()).isTrue();
            verify(notificationRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("markAllAsRead")
    class MarkAllAsRead {

        @Test
        @DisplayName("should_mark_all_unread_notifications_when_user_has_unread")
        void should_mark_all_unread_notifications_when_user_has_unread() {
            // Given
            List<Notification> unread = List.of(
                    buildNotification(1L, USER_ID, NotificationCategory.WELCOME, false),
                    buildNotification(2L, USER_ID,
                            NotificationCategory.PAYMENT_SUCCESS, false),
                    buildNotification(3L, USER_ID,
                            NotificationCategory.SYSTEM, false));
            when(notificationRepository.findByUserIdAndReadFalse(USER_ID))
                    .thenReturn(unread);

            // When
            MarkAllReadResponse response = service.markAllAsRead(USER_ID);

            // Then
            assertThat(response.getMarkedCount()).isEqualTo(3);
            assertThat(response.getReadAt()).isNotNull();
            verify(notificationRepository).saveAll(unread);
        }

        @Test
        @DisplayName("should_return_zero_count_when_no_unread_notifications")
        void should_return_zero_count_when_no_unread_notifications() {
            // Given
            when(notificationRepository.findByUserIdAndReadFalse(USER_ID))
                    .thenReturn(Collections.emptyList());

            // When
            MarkAllReadResponse response = service.markAllAsRead(USER_ID);

            // Then
            assertThat(response.getMarkedCount()).isEqualTo(0);
            assertThat(response.getReadAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("getPreferences")
    class GetPreferences {

        @Test
        @DisplayName("should_return_preferences_with_defaults_when_user_has_partial_prefs")
        void should_return_preferences_with_defaults_when_user_has_partial_prefs() {
            // Given
            List<NotificationPreference> prefs = List.of(
                    NotificationPreference.builder()
                            .userId(USER_ID)
                            .category(NotificationCategory.WELCOME)
                            .emailEnabled(false)
                            .inAppEnabled(true)
                            .build());
            when(preferenceRepository.findByUserId(USER_ID)).thenReturn(prefs);

            // When
            NotificationPreferenceResponse response =
                    service.getPreferences(USER_ID);

            // Then
            assertThat(response.getUserId()).isEqualTo(USER_ID);
            assertThat(response.getCategories()).hasSize(
                    NotificationCategory.values().length);

            // Explicit preference
            assertThat(response.getCategories().get("WELCOME")
                    .isEmailEnabled()).isFalse();

            // Default preference
            assertThat(response.getCategories().get("SYSTEM")
                    .isEmailEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("updatePreferences")
    class UpdatePreferences {

        @Test
        @DisplayName("should_create_new_preference_when_category_does_not_exist")
        void should_create_new_preference_when_category_does_not_exist() {
            // Given
            List<NotificationPreferenceRequest> requests = List.of(
                    NotificationPreferenceRequest.builder()
                            .category("PAYMENT_SUCCESS")
                            .emailEnabled(true)
                            .inAppEnabled(false)
                            .build());
            when(preferenceRepository.findByUserIdAndCategory(
                    USER_ID, NotificationCategory.PAYMENT_SUCCESS))
                    .thenReturn(Optional.empty());
            when(preferenceRepository.findByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());

            // When
            service.updatePreferences(USER_ID, requests);

            // Then
            ArgumentCaptor<NotificationPreference> captor =
                    ArgumentCaptor.forClass(NotificationPreference.class);
            verify(preferenceRepository).save(captor.capture());
            NotificationPreference saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getCategory())
                    .isEqualTo(NotificationCategory.PAYMENT_SUCCESS);
            assertThat(saved.getEmailEnabled()).isTrue();
            assertThat(saved.getInAppEnabled()).isFalse();
        }

        @Test
        @DisplayName("should_update_existing_preference_when_category_already_exists")
        void should_update_existing_preference_when_category_already_exists() {
            // Given
            NotificationPreference existing = NotificationPreference.builder()
                    .id(10L)
                    .userId(USER_ID)
                    .category(NotificationCategory.WELCOME)
                    .emailEnabled(true)
                    .inAppEnabled(true)
                    .build();
            List<NotificationPreferenceRequest> requests = List.of(
                    NotificationPreferenceRequest.builder()
                            .category("WELCOME")
                            .emailEnabled(false)
                            .inAppEnabled(true)
                            .build());
            when(preferenceRepository.findByUserIdAndCategory(
                    USER_ID, NotificationCategory.WELCOME))
                    .thenReturn(Optional.of(existing));
            when(preferenceRepository.findByUserId(USER_ID))
                    .thenReturn(List.of(existing));

            // When
            service.updatePreferences(USER_ID, requests);

            // Then
            verify(preferenceRepository).save(existing);
            assertThat(existing.getEmailEnabled()).isFalse();
            assertThat(existing.getInAppEnabled()).isTrue();
        }

        @Test
        @DisplayName("should_handle_mixed_create_and_update_when_batch_request")
        void should_handle_mixed_create_and_update_when_batch_request() {
            // Given
            NotificationPreference existing = NotificationPreference.builder()
                    .id(10L)
                    .userId(USER_ID)
                    .category(NotificationCategory.WELCOME)
                    .emailEnabled(true)
                    .inAppEnabled(true)
                    .build();
            List<NotificationPreferenceRequest> requests = List.of(
                    NotificationPreferenceRequest.builder()
                            .category("WELCOME")
                            .emailEnabled(false)
                            .inAppEnabled(false)
                            .build(),
                    NotificationPreferenceRequest.builder()
                            .category("SYSTEM")
                            .emailEnabled(true)
                            .inAppEnabled(false)
                            .build());
            when(preferenceRepository.findByUserIdAndCategory(
                    USER_ID, NotificationCategory.WELCOME))
                    .thenReturn(Optional.of(existing));
            when(preferenceRepository.findByUserIdAndCategory(
                    USER_ID, NotificationCategory.SYSTEM))
                    .thenReturn(Optional.empty());
            when(preferenceRepository.findByUserId(USER_ID))
                    .thenReturn(Collections.emptyList());

            // When
            service.updatePreferences(USER_ID, requests);

            // Then -- 2 save calls: 1 update + 1 create
            verify(preferenceRepository, times(2)).save(any());
        }
    }
}
```

---

## Test 3: NotificationControllerTest

- **What**: WebMvcTest integration tests for NotificationController
- **Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/controller/NotificationControllerTest.java`
- **Why**: Validates HTTP status codes, request/response serialization, header handling, and error mapping

```java
package com.familyhobbies.notificationservice.controller;

import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.notificationservice.dto.request.NotificationPreferenceRequest;
import com.familyhobbies.notificationservice.dto.response.MarkAllReadResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse.CategoryPreference;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMvcTest for {@link NotificationController}.
 */
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    private static final Long USER_ID = 42L;
    private static final String X_USER_ID = "X-User-Id";

    @Test
    @DisplayName("should_return_200_with_paginated_notifications_when_GET_me")
    void should_return_200_with_paginated_notifications_when_GET_me()
            throws Exception {
        // Given
        List<NotificationResponse> notifications = List.of(
                NotificationResponse.builder()
                        .id(1L)
                        .userId(USER_ID)
                        .category("WELCOME")
                        .title("Bienvenue")
                        .message("Bienvenue dans Family Hobbies Manager !")
                        .read(false)
                        .createdAt(Instant.now())
                        .build());
        Page<NotificationResponse> page =
                new PageImpl<>(notifications, PageRequest.of(0, 20), 1);
        when(notificationService.getMyNotifications(
                eq(USER_ID), any(), any(), any(), any(), any()))
                .thenReturn(page);

        // When / Then
        mockMvc.perform(get("/api/v1/notifications/me")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", is("Bienvenue")))
                .andExpect(jsonPath("$.content[0].read", is(false)))
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    @DisplayName("should_return_200_with_unread_count_when_GET_unread_count")
    void should_return_200_with_unread_count_when_GET_unread_count()
            throws Exception {
        // Given
        when(notificationService.getUnreadCount(USER_ID)).thenReturn(7L);

        // When / Then
        mockMvc.perform(get("/api/v1/notifications/me/unread-count")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(7)));
    }

    @Test
    @DisplayName("should_return_200_with_read_notification_when_PUT_read")
    void should_return_200_with_read_notification_when_PUT_read()
            throws Exception {
        // Given
        Instant readAt = Instant.now();
        NotificationResponse response = NotificationResponse.builder()
                .id(1L)
                .userId(USER_ID)
                .category("WELCOME")
                .title("Bienvenue")
                .message("Message")
                .read(true)
                .readAt(readAt)
                .createdAt(Instant.now().minusSeconds(60))
                .build();
        when(notificationService.markAsRead(1L, USER_ID)).thenReturn(response);

        // When / Then
        mockMvc.perform(put("/api/v1/notifications/1/read")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.read", is(true)))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    @Test
    @DisplayName("should_return_404_when_PUT_read_notification_not_found")
    void should_return_404_when_PUT_read_notification_not_found()
            throws Exception {
        // Given
        when(notificationService.markAsRead(999L, USER_ID))
                .thenThrow(new ResourceNotFoundException(
                        "Notification non trouvee: 999"));

        // When / Then
        mockMvc.perform(put("/api/v1/notifications/999/read")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should_return_403_when_PUT_read_forbidden_notification")
    void should_return_403_when_PUT_read_forbidden_notification()
            throws Exception {
        // Given
        when(notificationService.markAsRead(1L, USER_ID))
                .thenThrow(new ForbiddenException(
                        "Acces interdit a la notification 1"));

        // When / Then
        mockMvc.perform(put("/api/v1/notifications/1/read")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_return_200_with_marked_count_when_PUT_read_all")
    void should_return_200_with_marked_count_when_PUT_read_all()
            throws Exception {
        // Given
        MarkAllReadResponse response = MarkAllReadResponse.builder()
                .markedCount(5)
                .readAt(Instant.now())
                .build();
        when(notificationService.markAllAsRead(USER_ID)).thenReturn(response);

        // When / Then
        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedCount", is(5)))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
    }

    @Test
    @DisplayName("should_return_200_with_preferences_when_GET_preferences")
    void should_return_200_with_preferences_when_GET_preferences()
            throws Exception {
        // Given
        NotificationPreferenceResponse response =
                NotificationPreferenceResponse.builder()
                        .userId(USER_ID)
                        .categories(Map.of(
                                "WELCOME", CategoryPreference.builder()
                                        .emailEnabled(true)
                                        .inAppEnabled(true)
                                        .build(),
                                "SYSTEM", CategoryPreference.builder()
                                        .emailEnabled(false)
                                        .inAppEnabled(true)
                                        .build()))
                        .build();
        when(notificationService.getPreferences(USER_ID)).thenReturn(response);

        // When / Then
        mockMvc.perform(get("/api/v1/notifications/preferences")
                        .header(X_USER_ID, USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(42)))
                .andExpect(jsonPath("$.categories.WELCOME.emailEnabled",
                        is(true)))
                .andExpect(jsonPath("$.categories.SYSTEM.emailEnabled",
                        is(false)));
    }

    @Test
    @DisplayName("should_return_200_with_updated_preferences_when_PUT_preferences")
    void should_return_200_with_updated_preferences_when_PUT_preferences()
            throws Exception {
        // Given
        List<NotificationPreferenceRequest> requests = List.of(
                NotificationPreferenceRequest.builder()
                        .category("WELCOME")
                        .emailEnabled(false)
                        .inAppEnabled(true)
                        .build());

        NotificationPreferenceResponse response =
                NotificationPreferenceResponse.builder()
                        .userId(USER_ID)
                        .categories(Map.of(
                                "WELCOME", CategoryPreference.builder()
                                        .emailEnabled(false)
                                        .inAppEnabled(true)
                                        .build()))
                        .build();
        when(notificationService.updatePreferences(eq(USER_ID), any()))
                .thenReturn(response);

        // When / Then
        mockMvc.perform(put("/api/v1/notifications/preferences")
                        .header(X_USER_ID, USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requests)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(42)))
                .andExpect(jsonPath("$.categories.WELCOME.emailEnabled",
                        is(false)));
    }
}
```

---

## Test Summary

| Test Class | Test Count | Category |
|-----------|-----------|----------|
| NotificationMapperTest | 5 | Unit -- mapper logic |
| NotificationServiceImplTest | 12 | Unit -- service business logic |
| NotificationControllerTest | 8 | Integration -- WebMvcTest |
| **Total** | **25** | |
