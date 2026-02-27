package com.familyhobbies.notificationservice.mapper;

import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import org.springframework.stereotype.Component;

/**
 * Maps notification entities to response DTOs.
 */
@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getType(),
                notification.getCategory(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getReferenceId(),
                notification.getReferenceType(),
                notification.getCreatedAt(),
                notification.getReadAt()
        );
    }

    public NotificationPreferenceResponse toPreferenceResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
                preference.getId(),
                preference.getUserId(),
                preference.getCategory(),
                preference.isEmailEnabled(),
                preference.isInAppEnabled()
        );
    }
}
