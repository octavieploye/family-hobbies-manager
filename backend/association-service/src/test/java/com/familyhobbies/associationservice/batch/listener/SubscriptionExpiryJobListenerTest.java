package com.familyhobbies.associationservice.batch.listener;

import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.Association;
import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import com.familyhobbies.associationservice.entity.enums.SubscriptionType;
import com.familyhobbies.common.event.SubscriptionExpiredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * TDD contract tests for {@link SubscriptionExpiryJobListener}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Kafka events published after chunk with correct topic and payload</li>
 *   <li>No events published when no subscriptions were registered</li>
 *   <li>Pending events cleared after chunk error</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryJobListenerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private ChunkContext chunkContext;

    private SubscriptionExpiryJobListener listener;

    @BeforeEach
    void setUp() {
        listener = new SubscriptionExpiryJobListener(kafkaTemplate);
    }

    @Test
    @DisplayName("Should publish SubscriptionExpiredEvent to Kafka "
            + "for each registered subscription after chunk")
    void shouldPublishKafkaEventsAfterChunk() {
        // Given
        Subscription sub1 = createExpiredSubscription(1L);
        Subscription sub2 = createExpiredSubscription(2L);

        listener.beforeChunk(chunkContext);
        listener.registerExpiredSubscription(sub1);
        listener.registerExpiredSubscription(sub2);

        // When
        listener.afterChunk(chunkContext);

        // Then
        verify(kafkaTemplate, times(2)).send(
                eq("subscription.expired"),
                any(String.class),
                any(SubscriptionExpiredEvent.class));

        // Verify keys are subscription IDs
        ArgumentCaptor<String> keyCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(2)).send(
                eq("subscription.expired"),
                keyCaptor.capture(),
                any(SubscriptionExpiredEvent.class));

        assertThat(keyCaptor.getAllValues())
                .containsExactly(
                        sub1.getId().toString(),
                        sub2.getId().toString());
    }

    @Test
    @DisplayName("Should NOT publish events when no subscriptions registered")
    void shouldNotPublishWhenEmpty() {
        // Given
        listener.beforeChunk(chunkContext);
        // No subscriptions registered

        // When
        listener.afterChunk(chunkContext);

        // Then
        verify(kafkaTemplate, never()).send(
                any(String.class),
                any(String.class),
                any());
    }

    @Test
    @DisplayName("Should clear pending events after chunk error")
    void shouldClearEventsOnChunkError() {
        // Given
        listener.beforeChunk(chunkContext);
        listener.registerExpiredSubscription(createExpiredSubscription(1L));

        // When -- chunk fails
        listener.afterChunkError(chunkContext);

        // Then -- subsequent afterChunk should have nothing to publish
        listener.afterChunk(chunkContext);
        verify(kafkaTemplate, never()).send(
                any(String.class),
                any(String.class),
                any());
    }

    @Test
    @DisplayName("Should include correct fields in SubscriptionExpiredEvent")
    void shouldIncludeCorrectEventFields() {
        // Given
        Long subscriptionId = 42L;
        Long userId = 100L;
        Long familyMemberId = 200L;
        Long familyId = 300L;
        Long activityId = 10L;
        Long associationId = 5L;
        Instant expiredAt = Instant.now();

        Association association = new Association();
        association.setId(associationId);

        Activity activity = new Activity();
        activity.setId(activityId);
        activity.setAssociation(association);

        Subscription subscription = new Subscription();
        subscription.setId(subscriptionId);
        subscription.setUserId(userId);
        subscription.setFamilyMemberId(familyMemberId);
        subscription.setFamilyId(familyId);
        subscription.setActivity(activity);
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setExpiredAt(expiredAt);
        subscription.setSubscriptionType(SubscriptionType.ADHESION);
        subscription.setStartDate(LocalDate.now().minusYears(1));
        subscription.setEndDate(LocalDate.now().minusDays(1));

        listener.beforeChunk(chunkContext);
        listener.registerExpiredSubscription(subscription);

        // When
        listener.afterChunk(chunkContext);

        // Then
        ArgumentCaptor<SubscriptionExpiredEvent> eventCaptor =
                ArgumentCaptor.forClass(SubscriptionExpiredEvent.class);
        verify(kafkaTemplate).send(
                eq("subscription.expired"),
                eq(subscriptionId.toString()),
                eventCaptor.capture());

        SubscriptionExpiredEvent event = eventCaptor.getValue();
        assertThat(event.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getFamilyMemberId()).isEqualTo(familyMemberId);
        assertThat(event.getFamilyId()).isEqualTo(familyId);
        assertThat(event.getAssociationId()).isEqualTo(associationId);
        assertThat(event.getActivityId()).isEqualTo(activityId);
        assertThat(event.getExpiredAt()).isEqualTo(expiredAt);
        assertThat(event.getOccurredAt()).isNotNull();
    }

    private Subscription createExpiredSubscription(Long id) {
        Activity activity = new Activity();
        activity.setId(10L);
        Association association = new Association();
        association.setId(5L);
        activity.setAssociation(association);

        Subscription subscription = new Subscription();
        subscription.setId(id);
        subscription.setUserId(100L);
        subscription.setFamilyMemberId(200L);
        subscription.setFamilyId(300L);
        subscription.setActivity(activity);
        subscription.setStatus(SubscriptionStatus.EXPIRED);
        subscription.setExpiredAt(Instant.now());
        subscription.setSubscriptionType(SubscriptionType.ADHESION);
        subscription.setStartDate(LocalDate.now().minusYears(1));
        subscription.setEndDate(LocalDate.now().minusDays(1));
        return subscription;
    }
}
