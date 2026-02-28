package com.familyhobbies.associationservice.mapper;

import com.familyhobbies.associationservice.dto.request.MarkAttendanceRequest;
import com.familyhobbies.associationservice.dto.response.AttendanceResponse;
import com.familyhobbies.associationservice.entity.Attendance;
import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.Subscription;
import org.springframework.stereotype.Component;

/**
 * Maps {@link Attendance} entities to/from DTOs.
 * Manual mapper (no MapStruct) for full control and transparency.
 *
 * Note: memberFirstName and memberLastName are cross-service fields
 * that must be populated by the caller (e.g., service layer),
 * since attendance lives in association-service but member names
 * live in user-service. For now, they are set to null in the mapper
 * and populated by the service when available.
 */
@Component
public class AttendanceMapper {

    /**
     * Maps an attendance entity to a response DTO.
     * Member names are set to null here -- the service layer populates them
     * if cross-service data is available.
     */
    public AttendanceResponse toResponse(Attendance entity) {
        if (entity == null) {
            return null;
        }
        return new AttendanceResponse(
            entity.getId(),
            entity.getSession() != null ? entity.getSession().getId() : null,
            entity.getFamilyMemberId(),
            null, // memberFirstName - cross-service, populated by service if available
            null, // memberLastName - cross-service, populated by service if available
            entity.getSubscription() != null ? entity.getSubscription().getId() : null,
            entity.getSessionDate(),
            entity.getStatus(),
            entity.getNote(),
            entity.getMarkedBy(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * Creates a new Attendance entity from a request DTO.
     * Session, Subscription entities, and markedBy must be set by the caller.
     */
    public Attendance toEntity(MarkAttendanceRequest request, Session session,
                                Subscription subscription, Long markedBy) {
        if (request == null) {
            return null;
        }
        return Attendance.builder()
            .session(session)
            .familyMemberId(request.familyMemberId())
            .subscription(subscription)
            .sessionDate(request.sessionDate())
            .status(request.status())
            .note(request.note())
            .markedBy(markedBy)
            .build();
    }
}
