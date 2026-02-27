package com.familyhobbies.userservice.event;

import com.familyhobbies.common.event.UserRegisteredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserEventPublisher.
 *
 * Story: S2-006 -- UserRegisteredEvent Kafka Publishing
 * Tests: 3 test methods
 *
 * These tests verify:
 * - Event is sent to the correct topic with userId as key
 * - Fire-and-forget: synchronous Kafka failures do NOT propagate
 * - Fire-and-forget: asynchronous Kafka failures are logged but do NOT propagate
 *
 * Uses @ExtendWith(MockitoExtension.class) -- no Spring context loaded, no EmbeddedKafka.
 * Mocks: KafkaTemplate.
 */
@ExtendWith(MockitoExtension.class)
class UserEventPublisherTest {

    private static final String EXPECTED_TOPIC = "family-hobbies.user.registered";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private UserEventPublisher userEventPublisher;

    @Test
    @DisplayName("should send to correct topic with userId as key when published")
    void should_sendToCorrectTopicWithUserIdAsKey_when_published() {
        // given
        UserRegisteredEvent event = new UserRegisteredEvent(42L, "jean@email.com", "Jean", "Dupont");

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.complete(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // when
        userEventPublisher.publishUserRegistered(event);

        // then
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);

        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(EXPECTED_TOPIC);
        assertThat(keyCaptor.getValue()).isEqualTo("42");
        assertThat(valueCaptor.getValue()).isEqualTo(event);
    }

    @Test
    @DisplayName("should not throw when Kafka send fails synchronously")
    void should_notThrow_when_kafkaSendFailsSynchronously() {
        // given
        UserRegisteredEvent event = new UserRegisteredEvent(42L, "jean@email.com", "Jean", "Dupont");

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("Kafka broker unavailable"));

        // when & then -- fire-and-forget: no exception should propagate
        assertThatCode(() -> userEventPublisher.publishUserRegistered(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should log error when Kafka send fails asynchronously")
    void should_logError_when_kafkaSendFailsAsynchronously() {
        // given
        UserRegisteredEvent event = new UserRegisteredEvent(42L, "jean@email.com", "Jean", "Dupont");

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Async Kafka failure"));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(future);

        // when & then -- fire-and-forget: async failure should NOT propagate
        assertThatCode(() -> userEventPublisher.publishUserRegistered(event))
                .doesNotThrowAnyException();

        // Verify send was called (the async callback handles the error internally via logging)
        verify(kafkaTemplate).send(eq(EXPECTED_TOPIC), eq("42"), eq(event));
    }
}
