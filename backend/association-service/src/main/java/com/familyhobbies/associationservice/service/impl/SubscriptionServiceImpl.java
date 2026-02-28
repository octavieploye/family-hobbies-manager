package com.familyhobbies.associationservice.service.impl;

import com.familyhobbies.associationservice.dto.request.SubscriptionRequest;
import com.familyhobbies.associationservice.dto.response.SubscriptionResponse;
import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.event.SubscriptionEventPublisher;
import com.familyhobbies.associationservice.mapper.SubscriptionMapper;
import com.familyhobbies.associationservice.repository.ActivityRepository;
import com.familyhobbies.associationservice.repository.SubscriptionRepository;
import com.familyhobbies.associationservice.service.SubscriptionService;
import com.familyhobbies.errorhandling.exception.web.BadRequestException;
import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implementation of {@link SubscriptionService}.
 * Handles subscription lifecycle with business rules enforcement.
 */
@Service
@Transactional(readOnly = true)
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final ActivityRepository activityRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final SubscriptionEventPublisher eventPublisher;

    public SubscriptionServiceImpl(SubscriptionRepository subscriptionRepository,
                                    ActivityRepository activityRepository,
                                    SubscriptionMapper subscriptionMapper,
                                    SubscriptionEventPublisher eventPublisher) {
        this.subscriptionRepository = subscriptionRepository;
        this.activityRepository = activityRepository;
        this.subscriptionMapper = subscriptionMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SubscriptionResponse createSubscription(SubscriptionRequest request, Long userId) {
        Activity activity = activityRepository.findById(request.activityId())
            .orElseThrow(() -> ResourceNotFoundException.of("Activity", request.activityId()));

        validateActivityIsActive(activity);
        validateNoDuplicateSubscription(request.activityId(), request.familyMemberId());

        Subscription subscription = subscriptionMapper.toEntity(request, activity, userId);
        Subscription saved = subscriptionRepository.save(subscription);

        eventPublisher.publishSubscriptionCreated(saved);

        return subscriptionMapper.toResponse(saved);
    }

    @Override
    public List<SubscriptionResponse> findByFamilyId(Long familyId, Long userId) {
        List<Subscription> subscriptions = subscriptionRepository.findByFamilyId(familyId);
        validateOwnership(subscriptions, userId);
        return subscriptions.stream().map(subscriptionMapper::toResponse).toList();
    }

    @Override
    public List<SubscriptionResponse> findByMemberId(Long memberId, Long userId) {
        List<Subscription> subscriptions = subscriptionRepository.findByFamilyMemberId(memberId);
        validateOwnership(subscriptions, userId);
        return subscriptions.stream().map(subscriptionMapper::toResponse).toList();
    }

    @Override
    public SubscriptionResponse findById(Long subscriptionId, Long userId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> ResourceNotFoundException.of("Subscription", subscriptionId));
        validateSingleOwnership(subscription, userId);
        return subscriptionMapper.toResponse(subscription);
    }

    @Override
    @Transactional
    public SubscriptionResponse cancelSubscription(Long subscriptionId, Long userId, String reason) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> ResourceNotFoundException.of("Subscription", subscriptionId));
        validateSingleOwnership(subscription, userId);

        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancellationReason(reason);
        subscription.setCancelledAt(Instant.now());

        Subscription saved = subscriptionRepository.save(subscription);

        eventPublisher.publishSubscriptionCancelled(saved);

        return subscriptionMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public SubscriptionResponse activateSubscription(Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> ResourceNotFoundException.of("Subscription", subscriptionId));

        if (subscription.getStatus() != SubscriptionStatus.PENDING) {
            throw new BadRequestException(
                "Cannot activate subscription with status: " + subscription.getStatus());
        }

        subscription.setStatus(SubscriptionStatus.ACTIVE);
        Subscription saved = subscriptionRepository.save(subscription);
        return subscriptionMapper.toResponse(saved);
    }

    private void validateActivityIsActive(Activity activity) {
        if (activity.getStatus() != ActivityStatus.ACTIVE) {
            throw new BadRequestException(
                "Cannot subscribe to activity with status: " + activity.getStatus());
        }
    }

    private void validateNoDuplicateSubscription(Long activityId, Long familyMemberId) {
        List<SubscriptionStatus> activeStatuses = List.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE);
        List<Subscription> existing = subscriptionRepository
            .findByActivity_IdAndFamilyMemberIdAndStatusIn(activityId, familyMemberId, activeStatuses);

        if (!existing.isEmpty()) {
            throw new ConflictException(
                "Family member " + familyMemberId + " already has an active or pending subscription for activity " + activityId);
        }
    }

    private void validateOwnership(List<Subscription> subscriptions, Long userId) {
        if (!subscriptions.isEmpty() && !subscriptions.get(0).getUserId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to view these subscriptions");
        }
    }

    private void validateSingleOwnership(Subscription subscription, Long userId) {
        if (!subscription.getUserId().equals(userId)) {
            throw new ForbiddenException("You do not have permission to access this subscription");
        }
    }
}
