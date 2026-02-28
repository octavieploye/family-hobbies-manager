package com.familyhobbies.associationservice.mapper;

import com.familyhobbies.associationservice.dto.request.ActivityRequest;
import com.familyhobbies.associationservice.dto.request.SessionRequest;
import com.familyhobbies.associationservice.dto.response.ActivityDetailResponse;
import com.familyhobbies.associationservice.dto.response.ActivityResponse;
import com.familyhobbies.associationservice.dto.response.SessionResponse;
import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Maps {@link Activity} and {@link Session} entities to/from DTOs.
 * Manual mapper (no MapStruct) for full control and transparency.
 */
@Component
public class ActivityMapper {

    /**
     * Maps an activity entity to a summary response (list view).
     */
    public ActivityResponse toResponse(Activity entity) {
        if (entity == null) {
            return null;
        }
        int sessionCount = entity.getSessions() != null ? entity.getSessions().size() : 0;
        return new ActivityResponse(
            entity.getId(),
            entity.getName(),
            entity.getCategory(),
            entity.getLevel(),
            entity.getMinAge(),
            entity.getMaxAge(),
            entity.getPriceCents(),
            entity.getStatus(),
            sessionCount
        );
    }

    /**
     * Maps an activity entity to a detailed response with embedded sessions.
     */
    public ActivityDetailResponse toDetailResponse(Activity entity) {
        if (entity == null) {
            return null;
        }
        Association association = entity.getAssociation();
        List<SessionResponse> sessions = entity.getSessions() != null
            ? entity.getSessions().stream().map(this::toSessionResponse).toList()
            : Collections.emptyList();

        return new ActivityDetailResponse(
            entity.getId(),
            association != null ? association.getId() : null,
            association != null ? association.getName() : null,
            entity.getName(),
            entity.getDescription(),
            entity.getCategory(),
            entity.getLevel(),
            entity.getMinAge(),
            entity.getMaxAge(),
            entity.getMaxCapacity(),
            entity.getPriceCents(),
            entity.getSeasonStart(),
            entity.getSeasonEnd(),
            entity.getStatus(),
            sessions,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * Maps a session entity to a response DTO.
     */
    public SessionResponse toSessionResponse(Session entity) {
        if (entity == null) {
            return null;
        }
        return new SessionResponse(
            entity.getId(),
            entity.getActivity() != null ? entity.getActivity().getId() : null,
            entity.getDayOfWeek(),
            entity.getStartTime(),
            entity.getEndTime(),
            entity.getLocation(),
            entity.getInstructorName(),
            entity.getMaxCapacity(),
            entity.isActive()
        );
    }

    /**
     * Creates a new Activity entity from a request DTO.
     * The association must be set separately by the caller.
     */
    public Activity toEntity(ActivityRequest request, Association association) {
        if (request == null) {
            return null;
        }
        return Activity.builder()
            .association(association)
            .name(request.name())
            .description(request.description())
            .category(request.category())
            .level(request.level() != null ? request.level() : ActivityLevel.ALL_LEVELS)
            .minAge(request.minAge())
            .maxAge(request.maxAge())
            .maxCapacity(request.maxCapacity())
            .priceCents(request.priceCents())
            .seasonStart(request.seasonStart())
            .seasonEnd(request.seasonEnd())
            .build();
    }

    /**
     * Updates an existing Activity entity from a request DTO.
     */
    public void updateEntity(Activity entity, ActivityRequest request) {
        if (entity == null || request == null) {
            return;
        }
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setCategory(request.category());
        entity.setLevel(request.level() != null ? request.level() : ActivityLevel.ALL_LEVELS);
        entity.setMinAge(request.minAge());
        entity.setMaxAge(request.maxAge());
        entity.setMaxCapacity(request.maxCapacity());
        entity.setPriceCents(request.priceCents());
        entity.setSeasonStart(request.seasonStart());
        entity.setSeasonEnd(request.seasonEnd());
    }

    /**
     * Creates a new Session entity from a request DTO.
     * The activity must be set separately by the caller.
     */
    public Session toSessionEntity(SessionRequest request, Activity activity) {
        if (request == null) {
            return null;
        }
        return Session.builder()
            .activity(activity)
            .dayOfWeek(request.dayOfWeek())
            .startTime(request.startTime())
            .endTime(request.endTime())
            .location(request.location())
            .instructorName(request.instructorName())
            .maxCapacity(request.maxCapacity())
            .build();
    }

    /**
     * Updates an existing Session entity from a request DTO.
     */
    public void updateSessionEntity(Session entity, SessionRequest request) {
        if (entity == null || request == null) {
            return;
        }
        entity.setDayOfWeek(request.dayOfWeek());
        entity.setStartTime(request.startTime());
        entity.setEndTime(request.endTime());
        entity.setLocation(request.location());
        entity.setInstructorName(request.instructorName());
        entity.setMaxCapacity(request.maxCapacity());
    }
}
