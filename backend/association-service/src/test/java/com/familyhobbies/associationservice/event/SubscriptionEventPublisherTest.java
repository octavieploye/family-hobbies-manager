package com.familyhobbies.associationservice.event;

import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionType;
import com.familyhobbies.common.event.SubscriptionCancelledEvent;
import com.familyhobbies.common.event.SubscriptionCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SubscriptionEventPublisher.
 *
 * Story: S3-006 -- Subscription Kafka Events
 * Tests: 4 test methods
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private SubscriptionEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<SubscriptionCreatedEvent> createdEventCaptor;

    @Captor
    private ArgumentCaptor<SubscriptionCancelledEvent> cancelledEventCaptor;

    private Subscription testSubscription;

    @BeforeEach
    void setUp() {
        Association association = Association.builder()
            .id(1L)
            .name("Lyon Natation Metropole")
            .category(AssociationCategory.SPORT)
            .build();

        Activity activity = Activity.builder()
            .id(1L)
            .association(association)
            .name("Natation enfants")
            .priceCents(18000)
            .build();

        testSubscription = Subscription.builder()
            .id(1L)
            .activity(activity)
            .familyMemberId(10L)
            .familyId(5L)
            .userId(100L)
            .subscriptionType(SubscriptionType.ADHESION)
            .status(SubscriptionStatus.PENDING)
            .startDate(LocalDate.of(2025, 9, 1))
            .build();
    }

    @Test
    @DisplayName("should_publishSubscriptionCreatedEvent_when_subscriptionCreated")
    @SuppressWarnings("unchecked")
    void should_publishSubscriptionCreatedEvent_when_subscriptionCreated() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
            .thenReturn(new CompletableFuture<>());

        eventPublisher.publishSubscriptionCreated(testSubscription);

        verify(kafkaTemplate).send(
            eq("family-hobbies.subscription.created"),
            eq("1"),
            createdEventCaptor.capture()
        );

        SubscriptionCreatedEvent event = createdEventCaptor.getValue();
        assertThat(event.getSubscriptionId()).isEqualTo(1L);
        assertThat(event.getUserId()).isEqualTo(100L);
        assertThat(event.getFamilyId()).isEqualTo(5L);
        assertThat(event.getAssociationId()).isEqualTo(1L);
        assertThat(event.getSubscriptionType()).isEqualTo("ADHESION");
    }

    @Test
    @DisplayName("should_publishSubscriptionCancelledEvent_when_subscriptionCancelled")
    @SuppressWarnings("unchecked")
    void should_publishSubscriptionCancelledEvent_when_subscriptionCancelled() {
        testSubscription.setStatus(SubscriptionStatus.CANCELLED);
        testSubscription.setCancellationReason("Moving away");

        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
            .thenReturn(new CompletableFuture<>());

        eventPublisher.publishSubscriptionCancelled(testSubscription);

        verify(kafkaTemplate).send(
            eq("family-hobbies.subscription.cancelled"),
            eq("1"),
            cancelledEventCaptor.capture()
        );

        SubscriptionCancelledEvent event = cancelledEventCaptor.getValue();
        assertThat(event.getSubscriptionId()).isEqualTo(1L);
        assertThat(event.getUserId()).isEqualTo(100L);
        assertThat(event.getCancellationReason()).isEqualTo("Moving away");
    }

    @Test
    @DisplayName("should_notThrow_when_kafkaSendFails")
    void should_notThrow_when_kafkaSendFails() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
            .thenThrow(new RuntimeException("Kafka is down"));

        // Should not throw -- fire-and-forget pattern
        eventPublisher.publishSubscriptionCreated(testSubscription);
    }

    @Test
    @DisplayName("should_notThrow_when_kafkaSendFailsForCancel")
    void should_notThrow_when_kafkaSendFailsForCancel() {
        testSubscription.setCancellationReason("Test reason");

        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
            .thenThrow(new RuntimeException("Kafka is down"));

        // Should not throw -- fire-and-forget pattern
        eventPublisher.publishSubscriptionCancelled(testSubscription);
    }
}
