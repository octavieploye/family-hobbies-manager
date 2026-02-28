package com.familyhobbies.associationservice.service.impl;

import com.familyhobbies.associationservice.dto.request.ActivityRequest;
import com.familyhobbies.associationservice.dto.request.SessionRequest;
import com.familyhobbies.associationservice.dto.response.ActivityDetailResponse;
import com.familyhobbies.associationservice.dto.response.ActivityResponse;
import com.familyhobbies.associationservice.dto.response.SessionResponse;
import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.mapper.ActivityMapper;
import com.familyhobbies.associationservice.repository.ActivityRepository;
import com.familyhobbies.associationservice.repository.AssociationRepository;
import com.familyhobbies.associationservice.repository.SessionRepository;
import com.familyhobbies.associationservice.service.ActivityService;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link ActivityService}.
 * Handles activity CRUD with association-scoped lookups and session management.
 */
@Service
@Transactional(readOnly = true)
public class ActivityServiceImpl implements ActivityService {

    private final ActivityRepository activityRepository;
    private final SessionRepository sessionRepository;
    private final AssociationRepository associationRepository;
    private final ActivityMapper activityMapper;

    public ActivityServiceImpl(ActivityRepository activityRepository,
                                SessionRepository sessionRepository,
                                AssociationRepository associationRepository,
                                ActivityMapper activityMapper) {
        this.activityRepository = activityRepository;
        this.sessionRepository = sessionRepository;
        this.associationRepository = associationRepository;
        this.activityMapper = activityMapper;
    }

    @Override
    public Page<ActivityResponse> listActivities(Long associationId, AssociationCategory category,
                                                  ActivityLevel level, Pageable pageable) {
        verifyAssociationExists(associationId);

        Page<Activity> activities;
        if (category != null && level != null) {
            activities = activityRepository.findByAssociation_IdAndCategoryAndLevel(
                associationId, category, level, pageable);
        } else if (category != null) {
            activities = activityRepository.findByAssociation_IdAndCategory(
                associationId, category, pageable);
        } else if (level != null) {
            activities = activityRepository.findByAssociation_IdAndLevel(
                associationId, level, pageable);
        } else {
            activities = activityRepository.findByAssociation_Id(associationId, pageable);
        }
        return activities.map(activityMapper::toResponse);
    }

    @Override
    public ActivityDetailResponse getActivityDetail(Long associationId, Long activityId) {
        Activity activity = findActivityByAssociation(associationId, activityId);
        return activityMapper.toDetailResponse(activity);
    }

    @Override
    @Transactional
    public ActivityDetailResponse createActivity(Long associationId, ActivityRequest request) {
        Association association = associationRepository.findById(associationId)
            .orElseThrow(() -> ResourceNotFoundException.of("Association", associationId));

        Activity activity = activityMapper.toEntity(request, association);
        Activity saved = activityRepository.save(activity);
        return activityMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional
    public ActivityDetailResponse updateActivity(Long associationId, Long activityId, ActivityRequest request) {
        Activity activity = findActivityByAssociation(associationId, activityId);
        activityMapper.updateEntity(activity, request);
        Activity saved = activityRepository.save(activity);
        return activityMapper.toDetailResponse(saved);
    }

    @Override
    @Transactional
    public void deleteActivity(Long associationId, Long activityId) {
        Activity activity = findActivityByAssociation(associationId, activityId);
        activity.setStatus(ActivityStatus.CANCELLED);
        activityRepository.save(activity);
    }

    @Override
    public List<SessionResponse> listSessions(Long associationId, Long activityId) {
        findActivityByAssociation(associationId, activityId);
        List<Session> sessions = sessionRepository.findByActivity_IdAndActiveTrue(activityId);
        return sessions.stream().map(activityMapper::toSessionResponse).toList();
    }

    @Override
    @Transactional
    public SessionResponse createSession(Long associationId, Long activityId, SessionRequest request) {
        Activity activity = findActivityByAssociation(associationId, activityId);
        Session session = activityMapper.toSessionEntity(request, activity);
        Session saved = sessionRepository.save(session);
        return activityMapper.toSessionResponse(saved);
    }

    @Override
    @Transactional
    public SessionResponse updateSession(Long associationId, Long activityId,
                                          Long sessionId, SessionRequest request) {
        findActivityByAssociation(associationId, activityId);
        Session session = sessionRepository.findByIdAndActivity_Id(sessionId, activityId)
            .orElseThrow(() -> ResourceNotFoundException.of("Session", sessionId));
        activityMapper.updateSessionEntity(session, request);
        Session saved = sessionRepository.save(session);
        return activityMapper.toSessionResponse(saved);
    }

    @Override
    @Transactional
    public void deleteSession(Long associationId, Long activityId, Long sessionId) {
        findActivityByAssociation(associationId, activityId);
        Session session = sessionRepository.findByIdAndActivity_Id(sessionId, activityId)
            .orElseThrow(() -> ResourceNotFoundException.of("Session", sessionId));
        session.setActive(false);
        sessionRepository.save(session);
    }

    private Activity findActivityByAssociation(Long associationId, Long activityId) {
        return activityRepository.findByIdAndAssociation_Id(activityId, associationId)
            .orElseThrow(() -> ResourceNotFoundException.of("Activity", activityId));
    }

    private void verifyAssociationExists(Long associationId) {
        if (!associationRepository.existsById(associationId)) {
            throw ResourceNotFoundException.of("Association", associationId);
        }
    }
}
