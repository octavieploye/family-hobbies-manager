package com.familyhobbies.associationservice.service.impl;

import com.familyhobbies.associationservice.dto.request.SubscriptionRequest;
import com.familyhobbies.associationservice.dto.response.SubscriptionResponse;
import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionType;
import com.familyhobbies.associationservice.event.SubscriptionEventPublisher;
import com.familyhobbies.associationservice.mapper.SubscriptionMapper;
import com.familyhobbies.associationservice.repository.ActivityRepository;
import com.familyhobbies.associationservice.repository.SubscriptionRepository;
import com.familyhobbies.errorhandling.exception.web.BadRequestException;
import com.familyhobbies.errorhandling.exception.web.ConflictException;
import com.familyhobbies.errorhandling.exception.web.ForbiddenException;
import com.familyhobbies.errorhandling.exception.web.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SubscriptionServiceImpl.
 *
 * Story: S3-003 -- Subscription Entity & Lifecycle
 * Tests: 14 test methods
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private SubscriptionMapper subscriptionMapper;

    @Mock
    private SubscriptionEventPublisher eventPublisher;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    private Activity testActivity;
    private Subscription testSubscription;
    private SubscriptionResponse testResponse;
    private SubscriptionRequest testRequest;

    @BeforeEach
    void setUp() {
        Association association = Association.builder()
            .id(1L)
            .name("Lyon Natation Metropole")
            .category(AssociationCategory.SPORT)
            .build();

        testActivity = Activity.builder()
            .id(1L)
            .association(association)
            .name("Natation enfants")
            .category(AssociationCategory.SPORT)
            .level(ActivityLevel.BEGINNER)
            .status(ActivityStatus.ACTIVE)
            .priceCents(18000)
            .build();

        testSubscription = Subscription.builder()
            .id(1L)
            .activity(testActivity)
            .familyMemberId(10L)
            .familyId(5L)
            .userId(100L)
            .memberFirstName("Lucas")
            .memberLastName("Dupont")
            .subscriptionType(SubscriptionType.ADHESION)
            .status(SubscriptionStatus.PENDING)
            .startDate(LocalDate.of(2025, 9, 1))
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        testResponse = new SubscriptionResponse(
            1L, 1L, "Natation enfants", "Lyon Natation Metropole",
            10L, "Lucas", "Dupont", 5L, 100L, SubscriptionType.ADHESION,
            SubscriptionStatus.PENDING, LocalDate.of(2025, 9, 1), null,
            null, null, Instant.now(), Instant.now()
        );

        testRequest = new SubscriptionRequest(
            1L, 10L, 5L, "Lucas", "Dupont", SubscriptionType.ADHESION,
            LocalDate.of(2025, 9, 1), null
        );
    }

    @Test
    @DisplayName("should_createSubscription_when_validRequest")
    void should_createSubscription_when_validRequest() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));
        when(subscriptionRepository.findByActivity_IdAndFamilyMemberIdAndStatusIn(
            eq(1L), eq(10L), any())).thenReturn(Collections.emptyList());
        when(subscriptionMapper.toEntity(testRequest, testActivity, 100L)).thenReturn(testSubscription);
        when(subscriptionRepository.save(testSubscription)).thenReturn(testSubscription);
        when(subscriptionMapper.toResponse(testSubscription)).thenReturn(testResponse);

        SubscriptionResponse result = subscriptionService.createSubscription(testRequest, 100L);

        assertThat(result.activityName()).isEqualTo("Natation enfants");
        assertThat(result.status()).isEqualTo(SubscriptionStatus.PENDING);
        verify(eventPublisher).publishSubscriptionCreated(testSubscription);
    }

    @Test
    @DisplayName("should_throwResourceNotFound_when_activityNotFound")
    void should_throwResourceNotFound_when_activityNotFound() {
        when(activityRepository.findById(999L)).thenReturn(Optional.empty());

        SubscriptionRequest request = new SubscriptionRequest(
            999L, 10L, 5L, "Lucas", "Dupont", SubscriptionType.ADHESION,
            LocalDate.of(2025, 9, 1), null
        );

        assertThatThrownBy(() -> subscriptionService.createSubscription(request, 100L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("should_throwBadRequest_when_activityNotActive")
    void should_throwBadRequest_when_activityNotActive() {
        Activity cancelledActivity = Activity.builder()
            .id(2L)
            .name("Cancelled")
            .status(ActivityStatus.CANCELLED)
            .priceCents(0)
            .build();

        when(activityRepository.findById(2L)).thenReturn(Optional.of(cancelledActivity));

        SubscriptionRequest request = new SubscriptionRequest(
            2L, 10L, 5L, "Lucas", "Dupont", SubscriptionType.ADHESION,
            LocalDate.of(2025, 9, 1), null
        );

        assertThatThrownBy(() -> subscriptionService.createSubscription(request, 100L))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("CANCELLED");
    }

    @Test
    @DisplayName("should_throwConflict_when_duplicateSubscription")
    void should_throwConflict_when_duplicateSubscription() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(testActivity));
        when(subscriptionRepository.findByActivity_IdAndFamilyMemberIdAndStatusIn(
            eq(1L), eq(10L), any())).thenReturn(List.of(testSubscription));

        assertThatThrownBy(() -> subscriptionService.createSubscription(testRequest, 100L))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already has an active or pending subscription");
    }

    @Test
    @DisplayName("should_findByFamilyId_when_validOwner")
    void should_findByFamilyId_when_validOwner() {
        when(subscriptionRepository.findByFamilyId(5L)).thenReturn(List.of(testSubscription));
        when(subscriptionMapper.toResponse(testSubscription)).thenReturn(testResponse);

        List<SubscriptionResponse> result = subscriptionService.findByFamilyId(5L, 100L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).familyId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("should_throwForbidden_when_notOwnerForFamilyList")
    void should_throwForbidden_when_notOwnerForFamilyList() {
        when(subscriptionRepository.findByFamilyId(5L)).thenReturn(List.of(testSubscription));

        assertThatThrownBy(() -> subscriptionService.findByFamilyId(5L, 999L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("should_findByMemberId_when_validOwner")
    void should_findByMemberId_when_validOwner() {
        when(subscriptionRepository.findByFamilyMemberId(10L)).thenReturn(List.of(testSubscription));
        when(subscriptionMapper.toResponse(testSubscription)).thenReturn(testResponse);

        List<SubscriptionResponse> result = subscriptionService.findByMemberId(10L, 100L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("should_findById_when_validOwner")
    void should_findById_when_validOwner() {
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));
        when(subscriptionMapper.toResponse(testSubscription)).thenReturn(testResponse);

        SubscriptionResponse result = subscriptionService.findById(1L, 100L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should_throwForbidden_when_notOwnerForGetById")
    void should_throwForbidden_when_notOwnerForGetById() {
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));

        assertThatThrownBy(() -> subscriptionService.findById(1L, 999L))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("should_cancelSubscription_when_validOwner")
    void should_cancelSubscription_when_validOwner() {
        SubscriptionResponse cancelledResponse = new SubscriptionResponse(
            1L, 1L, "Natation enfants", "Lyon Natation Metropole",
            10L, "Lucas", "Dupont", 5L, 100L, SubscriptionType.ADHESION,
            SubscriptionStatus.CANCELLED, LocalDate.of(2025, 9, 1), null,
            "Personal reasons", Instant.now(), Instant.now(), Instant.now()
        );

        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(testSubscription)).thenReturn(testSubscription);
        when(subscriptionMapper.toResponse(testSubscription)).thenReturn(cancelledResponse);

        SubscriptionResponse result = subscriptionService.cancelSubscription(1L, 100L, "Personal reasons");

        assertThat(result.status()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(testSubscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(testSubscription.getCancellationReason()).isEqualTo("Personal reasons");
        assertThat(testSubscription.getCancelledAt()).isNotNull();
        verify(eventPublisher).publishSubscriptionCancelled(testSubscription);
    }

    @Test
    @DisplayName("should_throwForbidden_when_notOwnerForCancel")
    void should_throwForbidden_when_notOwnerForCancel() {
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));

        assertThatThrownBy(() -> subscriptionService.cancelSubscription(1L, 999L, "reason"))
            .isInstanceOf(ForbiddenException.class);

        verify(eventPublisher, never()).publishSubscriptionCancelled(any());
    }

    @Test
    @DisplayName("should_activateSubscription_when_pendingStatus")
    void should_activateSubscription_when_pendingStatus() {
        SubscriptionResponse activeResponse = new SubscriptionResponse(
            1L, 1L, "Natation enfants", "Lyon Natation Metropole",
            10L, "Lucas", "Dupont", 5L, 100L, SubscriptionType.ADHESION,
            SubscriptionStatus.ACTIVE, LocalDate.of(2025, 9, 1), null,
            null, null, Instant.now(), Instant.now()
        );

        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));
        when(subscriptionRepository.save(testSubscription)).thenReturn(testSubscription);
        when(subscriptionMapper.toResponse(testSubscription)).thenReturn(activeResponse);

        SubscriptionResponse result = subscriptionService.activateSubscription(1L);

        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(testSubscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("should_throwBadRequest_when_activatingNonPendingSubscription")
    void should_throwBadRequest_when_activatingNonPendingSubscription() {
        testSubscription.setStatus(SubscriptionStatus.ACTIVE);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(testSubscription));

        assertThatThrownBy(() -> subscriptionService.activateSubscription(1L))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("ACTIVE");
    }

    @Test
    @DisplayName("should_returnEmptyList_when_noSubscriptionsForFamily")
    void should_returnEmptyList_when_noSubscriptionsForFamily() {
        when(subscriptionRepository.findByFamilyId(999L)).thenReturn(Collections.emptyList());

        List<SubscriptionResponse> result = subscriptionService.findByFamilyId(999L, 100L);

        assertThat(result).isEmpty();
    }
}
