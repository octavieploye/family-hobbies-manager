package com.familyhobbies.notificationservice.service.impl;

import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import com.familyhobbies.notificationservice.dto.request.NotificationPreferenceRequest;
import com.familyhobbies.notificationservice.dto.response.NotificationPreferenceResponse;
import com.familyhobbies.notificationservice.dto.response.NotificationResponse;
import com.familyhobbies.notificationservice.dto.response.UnreadCountResponse;
import com.familyhobbies.notificationservice.entity.Notification;
import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.mapper.NotificationMapper;
import com.familyhobbies.notificationservice.repository.NotificationPreferenceRepository;
import com.familyhobbies.notificationservice.repository.NotificationRepository;
import com.familyhobbies.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(notificationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        int count = notificationRepository.countByUserIdAndReadFalse(userId);
        return new UnreadCountResponse(count);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Notification", notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw ResourceNotFoundException.of("Notification", notificationId);
        }

        notification.setRead(true);
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);
        log.info("Notification {} marked as read for user {}", notificationId, userId);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        int count = notificationRepository.markAllAsReadByUserId(userId, Instant.now());
        log.info("Marked {} notifications as read for user {}", count, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(Long userId) {
        List<NotificationPreference> preferences = notificationPreferenceRepository.findByUserId(userId);
        return preferences.stream()
                .map(notificationMapper::toPreferenceResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public NotificationPreferenceResponse updatePreference(Long userId, NotificationPreferenceRequest request) {
        NotificationPreference preference = notificationPreferenceRepository
                .findByUserIdAndCategory(userId, request.category())
                .orElseGet(() -> {
                    // Create default preference if not found
                    log.info("Creating default notification preference for user {} category {}",
                            userId, request.category());
                    return NotificationPreference.builder()
                            .userId(userId)
                            .category(request.category())
                            .build();
                });

        preference.setEmailEnabled(request.emailEnabled());
        preference.setInAppEnabled(request.inAppEnabled());

        NotificationPreference saved = notificationPreferenceRepository.save(preference);
        return notificationMapper.toPreferenceResponse(saved);
    }
}
