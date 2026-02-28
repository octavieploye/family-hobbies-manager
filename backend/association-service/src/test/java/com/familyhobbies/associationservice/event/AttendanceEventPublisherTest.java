package com.familyhobbies.associationservice.event;

import com.familyhobbies.associationservice.entity.Attendance;
import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.enums.AttendanceStatus;
import com.familyhobbies.common.event.AttendanceMarkedEvent;
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
 * Unit tests for AttendanceEventPublisher.
 *
 * Story: S4-005 -- Attendance Kafka Events
 * Tests: 4 test methods
 */
@ExtendWith(MockitoExtension.class)
class AttendanceEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AttendanceEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<AttendanceMarkedEvent> eventCaptor;

    private Attendance testAttendance;

    @BeforeEach
    void setUp() {
        Session session = Session.builder()
            .id(1L)
            .build();

        testAttendance = Attendance.builder()
            .id(1L)
            .session(session)
            .familyMemberId(10L)
            .sessionDate(LocalDate.of(2025, 10, 15))
            .status(AttendanceStatus.PRESENT)
            .markedBy(100L)
            .build();
    }

    @Test
    @DisplayName("should_publishAttendanceMarkedEvent_when_attendanceMarked")
    @SuppressWarnings("unchecked")
    void should_publishAttendanceMarkedEvent_when_attendanceMarked() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
            .thenReturn(new CompletableFuture<>());

        eventPublisher.publishAttendanceMarked(testAttendance);

        verify(kafkaTemplate).send(
            eq("family-hobbies.attendance.marked"),
            eq("1"),
            eventCaptor.capture()
        );

        AttendanceMarkedEvent event = eventCaptor.getValue();
        assertThat(event.getAttendanceId()).isEqualTo(1L);
        assertThat(event.getMemberId()).isEqualTo(10L);
        assertThat(event.getSessionId()).isEqualTo(1L);
        assertThat(event.getStatus()).isEqualTo("PRESENT");
    }

    @Test
    @DisplayName("should_includeCorrectStatus_when_absentStatusMarked")
    @SuppressWarnings("unchecked")
    void should_includeCorrectStatus_when_absentStatusMarked() {
        testAttendance.setStatus(AttendanceStatus.ABSENT);

        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
            .thenReturn(new CompletableFuture<>());

        eventPublisher.publishAttendanceMarked(testAttendance);

        verify(kafkaTemplate).send(
            eq("family-hobbies.attendance.marked"),
            eq("1"),
            eventCaptor.capture()
        );

        AttendanceMarkedEvent event = eventCaptor.getValue();
        assertThat(event.getStatus()).isEqualTo("ABSENT");
    }

    @Test
    @DisplayName("should_notThrow_when_kafkaSendFails")
    void should_notThrow_when_kafkaSendFails() {
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
            .thenThrow(new RuntimeException("Kafka is down"));

        // Should not throw -- fire-and-forget pattern
        eventPublisher.publishAttendanceMarked(testAttendance);
    }

    @Test
    @DisplayName("should_useAttendanceIdAsKafkaKey")
    @SuppressWarnings("unchecked")
    void should_useAttendanceIdAsKafkaKey() {
        testAttendance = Attendance.builder()
            .id(42L)
            .session(Session.builder().id(1L).build())
            .familyMemberId(10L)
            .status(AttendanceStatus.LATE)
            .build();

        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
            .thenReturn(new CompletableFuture<>());

        eventPublisher.publishAttendanceMarked(testAttendance);

        verify(kafkaTemplate).send(
            eq("family-hobbies.attendance.marked"),
            eq("42"),
            any(AttendanceMarkedEvent.class)
        );
    }
}
