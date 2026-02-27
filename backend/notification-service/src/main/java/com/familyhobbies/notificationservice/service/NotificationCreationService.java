package com.familyhobbies.notificationservice.service;

import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import com.familyhobbies.notificationservice.entity.enums.NotificationType;

/**
 * Service for creating notifications and optionally sending emails
 * based on user preferences.
 */
public interface NotificationCreationService {

    /**
     * Creates a notification in the database and optionally sends an email
     * if the user's preferences allow it.
     *
     * @param userId        the target user ID
     * @param type          notification delivery type
     * @param category      notification category
     * @param title         notification title
     * @param message       notification message body
     * @param referenceId   optional reference to related entity
     * @param referenceType optional type of related entity
     * @return the persisted notification
     */
    Notification createNotification(Long userId, NotificationType type, NotificationCategory category,
                                    String title, String message,
                                    String referenceId, String referenceType);
}
