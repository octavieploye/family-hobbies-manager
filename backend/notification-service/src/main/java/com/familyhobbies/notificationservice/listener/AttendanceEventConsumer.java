package com.familyhobbies.notificationservice.listener;

import com.familyhobbies.common.event.AttendanceMarkedEvent;
import com.familyhobbies.notificationservice.entity.enums.NotificationCategory;
import com.familyhobbies.notificationservice.entity.enums.NotificationType;
import com.familyhobbies.notificationservice.service.NotificationCreationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for attendance-related events.
 * Creates in-app attendance notifications only (no email).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceEventConsumer {

    private final NotificationCreationService notificationCreationService;

    @KafkaListener(topics = "family-hobbies.attendance.marked", groupId = "notification-service-group")
    public void handleAttendanceMarked(AttendanceMarkedEvent event) {
        log.info("Received AttendanceMarkedEvent: attendanceId={}, memberId={}, status={}",
                event.getAttendanceId(), event.getMemberId(), event.getStatus());

        notificationCreationService.createNotification(
                event.getMemberId(),
                NotificationType.IN_APP,
                NotificationCategory.ATTENDANCE,
                "Presence enregistree",
                "Votre presence a ete enregistree pour la session " + event.getSessionId() +
                        ". Statut : " + event.getStatus(),
                String.valueOf(event.getAttendanceId()),
                "ATTENDANCE"
        );
    }
}
