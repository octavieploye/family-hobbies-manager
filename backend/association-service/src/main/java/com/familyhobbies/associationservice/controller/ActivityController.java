package com.familyhobbies.associationservice.controller;

import com.familyhobbies.associationservice.dto.request.ActivityRequest;
import com.familyhobbies.associationservice.dto.request.SessionRequest;
import com.familyhobbies.associationservice.dto.response.ActivityDetailResponse;
import com.familyhobbies.associationservice.dto.response.ActivityResponse;
import com.familyhobbies.associationservice.dto.response.SessionResponse;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.service.ActivityService;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * REST controller for activity and session CRUD operations.
 * Activities are scoped under an association.
 *
 * Path prefix: /api/v1/associations/{associationId}/activities
 *
 * Public endpoints: GET (list, detail, sessions)
 * Protected endpoints: POST, PUT, DELETE (require ADMIN or ASSOCIATION role via X-User-Roles header)
 */
@RestController
@RequestMapping("/api/v1/associations/{associationId}/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    /**
     * List activities for an association (public, paginated, filterable).
     * GET /api/v1/associations/{associationId}/activities
     */
    @GetMapping
    public ResponseEntity<Page<ActivityResponse>> listActivities(
            @PathVariable Long associationId,
            @RequestParam(required = false) AssociationCategory category,
            @RequestParam(required = false) ActivityLevel level,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<ActivityResponse> result = activityService.listActivities(associationId, category, level, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * Get activity detail with sessions (public).
     * GET /api/v1/associations/{associationId}/activities/{activityId}
     */
    @GetMapping("/{activityId}")
    public ResponseEntity<ActivityDetailResponse> getActivityDetail(
            @PathVariable Long associationId,
            @PathVariable Long activityId) {

        ActivityDetailResponse result = activityService.getActivityDetail(associationId, activityId);
        return ResponseEntity.ok(result);
    }

    /**
     * Create a new activity (ADMIN or ASSOCIATION role).
     * POST /api/v1/associations/{associationId}/activities
     */
    @PostMapping
    public ResponseEntity<ActivityDetailResponse> createActivity(
            @PathVariable Long associationId,
            @Valid @RequestBody ActivityRequest request,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateAdminOrAssociationRole(roles);
        ActivityDetailResponse result = activityService.createActivity(associationId, request);
        URI location = URI.create("/api/v1/associations/" + associationId + "/activities/" + result.id());
        return ResponseEntity.created(location).body(result);
    }

    /**
     * Update an existing activity (ADMIN or ASSOCIATION role).
     * PUT /api/v1/associations/{associationId}/activities/{activityId}
     */
    @PutMapping("/{activityId}")
    public ResponseEntity<ActivityDetailResponse> updateActivity(
            @PathVariable Long associationId,
            @PathVariable Long activityId,
            @Valid @RequestBody ActivityRequest request,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateAdminOrAssociationRole(roles);
        ActivityDetailResponse result = activityService.updateActivity(associationId, activityId, request);
        return ResponseEntity.ok(result);
    }

    /**
     * Soft-delete an activity (ADMIN only, sets status to CANCELLED).
     * DELETE /api/v1/associations/{associationId}/activities/{activityId}
     */
    @DeleteMapping("/{activityId}")
    public ResponseEntity<Void> deleteActivity(
            @PathVariable Long associationId,
            @PathVariable Long activityId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateAdminOrAssociationRole(roles);
        activityService.deleteActivity(associationId, activityId);
        return ResponseEntity.noContent().build();
    }

    /**
     * List sessions for an activity (public).
     * GET /api/v1/associations/{associationId}/activities/{activityId}/sessions
     */
    @GetMapping("/{activityId}/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(
            @PathVariable Long associationId,
            @PathVariable Long activityId) {

        List<SessionResponse> result = activityService.listSessions(associationId, activityId);
        return ResponseEntity.ok(result);
    }

    /**
     * Add a session to an activity (ADMIN or ASSOCIATION role).
     * POST /api/v1/associations/{associationId}/activities/{activityId}/sessions
     */
    @PostMapping("/{activityId}/sessions")
    public ResponseEntity<SessionResponse> createSession(
            @PathVariable Long associationId,
            @PathVariable Long activityId,
            @Valid @RequestBody SessionRequest request,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateAdminOrAssociationRole(roles);
        SessionResponse result = activityService.createSession(associationId, activityId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Update a session (ADMIN or ASSOCIATION role).
     * PUT /api/v1/associations/{associationId}/activities/{activityId}/sessions/{sessionId}
     */
    @PutMapping("/{activityId}/sessions/{sessionId}")
    public ResponseEntity<SessionResponse> updateSession(
            @PathVariable Long associationId,
            @PathVariable Long activityId,
            @PathVariable Long sessionId,
            @Valid @RequestBody SessionRequest request,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateAdminOrAssociationRole(roles);
        SessionResponse result = activityService.updateSession(associationId, activityId, sessionId, request);
        return ResponseEntity.ok(result);
    }

    /**
     * Deactivate a session (ADMIN only, sets active to false).
     * DELETE /api/v1/associations/{associationId}/activities/{activityId}/sessions/{sessionId}
     */
    @DeleteMapping("/{activityId}/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable Long associationId,
            @PathVariable Long activityId,
            @PathVariable Long sessionId,
            @RequestHeader(value = "X-User-Roles", defaultValue = "") String roles) {

        validateAdminOrAssociationRole(roles);
        activityService.deleteSession(associationId, activityId, sessionId);
        return ResponseEntity.noContent().build();
    }

    private void validateAdminOrAssociationRole(String roles) {
        if (roles == null || (!roles.contains("ADMIN") && !roles.contains("ASSOCIATION"))) {
            throw new ForbiddenException("ADMIN or ASSOCIATION role required to perform this action");
        }
    }
}
