package com.familyhobbies.notificationservice.dto.request;

import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceRequest(
        @NotNull(message = "Category is required")
        NotificationCategory category,
        boolean emailEnabled,
        boolean inAppEnabled
) {
}
