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
 * Member names are pulled from the Subscription entity, which stores
 * memberFirstName and memberLastName at subscription creation time
 * (denormalized to avoid cross-service calls).
 */
@Component
public class AttendanceMapper {

    /**
     * Maps an attendance entity to a response DTO.
     * Member names are pulled from the subscription entity.
     */
    public AttendanceResponse toResponse(Attendance entity) {
        if (entity == null) {
            return null;
        }
        Subscription subscription = entity.getSubscription();
        return new AttendanceResponse(
            entity.getId(),
            entity.getSession() != null ? entity.getSession().getId() : null,
            entity.getFamilyMemberId(),
            subscription != null ? subscription.getMemberFirstName() : null,
            subscription != null ? subscription.getMemberLastName() : null,
            subscription != null ? subscription.getId() : null,
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
