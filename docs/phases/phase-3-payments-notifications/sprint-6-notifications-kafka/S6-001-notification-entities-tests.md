# Story S6-001: Notification Entities -- Failing Tests (TDD Contract)

> Companion file for [S6-001: Implement Notification Entities](./S6-001-notification-entities.md)
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Test 1: Entity Mapping Tests

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/entity/NotificationEntityTest.java`

```java
package com.familyhobbies.notificationservice.entity;

import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for JPA entity construction and default values.
 * These tests verify the entity builders produce objects with correct defaults
 * WITHOUT requiring a database connection.
 */
@DisplayName("Notification Entities")
class NotificationEntityTest {

    @Nested
    @DisplayName("Notification")
    class NotificationTests {

        @Test
        @DisplayName("should_set_defaults_when_built_with_builder")
        void should_set_defaults_when_built_with_builder() {
            // When
            Notification notification = Notification.builder()
                    .userId(1L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.WELCOME)
                    .title("Bienvenue")
                    .message("Bienvenue sur Family Hobbies Manager !")
                    .build();

            // Then
            assertThat(notification.getUserId()).isEqualTo(1L);
            assertThat(notification.getType()).isEqualTo(NotificationType.IN_APP);
            assertThat(notification.getCategory()).isEqualTo(NotificationCategory.WELCOME);
            assertThat(notification.getTitle()).isEqualTo("Bienvenue");
            assertThat(notification.getMessage()).isEqualTo("Bienvenue sur Family Hobbies Manager !");
            assertThat(notification.getRead()).isFalse();
            assertThat(notification.getReadAt()).isNull();
            assertThat(notification.getEmailSent()).isFalse();
            assertThat(notification.getEmailSentAt()).isNull();
            assertThat(notification.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should_allow_override_of_defaults_when_explicitly_set")
        void should_allow_override_of_defaults_when_explicitly_set() {
            // Given
            Instant now = Instant.now();

            // When
            Notification notification = Notification.builder()
                    .userId(2L)
                    .type(NotificationType.EMAIL)
                    .category(NotificationCategory.PAYMENT_SUCCESS)
                    .title("Paiement confirme")
                    .message("Votre paiement a ete traite.")
                    .read(true)
                    .readAt(now)
                    .emailSent(true)
                    .emailSentAt(now)
                    .build();

            // Then
            assertThat(notification.getRead()).isTrue();
            assertThat(notification.getReadAt()).isEqualTo(now);
            assertThat(notification.getEmailSent()).isTrue();
            assertThat(notification.getEmailSentAt()).isEqualTo(now);
        }

        @Test
        @DisplayName("should_create_instance_with_no_args_constructor")
        void should_create_instance_with_no_args_constructor() {
            // When
            Notification notification = new Notification();

            // Then
            assertThat(notification).isNotNull();
            assertThat(notification.getId()).isNull();
        }
    }

    @Nested
    @DisplayName("EmailTemplate")
    class EmailTemplateTests {

        @Test
        @DisplayName("should_set_defaults_when_built_with_builder")
        void should_set_defaults_when_built_with_builder() {
            // When
            EmailTemplate template = EmailTemplate.builder()
                    .code("WELCOME")
                    .subjectTemplate("Bienvenue sur Family Hobbies Manager !")
                    .bodyTemplate("<html><body>Bonjour [[${firstName}]]</body></html>")
                    .variables("firstName,lastName,email")
                    .build();

            // Then
            assertThat(template.getCode()).isEqualTo("WELCOME");
            assertThat(template.getSubjectTemplate()).isEqualTo("Bienvenue sur Family Hobbies Manager !");
            assertThat(template.getBodyTemplate()).contains("firstName");
            assertThat(template.getVariables()).isEqualTo("firstName,lastName,email");
            assertThat(template.getActive()).isTrue();
            assertThat(template.getCreatedAt()).isNotNull();
            assertThat(template.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should_allow_inactive_template_when_active_set_to_false")
        void should_allow_inactive_template_when_active_set_to_false() {
            // When
            EmailTemplate template = EmailTemplate.builder()
                    .code("DEPRECATED")
                    .subjectTemplate("Old Template")
                    .bodyTemplate("<html></html>")
                    .active(false)
                    .build();

            // Then
            assertThat(template.getActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("NotificationPreference")
    class NotificationPreferenceTests {

        @Test
        @DisplayName("should_set_defaults_when_built_with_builder")
        void should_set_defaults_when_built_with_builder() {
            // When
            NotificationPreference pref = NotificationPreference.builder()
                    .userId(1L)
                    .category(NotificationCategory.PAYMENT_SUCCESS)
                    .build();

            // Then
            assertThat(pref.getUserId()).isEqualTo(1L);
            assertThat(pref.getCategory()).isEqualTo(NotificationCategory.PAYMENT_SUCCESS);
            assertThat(pref.getEmailEnabled()).isTrue();
            assertThat(pref.getInAppEnabled()).isTrue();
            assertThat(pref.getCreatedAt()).isNotNull();
            assertThat(pref.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should_allow_disabling_email_when_email_enabled_set_to_false")
        void should_allow_disabling_email_when_email_enabled_set_to_false() {
            // When
            NotificationPreference pref = NotificationPreference.builder()
                    .userId(1L)
                    .category(NotificationCategory.ATTENDANCE_REMINDER)
                    .emailEnabled(false)
                    .inAppEnabled(true)
                    .build();

            // Then
            assertThat(pref.getEmailEnabled()).isFalse();
            assertThat(pref.getInAppEnabled()).isTrue();
        }

        @Test
        @DisplayName("should_allow_disabling_both_channels_when_both_set_to_false")
        void should_allow_disabling_both_channels_when_both_set_to_false() {
            // When
            NotificationPreference pref = NotificationPreference.builder()
                    .userId(1L)
                    .category(NotificationCategory.SYSTEM)
                    .emailEnabled(false)
                    .inAppEnabled(false)
                    .build();

            // Then
            assertThat(pref.getEmailEnabled()).isFalse();
            assertThat(pref.getInAppEnabled()).isFalse();
        }
    }
}
```

---

## Test 2: Enum Tests

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/enums/NotificationEnumTest.java`

```java
package com.familyhobbies.notificationservice.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for notification enums ensuring all expected values exist
 * and no duplicates are present.
 */
@DisplayName("Notification Enums")
class NotificationEnumTest {

    @Nested
    @DisplayName("NotificationType")
    class NotificationTypeTests {

        @Test
        @DisplayName("should_have_exactly_3_values")
        void should_have_exactly_3_values() {
            assertThat(NotificationType.values()).hasSize(3);
        }

        @Test
        @DisplayName("should_contain_EMAIL_IN_APP_SMS")
        void should_contain_EMAIL_IN_APP_SMS() {
            assertThat(NotificationType.values()).containsExactlyInAnyOrder(
                    NotificationType.EMAIL,
                    NotificationType.IN_APP,
                    NotificationType.SMS
            );
        }

        @Test
        @DisplayName("should_resolve_from_string_when_valueOf_called")
        void should_resolve_from_string_when_valueOf_called() {
            assertThat(NotificationType.valueOf("EMAIL")).isEqualTo(NotificationType.EMAIL);
            assertThat(NotificationType.valueOf("IN_APP")).isEqualTo(NotificationType.IN_APP);
            assertThat(NotificationType.valueOf("SMS")).isEqualTo(NotificationType.SMS);
        }
    }

    @Nested
    @DisplayName("NotificationCategory")
    class NotificationCategoryTests {

        @Test
        @DisplayName("should_have_exactly_7_values")
        void should_have_exactly_7_values() {
            assertThat(NotificationCategory.values()).hasSize(7);
        }

        @Test
        @DisplayName("should_contain_all_expected_categories")
        void should_contain_all_expected_categories() {
            assertThat(NotificationCategory.values()).containsExactlyInAnyOrder(
                    NotificationCategory.WELCOME,
                    NotificationCategory.PAYMENT_SUCCESS,
                    NotificationCategory.PAYMENT_FAILED,
                    NotificationCategory.SUBSCRIPTION_CONFIRMED,
                    NotificationCategory.SUBSCRIPTION_CANCELLED,
                    NotificationCategory.ATTENDANCE_REMINDER,
                    NotificationCategory.SYSTEM
            );
        }

        @Test
        @DisplayName("should_have_unique_names_when_checked_for_duplicates")
        void should_have_unique_names_when_checked_for_duplicates() {
            long uniqueCount = java.util.Arrays.stream(NotificationCategory.values())
                    .map(Enum::name)
                    .distinct()
                    .count();
            assertThat(uniqueCount).isEqualTo(NotificationCategory.values().length);
        }

        @Test
        @DisplayName("should_resolve_from_string_when_valueOf_called")
        void should_resolve_from_string_when_valueOf_called() {
            assertThat(NotificationCategory.valueOf("WELCOME"))
                    .isEqualTo(NotificationCategory.WELCOME);
            assertThat(NotificationCategory.valueOf("PAYMENT_SUCCESS"))
                    .isEqualTo(NotificationCategory.PAYMENT_SUCCESS);
            assertThat(NotificationCategory.valueOf("SUBSCRIPTION_CONFIRMED"))
                    .isEqualTo(NotificationCategory.SUBSCRIPTION_CONFIRMED);
        }
    }
}
```

---

## Test 3: Repository Integration Tests

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/repository/NotificationRepositoryTest.java`

```java
package com.familyhobbies.notificationservice.repository;

import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import com.familyhobbies.notificationservice.enums.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link NotificationRepository}.
 * Uses {@code @DataJpaTest} with an embedded H2 database.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("NotificationRepository")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Nested
    @DisplayName("findByUserIdOrderByCreatedAtDesc")
    class FindByUserId {

        @Test
        @DisplayName("should_return_notifications_ordered_by_created_at_desc_when_user_has_notifications")
        void should_return_notifications_ordered_by_created_at_desc_when_user_has_notifications() {
            // Given
            Notification n1 = Notification.builder()
                    .userId(1L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.WELCOME)
                    .title("Bienvenue")
                    .message("Bienvenue sur la plateforme")
                    .build();
            Notification n2 = Notification.builder()
                    .userId(1L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.PAYMENT_SUCCESS)
                    .title("Paiement confirme")
                    .message("Votre paiement de 50 EUR a ete traite")
                    .build();
            notificationRepository.save(n1);
            notificationRepository.save(n2);

            // When
            Page<Notification> page = notificationRepository
                    .findByUserIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 10));

            // Then
            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("should_return_empty_page_when_user_has_no_notifications")
        void should_return_empty_page_when_user_has_no_notifications() {
            // When
            Page<Notification> page = notificationRepository
                    .findByUserIdOrderByCreatedAtDesc(999L, PageRequest.of(0, 10));

            // Then
            assertThat(page.getTotalElements()).isZero();
            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("should_not_return_other_users_notifications_when_queried_by_user_id")
        void should_not_return_other_users_notifications_when_queried_by_user_id() {
            // Given
            notificationRepository.save(Notification.builder()
                    .userId(1L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.WELCOME)
                    .title("User 1")
                    .message("Message for user 1")
                    .build());
            notificationRepository.save(Notification.builder()
                    .userId(2L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.WELCOME)
                    .title("User 2")
                    .message("Message for user 2")
                    .build());

            // When
            Page<Notification> page = notificationRepository
                    .findByUserIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 10));

            // Then
            assertThat(page.getTotalElements()).isEqualTo(1);
            assertThat(page.getContent().get(0).getTitle()).isEqualTo("User 1");
        }
    }

    @Nested
    @DisplayName("countByUserIdAndReadFalse")
    class CountUnread {

        @Test
        @DisplayName("should_return_unread_count_when_user_has_unread_notifications")
        void should_return_unread_count_when_user_has_unread_notifications() {
            // Given
            notificationRepository.save(Notification.builder()
                    .userId(1L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.WELCOME)
                    .title("Unread 1")
                    .message("Unread message 1")
                    .read(false)
                    .build());
            notificationRepository.save(Notification.builder()
                    .userId(1L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.PAYMENT_SUCCESS)
                    .title("Unread 2")
                    .message("Unread message 2")
                    .read(false)
                    .build());
            notificationRepository.save(Notification.builder()
                    .userId(1L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.SYSTEM)
                    .title("Read")
                    .message("Read message")
                    .read(true)
                    .build());

            // When
            long unreadCount = notificationRepository.countByUserIdAndReadFalse(1L);

            // Then
            assertThat(unreadCount).isEqualTo(2);
        }

        @Test
        @DisplayName("should_return_zero_when_all_notifications_are_read")
        void should_return_zero_when_all_notifications_are_read() {
            // Given
            notificationRepository.save(Notification.builder()
                    .userId(1L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.WELCOME)
                    .title("Read")
                    .message("Read message")
                    .read(true)
                    .build());

            // When
            long unreadCount = notificationRepository.countByUserIdAndReadFalse(1L);

            // Then
            assertThat(unreadCount).isZero();
        }

        @Test
        @DisplayName("should_return_zero_when_user_has_no_notifications")
        void should_return_zero_when_user_has_no_notifications() {
            // When
            long unreadCount = notificationRepository.countByUserIdAndReadFalse(999L);

            // Then
            assertThat(unreadCount).isZero();
        }
    }

    @Nested
    @DisplayName("deleteByUserId")
    class DeleteByUserId {

        @Test
        @DisplayName("should_delete_all_notifications_when_user_id_matches")
        void should_delete_all_notifications_when_user_id_matches() {
            // Given
            notificationRepository.save(Notification.builder()
                    .userId(1L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.WELCOME)
                    .title("To delete")
                    .message("Will be deleted")
                    .build());
            notificationRepository.save(Notification.builder()
                    .userId(2L)
                    .type(NotificationType.IN_APP)
                    .category(NotificationCategory.WELCOME)
                    .title("To keep")
                    .message("Will be kept")
                    .build());

            // When
            notificationRepository.deleteByUserId(1L);

            // Then
            assertThat(notificationRepository.findAll()).hasSize(1);
            assertThat(notificationRepository.findAll().get(0).getUserId()).isEqualTo(2L);
        }
    }
}
```

---

## Test 4: EmailTemplate Repository Integration Tests

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/repository/EmailTemplateRepositoryTest.java`

```java
package com.familyhobbies.notificationservice.repository;

import com.familyhobbies.notificationservice.entity.EmailTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link EmailTemplateRepository}.
 * Uses {@code @DataJpaTest} with an embedded H2 database.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("EmailTemplateRepository")
class EmailTemplateRepositoryTest {

    @Autowired
    private EmailTemplateRepository emailTemplateRepository;

    @BeforeEach
    void setUp() {
        emailTemplateRepository.deleteAll();
    }

    @Test
    @DisplayName("should_return_template_when_code_exists_and_active_is_true")
    void should_return_template_when_code_exists_and_active_is_true() {
        // Given
        emailTemplateRepository.save(EmailTemplate.builder()
                .code("WELCOME")
                .subjectTemplate("Bienvenue !")
                .bodyTemplate("<html><body>Bonjour</body></html>")
                .variables("firstName,lastName")
                .active(true)
                .build());

        // When
        Optional<EmailTemplate> result = emailTemplateRepository
                .findByCodeAndActiveTrue("WELCOME");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("WELCOME");
        assertThat(result.get().getSubjectTemplate()).isEqualTo("Bienvenue !");
    }

    @Test
    @DisplayName("should_return_empty_when_code_exists_but_active_is_false")
    void should_return_empty_when_code_exists_but_active_is_false() {
        // Given
        emailTemplateRepository.save(EmailTemplate.builder()
                .code("DEPRECATED")
                .subjectTemplate("Old")
                .bodyTemplate("<html></html>")
                .active(false)
                .build());

        // When
        Optional<EmailTemplate> result = emailTemplateRepository
                .findByCodeAndActiveTrue("DEPRECATED");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should_return_empty_when_code_does_not_exist")
    void should_return_empty_when_code_does_not_exist() {
        // When
        Optional<EmailTemplate> result = emailTemplateRepository
                .findByCodeAndActiveTrue("NONEXISTENT");

        // Then
        assertThat(result).isEmpty();
    }
}
```

---

## Test 5: NotificationPreference Repository Integration Tests

**Where**: `backend/notification-service/src/test/java/com/familyhobbies/notificationservice/repository/NotificationPreferenceRepositoryTest.java`

```java
package com.familyhobbies.notificationservice.repository;

import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.enums.NotificationCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link NotificationPreferenceRepository}.
 * Uses {@code @DataJpaTest} with an embedded H2 database.
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("NotificationPreferenceRepository")
class NotificationPreferenceRepositoryTest {

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @BeforeEach
    void setUp() {
        preferenceRepository.deleteAll();
    }

    @Nested
    @DisplayName("findByUserId")
    class FindByUserId {

        @Test
        @DisplayName("should_return_all_preferences_when_user_has_preferences")
        void should_return_all_preferences_when_user_has_preferences() {
            // Given
            preferenceRepository.save(NotificationPreference.builder()
                    .userId(1L)
                    .category(NotificationCategory.WELCOME)
                    .emailEnabled(true)
                    .inAppEnabled(true)
                    .build());
            preferenceRepository.save(NotificationPreference.builder()
                    .userId(1L)
                    .category(NotificationCategory.PAYMENT_SUCCESS)
                    .emailEnabled(false)
                    .inAppEnabled(true)
                    .build());

            // When
            List<NotificationPreference> prefs = preferenceRepository.findByUserId(1L);

            // Then
            assertThat(prefs).hasSize(2);
        }

        @Test
        @DisplayName("should_return_empty_list_when_user_has_no_preferences")
        void should_return_empty_list_when_user_has_no_preferences() {
            // When
            List<NotificationPreference> prefs = preferenceRepository.findByUserId(999L);

            // Then
            assertThat(prefs).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByUserIdAndCategory")
    class FindByUserIdAndCategory {

        @Test
        @DisplayName("should_return_preference_when_user_and_category_match")
        void should_return_preference_when_user_and_category_match() {
            // Given
            preferenceRepository.save(NotificationPreference.builder()
                    .userId(1L)
                    .category(NotificationCategory.PAYMENT_FAILED)
                    .emailEnabled(true)
                    .inAppEnabled(false)
                    .build());

            // When
            Optional<NotificationPreference> result = preferenceRepository
                    .findByUserIdAndCategory(1L, NotificationCategory.PAYMENT_FAILED);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getEmailEnabled()).isTrue();
            assertThat(result.get().getInAppEnabled()).isFalse();
        }

        @Test
        @DisplayName("should_return_empty_when_category_does_not_match")
        void should_return_empty_when_category_does_not_match() {
            // Given
            preferenceRepository.save(NotificationPreference.builder()
                    .userId(1L)
                    .category(NotificationCategory.WELCOME)
                    .build());

            // When
            Optional<NotificationPreference> result = preferenceRepository
                    .findByUserIdAndCategory(1L, NotificationCategory.SYSTEM);

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteByUserId")
    class DeleteByUserId {

        @Test
        @DisplayName("should_delete_all_preferences_when_user_id_matches")
        void should_delete_all_preferences_when_user_id_matches() {
            // Given
            preferenceRepository.save(NotificationPreference.builder()
                    .userId(1L)
                    .category(NotificationCategory.WELCOME)
                    .build());
            preferenceRepository.save(NotificationPreference.builder()
                    .userId(1L)
                    .category(NotificationCategory.PAYMENT_SUCCESS)
                    .build());
            preferenceRepository.save(NotificationPreference.builder()
                    .userId(2L)
                    .category(NotificationCategory.WELCOME)
                    .build());

            // When
            preferenceRepository.deleteByUserId(1L);

            // Then
            assertThat(preferenceRepository.findAll()).hasSize(1);
            assertThat(preferenceRepository.findAll().get(0).getUserId()).isEqualTo(2L);
        }
    }
}
```

---

## Test Summary

| Test Class | Test Count | What It Verifies |
|------------|-----------|------------------|
| `NotificationEntityTest` | 8 | Entity builders, defaults, override, no-args constructor |
| `NotificationEnumTest` | 7 | Enum values count, contents, uniqueness, valueOf |
| `NotificationRepositoryTest` | 7 | Paginated queries, unread count, delete by user, user isolation |
| `EmailTemplateRepositoryTest` | 3 | Active template lookup, inactive filtering, non-existent code |
| `NotificationPreferenceRepositoryTest` | 5 | User preferences, user+category lookup, delete by user |
| **Total** | **30** | |
