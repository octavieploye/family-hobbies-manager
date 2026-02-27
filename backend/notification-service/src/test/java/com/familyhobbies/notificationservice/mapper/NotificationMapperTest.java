package com.familyhobbies.notificationservice.mapper;

import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import com.familyhobbies.notificationservice.entity.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for NotificationMapper.
 *
 * Stories: S6-001 -- Notification Entity + CRUD
 * Tests: 4 test methods
 */
class NotificationMapperTest {

    private final NotificationMapper mapper = new NotificationMapper();

    @Test
    @DisplayName("should map to response when valid notification")
    void should_mapToResponse_when_validNotification() {
        // given
        Instant now = Instant.now();
        Instant readAt = now.plusSeconds(60);
        Notification notification = Notification.builder()
                .id(1L)
                .userId(10L)
                .type(NotificationType.EMAIL)
                .category(NotificationCategory.WELCOME)
                .title("Bienvenue")
                .message("Bienvenue sur la plateforme")
                .read(true)
                .referenceId("10")
                .referenceType("USER")
                .createdAt(now)
                .readAt(readAt)
                .build();

        // when
        NotificationResponse response = mapper.toResponse(notification);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.type()).isEqualTo(NotificationType.EMAIL);
        assertThat(response.category()).isEqualTo(NotificationCategory.WELCOME);
        assertThat(response.title()).isEqualTo("Bienvenue");
        assertThat(response.message()).isEqualTo("Bienvenue sur la plateforme");
        assertThat(response.read()).isTrue();
        assertThat(response.referenceId()).isEqualTo("10");
        assertThat(response.referenceType()).isEqualTo("USER");
        assertThat(response.createdAt()).isEqualTo(now);
        assertThat(response.readAt()).isEqualTo(readAt);
    }

    @Test
    @DisplayName("should map to preference response when valid preference")
    void should_mapToPreferenceResponse_when_validPreference() {
        // given
        NotificationPreference preference = NotificationPreference.builder()
                .id(1L)
                .userId(10L)
                .category(NotificationCategory.PAYMENT)
                .emailEnabled(true)
                .inAppEnabled(false)
                .build();

        // when
        NotificationPreferenceResponse response = mapper.toPreferenceResponse(preference);

        // then
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.category()).isEqualTo(NotificationCategory.PAYMENT);
        assertThat(response.emailEnabled()).isTrue();
        assertThat(response.inAppEnabled()).isFalse();
    }

    @Test
    @DisplayName("should handle null readAt when unread notification")
    void should_handleNullReadAt_when_unreadNotification() {
        // given
        Notification notification = Notification.builder()
                .id(2L)
                .userId(20L)
                .type(NotificationType.IN_APP)
                .category(NotificationCategory.SYSTEM)
                .title("System Update")
                .message("A system update is available")
                .read(false)
                .createdAt(Instant.now())
                .readAt(null)
                .build();

        // when
        NotificationResponse response = mapper.toResponse(notification);

        // then
        assertThat(response.read()).isFalse();
        assertThat(response.readAt()).isNull();
    }

    @Test
    @DisplayName("should preserve all fields when mapping")
    void should_preserveAllFields_when_mapping() {
        // given
        Instant now = Instant.now();
        Notification notification = Notification.builder()
                .id(3L)
                .userId(30L)
                .type(NotificationType.PUSH)
                .category(NotificationCategory.ATTENDANCE)
                .title("Presence")
                .message("Votre presence a ete enregistree")
                .read(false)
                .referenceId("ABC-123")
                .referenceType("ATTENDANCE")
                .createdAt(now)
                .build();

        // when
        NotificationResponse response = mapper.toResponse(notification);

        // then
        assertThat(response.id()).isEqualTo(notification.getId());
        assertThat(response.userId()).isEqualTo(notification.getUserId());
        assertThat(response.type()).isEqualTo(notification.getType());
        assertThat(response.category()).isEqualTo(notification.getCategory());
        assertThat(response.title()).isEqualTo(notification.getTitle());
        assertThat(response.message()).isEqualTo(notification.getMessage());
        assertThat(response.read()).isEqualTo(notification.isRead());
        assertThat(response.referenceId()).isEqualTo(notification.getReferenceId());
        assertThat(response.referenceType()).isEqualTo(notification.getReferenceType());
        assertThat(response.createdAt()).isEqualTo(notification.getCreatedAt());
    }
}
