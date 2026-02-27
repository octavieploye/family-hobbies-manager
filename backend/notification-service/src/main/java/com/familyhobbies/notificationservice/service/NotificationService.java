package com.familyhobbies.notificationservice.service;

import com.familyhobbies.notificationservice.dto.request.NotificationPreferenceRequest;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.dto.response.UnreadCountResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service for managing notifications and user preferences.
 */
public interface NotificationService {

    Page<NotificationResponse> getNotifications(Long userId, Pageable pageable);

    UnreadCountResponse getUnreadCount(Long userId);

    void markAsRead(Long notificationId, Long userId);

    void markAllAsRead(Long userId);

    List<NotificationPreferenceResponse> getPreferences(Long userId);

    NotificationPreferenceResponse updatePreference(Long userId, NotificationPreferenceRequest request);
}
