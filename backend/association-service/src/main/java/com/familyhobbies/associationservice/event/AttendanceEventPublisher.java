package com.familyhobbies.associationservice.event;

import com.familyhobbies.associationservice.entity.Attendance;
import com.familyhobbies.common.event.AttendanceMarkedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes attendance-related domain events to Kafka topics.
 *
 * Uses fire-and-forget pattern: Kafka failures are logged but never
 * re-thrown, so that core attendance operations are never blocked
 * by messaging infrastructure issues.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttendanceEventPublisher {

    private static final String ATTENDANCE_MARKED_TOPIC = "family-hobbies.attendance.marked";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes an AttendanceMarkedEvent to Kafka.
     * Fire-and-forget: logs errors but never throws.
     *
     * @param attendance the marked attendance entity
     */
    public void publishAttendanceMarked(Attendance attendance) {
        try {
            AttendanceMarkedEvent event = new AttendanceMarkedEvent(
                attendance.getId(),
                attendance.getFamilyMemberId(),
                attendance.getSession() != null ? attendance.getSession().getId() : null,
                attendance.getStatus().name()
            );

            kafkaTemplate.send(ATTENDANCE_MARKED_TOPIC,
                    String.valueOf(attendance.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish AttendanceMarkedEvent for attendanceId={}: {}",
                                attendance.getId(), ex.getMessage());
                    } else {
                        log.info("Published AttendanceMarkedEvent for attendanceId={} to topic={}",
                                attendance.getId(), ATTENDANCE_MARKED_TOPIC);
                    }
                });
        } catch (Exception e) {
            // Fire-and-forget: log but do NOT re-throw
            log.error("Failed to send AttendanceMarkedEvent for attendanceId={}: {}",
                    attendance.getId(), e.getMessage(), e);
        }
    }
}
