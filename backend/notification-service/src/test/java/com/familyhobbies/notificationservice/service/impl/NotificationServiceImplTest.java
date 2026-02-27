package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.notificationservice.dto.request.NotificationPreferenceRequest;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.dto.response.UnreadCountResponse;
import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import com.familyhobbies.notificationservice.entity.enums.NotificationType;
import com.familyhobbies.notificationservice.mapper.NotificationMapper;
import com.familyhobbies.notificationservice.repository.NotificationPreferenceRepository;
import com.familyhobbies.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NotificationServiceImpl.
 *
 * Stories: S6-001, S6-003 -- Notification CRUD + Preferences
 * Tests: 8 test methods
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository notificationPreferenceRepository;

    @Spy
    private NotificationMapper notificationMapper = new NotificationMapper();

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private Notification testNotification;
    private NotificationPreference testPreference;

    @BeforeEach
    void setUp() {
        testNotification = Notification.builder()
                .id(1L)
                .userId(10L)
                .type(NotificationType.IN_APP)
                .category(NotificationCategory.WELCOME)
                .title("Bienvenue")
                .message("Bienvenue sur la plateforme")
                .read(false)
                .createdAt(Instant.now())
                .build();

        testPreference = NotificationPreference.builder()
                .id(1L)
                .userId(10L)
                .category(NotificationCategory.WELCOME)
                .emailEnabled(true)
                .inAppEnabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("should return paged notifications when user has notifications")
    void should_returnPagedNotifications_when_userHasNotifications() {
        // given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> page = new PageImpl<>(List.of(testNotification), pageable, 1);
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(10L, pageable)).thenReturn(page);

        // when
        Page<NotificationResponse> result = notificationService.getNotifications(10L, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Bienvenue");
        verify(notificationRepository).findByUserIdOrderByCreatedAtDesc(10L, pageable);
    }

    @Test
    @DisplayName("should return unread count when user has unread")
    void should_returnUnreadCount_when_userHasUnread() {
        // given
        when(notificationRepository.countByUserIdAndReadFalse(10L)).thenReturn(5);

        // when
        UnreadCountResponse result = notificationService.getUnreadCount(10L);

        // then
        assertThat(result.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("should mark as read when valid notification")
    void should_markAsRead_when_validNotification() {
        // given
        when(notificationRepository.findById(1L)).thenReturn(Optional.of(testNotification));
        when(notificationRepository.save(any(Notification.class))).thenReturn(testNotification);

        // when
        notificationService.markAsRead(1L, 10L);

        // then
        assertThat(testNotification.isRead()).isTrue();
        assertThat(testNotification.getReadAt()).isNotNull();
        verify(notificationRepository).save(testNotification);
    }

    @Test
    @DisplayName("should mark all as read when called by user")
    void should_markAllAsRead_when_calledByUser() {
        // given
        when(notificationRepository.markAllAsReadByUserId(eq(10L), any(Instant.class))).thenReturn(3);

        // when
        notificationService.markAllAsRead(10L);

        // then
        verify(notificationRepository).markAllAsReadByUserId(eq(10L), any(Instant.class));
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when notification not found")
    void should_throwResourceNotFoundException_when_notificationNotFound() {
        // given
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(99L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification not found with id: 99");
    }

    @Test
    @DisplayName("should return preferences when user has preferences")
    void should_returnPreferences_when_userHasPreferences() {
        // given
        when(notificationPreferenceRepository.findByUserId(10L)).thenReturn(List.of(testPreference));

        // when
        List<NotificationPreferenceResponse> result = notificationService.getPreferences(10L);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo(NotificationCategory.WELCOME);
        assertThat(result.get(0).emailEnabled()).isTrue();
    }

    @Test
    @DisplayName("should update preference when valid request")
    void should_updatePreference_when_validRequest() {
        // given
        NotificationPreferenceRequest request = new NotificationPreferenceRequest(
                NotificationCategory.WELCOME, false, true);
        when(notificationPreferenceRepository.findByUserIdAndCategory(10L, NotificationCategory.WELCOME))
                .thenReturn(Optional.of(testPreference));
        when(notificationPreferenceRepository.save(any(NotificationPreference.class))).thenReturn(testPreference);

        // when
        NotificationPreferenceResponse result = notificationService.updatePreference(10L, request);

        // then
        verify(notificationPreferenceRepository).save(any(NotificationPreference.class));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should create default preference when not found")
    void should_createDefaultPreference_when_notFound() {
        // given
        NotificationPreferenceRequest request = new NotificationPreferenceRequest(
                NotificationCategory.PAYMENT, true, false);
        when(notificationPreferenceRepository.findByUserIdAndCategory(10L, NotificationCategory.PAYMENT))
                .thenReturn(Optional.empty());

        NotificationPreference newPreference = NotificationPreference.builder()
                .id(2L)
                .userId(10L)
                .category(NotificationCategory.PAYMENT)
                .emailEnabled(true)
                .inAppEnabled(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(notificationPreferenceRepository.save(any(NotificationPreference.class))).thenReturn(newPreference);

        // when
        NotificationPreferenceResponse result = notificationService.updatePreference(10L, request);

        // then
        assertThat(result).isNotNull();
        assertThat(result.category()).isEqualTo(NotificationCategory.PAYMENT);
        verify(notificationPreferenceRepository).save(any(NotificationPreference.class));
    }
}
