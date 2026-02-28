package com.familyhobbies.associationservice.service;

import com.familyhobbies.associationservice.dto.request.ActivityRequest;
import com.familyhobbies.associationservice.dto.request.SessionRequest;
import com.familyhobbies.associationservice.dto.response.ActivityDetailResponse;
import com.familyhobbies.associationservice.dto.response.ActivityResponse;
import com.familyhobbies.associationservice.dto.response.SessionResponse;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for activity and session operations.
 */
public interface ActivityService {

    /**
     * Lists activities for an association with optional filters.
     */
    Page<ActivityResponse> listActivities(Long associationId, AssociationCategory category,
                                           ActivityLevel level, Pageable pageable);

    /**
     * Gets activity detail with embedded sessions.
     */
    ActivityDetailResponse getActivityDetail(Long associationId, Long activityId);

    /**
     * Creates a new activity for an association.
     */
    ActivityDetailResponse createActivity(Long associationId, ActivityRequest request);

    /**
     * Updates an existing activity.
     */
    ActivityDetailResponse updateActivity(Long associationId, Long activityId, ActivityRequest request);

    /**
     * Soft-deletes an activity (sets status to CANCELLED).
     */
    void deleteActivity(Long associationId, Long activityId);

    /**
     * Lists sessions for an activity.
     */
    List<SessionResponse> listSessions(Long associationId, Long activityId);

    /**
     * Adds a session to an activity.
     */
    SessionResponse createSession(Long associationId, Long activityId, SessionRequest request);

    /**
     * Updates an existing session.
     */
    SessionResponse updateSession(Long associationId, Long activityId, Long sessionId, SessionRequest request);

    /**
     * Deactivates a session (sets active to false).
     */
    void deleteSession(Long associationId, Long activityId, Long sessionId);
}
