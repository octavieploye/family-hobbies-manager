package com.familyhobbies.notificationservice.repository;

import com.familyhobbies.notificationservice.entity.NotificationPreference;
import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    Optional<NotificationPreference> findByUserIdAndCategory(Long userId, NotificationCategory category);

    List<NotificationPreference> findByUserId(Long userId);
}
