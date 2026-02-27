package com.familyhobbies.notificationservice.dto.response;

import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import com.familyhobbies.notificationservice.entity.enums.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long userId,
        NotificationType type,
        NotificationCategory category,
        String title,
        String message,
        boolean read,
        String referenceId,
        String referenceType,
        Instant createdAt,
        Instant readAt
) {
}
