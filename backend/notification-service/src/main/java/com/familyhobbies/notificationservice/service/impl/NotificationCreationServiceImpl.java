package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import com.familyhobbies.notificationservice.entity.enums.NotificationType;
import com.familyhobbies.notificationservice.repository.NotificationPreferenceRepository;
import com.familyhobbies.notificationservice.repository.NotificationRepository;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCreationServiceImpl implements NotificationCreationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    @Override
    @Transactional
    public Notification createNotification(Long userId, NotificationType type, NotificationCategory category,
                                           String title, String message,
                                           String referenceId, String referenceType) {
        // Check user preferences for this category
        Optional<NotificationPreference> preference =
                notificationPreferenceRepository.findByUserIdAndCategory(userId, category);

        boolean shouldCreateInApp = preference
                .map(NotificationPreference::isInAppEnabled)
                .orElse(true); // Default: enabled

        if (!shouldCreateInApp && type == NotificationType.IN_APP) {
            log.debug("In-app notification disabled for user {} category {}", userId, category);
            return null;
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .category(category)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .read(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Notification created: id={}, userId={}, category={}", saved.getId(), userId, category);
        return saved;
    }
}
