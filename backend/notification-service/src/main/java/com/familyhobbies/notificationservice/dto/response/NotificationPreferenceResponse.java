package com.familyhobbies.notificationservice.dto.response;

import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;

public record NotificationPreferenceResponse(
        Long id,
        Long userId,
        NotificationCategory category,
        boolean emailEnabled,
        boolean inAppEnabled
) {
}
