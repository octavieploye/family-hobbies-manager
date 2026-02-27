package com.familyhobbies.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Event published when attendance is marked for a session.
 * Consumed by notification-service to send attendance confirmation.
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AttendanceMarkedEvent extends DomainEvent {

    private Long attendanceId;
    private Long memberId;
    private Long sessionId;
    private String status;

    public AttendanceMarkedEvent(Long attendanceId, Long memberId,
                                  Long sessionId, String status) {
        super("ATTENDANCE_MARKED");
        this.attendanceId = attendanceId;
        this.memberId = memberId;
        this.sessionId = sessionId;
        this.status = status;
    }
}
