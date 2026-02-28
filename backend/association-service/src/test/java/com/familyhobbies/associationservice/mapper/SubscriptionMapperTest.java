package com.familyhobbies.associationservice.mapper;

import com.familyhobbies.associationservice.dto.request.SubscriptionRequest;
import com.familyhobbies.associationservice.dto.response.SubscriptionResponse;
import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.AssociationStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link SubscriptionMapper}.
 *
 * Tests: 3 test methods covering toResponse (with denormalized names)
 * and toEntity (field mapping from request).
 */
class SubscriptionMapperTest {

    private SubscriptionMapper mapper;
    private Association testAssociation;
    private Activity testActivity;
    private Subscription testSubscription;

    @BeforeEach
    void setUp() {
        mapper = new SubscriptionMapper();

        testAssociation = Association.builder()
            .id(1L)
            .name("Lyon Natation")
            .slug("lyon-natation")
            .category(AssociationCategory.SPORT)
            .status(AssociationStatus.ACTIVE)
            .build();

        testActivity = Activity.builder()
            .id(5L)
            .association(testAssociation)
            .name("Natation enfants")
            .category(AssociationCategory.SPORT)
            .level(ActivityLevel.BEGINNER)
            .status(ActivityStatus.ACTIVE)
            .priceCents(18000)
            .build();

        testSubscription = Subscription.builder()
            .id(100L)
            .activity(testActivity)
            .familyMemberId(10L)
            .familyId(5L)
            .userId(200L)
            .memberFirstName("Lucas")
            .memberLastName("Dupont")
            .subscriptionType(SubscriptionType.ADHESION)
            .status(SubscriptionStatus.ACTIVE)
            .startDate(LocalDate.of(2025, 9, 1))
            .endDate(LocalDate.of(2026, 6, 30))
            .createdAt(Instant.parse("2025-09-01T10:00:00Z"))
            .updatedAt(Instant.parse("2025-09-15T12:00:00Z"))
            .build();
    }

    @Test
    @DisplayName("should_mapAllFieldsIncludingDenormalizedNames_when_toResponse")
    void should_mapAllFieldsIncludingDenormalizedNames_when_toResponse() {
        SubscriptionResponse response = mapper.toResponse(testSubscription);

        assertNotNull(response);
        assertEquals(100L, response.id());
        assertEquals(5L, response.activityId());
        assertEquals("Natation enfants", response.activityName());
        assertEquals("Lyon Natation", response.associationName());
        assertEquals(10L, response.familyMemberId());
        assertEquals("Lucas", response.memberFirstName());
        assertEquals("Dupont", response.memberLastName());
        assertEquals(5L, response.familyId());
        assertEquals(200L, response.userId());
        assertEquals(SubscriptionType.ADHESION, response.subscriptionType());
        assertEquals(SubscriptionStatus.ACTIVE, response.status());
        assertEquals(LocalDate.of(2025, 9, 1), response.startDate());
        assertEquals(LocalDate.of(2026, 6, 30), response.endDate());
        assertNull(response.cancellationReason());
        assertNull(response.cancelledAt());
        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());
    }

    @Test
    @DisplayName("should_returnNull_when_toResponseWithNull")
    void should_returnNull_when_toResponseWithNull() {
        assertNull(mapper.toResponse(null));
    }

    @Test
    @DisplayName("should_createEntityFromRequest_when_toEntity")
    void should_createEntityFromRequest_when_toEntity() {
        SubscriptionRequest request = new SubscriptionRequest(
            5L,
            20L,
            8L,
            "Emma",
            "Martin",
            SubscriptionType.COTISATION,
            LocalDate.of(2025, 9, 1),
            LocalDate.of(2026, 6, 30)
        );

        Subscription entity = mapper.toEntity(request, testActivity, 300L);

        assertNotNull(entity);
        assertEquals(testActivity, entity.getActivity());
        assertEquals(20L, entity.getFamilyMemberId());
        assertEquals(8L, entity.getFamilyId());
        assertEquals(300L, entity.getUserId());
        assertEquals("Emma", entity.getMemberFirstName());
        assertEquals("Martin", entity.getMemberLastName());
        assertEquals(SubscriptionType.COTISATION, entity.getSubscriptionType());
        assertEquals(LocalDate.of(2025, 9, 1), entity.getStartDate());
        assertEquals(LocalDate.of(2026, 6, 30), entity.getEndDate());
    }

    @Test
    @DisplayName("should_returnNull_when_toEntityWithNull")
    void should_returnNull_when_toEntityWithNull() {
        assertNull(mapper.toEntity(null, testActivity, 300L));
    }
}
