package com.familyhobbies.associationservice.entity;

import com.familyhobbies.associationservice.entity.enums.DayOfWeekEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Session entity.
 * Verifies builder defaults, field mappings, and entity relationships.
 */
class SessionTest {

    @Test
    @DisplayName("should_createSession_when_usingBuilder")
    void should_createSession_when_usingBuilder() {
        Activity activity = Activity.builder().id(1L).name("Test Activity").build();

        Session session = Session.builder()
            .id(1L)
            .activity(activity)
            .dayOfWeek(DayOfWeekEnum.TUESDAY)
            .startTime(LocalTime.of(17, 0))
            .endTime(LocalTime.of(18, 0))
            .location("Piscine municipale")
            .instructorName("Marie Dupont")
            .maxCapacity(15)
            .build();

        assertThat(session.getId()).isEqualTo(1L);
        assertThat(session.getActivity()).isNotNull();
        assertThat(session.getActivity().getId()).isEqualTo(1L);
        assertThat(session.getDayOfWeek()).isEqualTo(DayOfWeekEnum.TUESDAY);
        assertThat(session.getStartTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(session.getEndTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(session.getLocation()).isEqualTo("Piscine municipale");
        assertThat(session.getInstructorName()).isEqualTo("Marie Dupont");
        assertThat(session.getMaxCapacity()).isEqualTo(15);
    }

    @Test
    @DisplayName("should_defaultToActiveTrue_when_usingBuilder")
    void should_defaultToActiveTrue_when_usingBuilder() {
        Session session = Session.builder()
            .dayOfWeek(DayOfWeekEnum.MONDAY)
            .startTime(LocalTime.of(9, 0))
            .endTime(LocalTime.of(10, 0))
            .build();

        assertThat(session.isActive()).isTrue();
    }

    @Test
    @DisplayName("should_supportSetters_when_modifyingFields")
    void should_supportSetters_when_modifyingFields() {
        Session session = new Session();
        session.setDayOfWeek(DayOfWeekEnum.FRIDAY);
        session.setLocation("Salle B");
        session.setActive(false);

        assertThat(session.getDayOfWeek()).isEqualTo(DayOfWeekEnum.FRIDAY);
        assertThat(session.getLocation()).isEqualTo("Salle B");
        assertThat(session.isActive()).isFalse();
    }

    @Test
    @DisplayName("should_allowNullableFields_when_usingBuilder")
    void should_allowNullableFields_when_usingBuilder() {
        Session session = Session.builder()
            .dayOfWeek(DayOfWeekEnum.WEDNESDAY)
            .startTime(LocalTime.of(14, 0))
            .endTime(LocalTime.of(15, 0))
            .build();

        assertThat(session.getLocation()).isNull();
        assertThat(session.getInstructorName()).isNull();
        assertThat(session.getMaxCapacity()).isNull();
    }
}
