package com.familyhobbies.associationservice.entity;

import com.familyhobbies.associationservice.entity.enums.ActivityLevel;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import com.familyhobbies.associationservice.entity.enums.AssociationCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Activity entity.
 * Verifies builder defaults, field mappings, and entity relationships.
 */
class ActivityTest {

    @Test
    @DisplayName("should_createActivity_when_usingBuilder")
    void should_createActivity_when_usingBuilder() {
        Association association = Association.builder().id(1L).name("Test Association").build();

        Activity activity = Activity.builder()
            .id(1L)
            .association(association)
            .name("Natation enfants")
            .description("Cours de natation pour enfants")
            .category(AssociationCategory.SPORT)
            .minAge(6)
            .maxAge(10)
            .maxCapacity(15)
            .priceCents(18000)
            .seasonStart(LocalDate.of(2025, 9, 1))
            .seasonEnd(LocalDate.of(2026, 6, 30))
            .build();

        assertThat(activity.getId()).isEqualTo(1L);
        assertThat(activity.getAssociation()).isNotNull();
        assertThat(activity.getAssociation().getId()).isEqualTo(1L);
        assertThat(activity.getName()).isEqualTo("Natation enfants");
        assertThat(activity.getDescription()).isEqualTo("Cours de natation pour enfants");
        assertThat(activity.getCategory()).isEqualTo(AssociationCategory.SPORT);
        assertThat(activity.getMinAge()).isEqualTo(6);
        assertThat(activity.getMaxAge()).isEqualTo(10);
        assertThat(activity.getMaxCapacity()).isEqualTo(15);
        assertThat(activity.getPriceCents()).isEqualTo(18000);
        assertThat(activity.getSeasonStart()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(activity.getSeasonEnd()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("should_defaultToActiveStatus_when_usingBuilder")
    void should_defaultToActiveStatus_when_usingBuilder() {
        Activity activity = Activity.builder()
            .name("Test")
            .category(AssociationCategory.SPORT)
            .build();

        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.ACTIVE);
    }

    @Test
    @DisplayName("should_defaultToAllLevels_when_usingBuilder")
    void should_defaultToAllLevels_when_usingBuilder() {
        Activity activity = Activity.builder()
            .name("Test")
            .category(AssociationCategory.SPORT)
            .build();

        assertThat(activity.getLevel()).isEqualTo(ActivityLevel.ALL_LEVELS);
    }

    @Test
    @DisplayName("should_defaultToZeroPriceCents_when_usingBuilder")
    void should_defaultToZeroPriceCents_when_usingBuilder() {
        Activity activity = Activity.builder()
            .name("Test")
            .category(AssociationCategory.SPORT)
            .build();

        assertThat(activity.getPriceCents()).isEqualTo(0);
    }

    @Test
    @DisplayName("should_initializeEmptySessionsList_when_usingBuilder")
    void should_initializeEmptySessionsList_when_usingBuilder() {
        Activity activity = Activity.builder()
            .name("Test")
            .category(AssociationCategory.SPORT)
            .build();

        assertThat(activity.getSessions()).isNotNull();
        assertThat(activity.getSessions()).isEmpty();
    }

    @Test
    @DisplayName("should_supportSetters_when_modifyingFields")
    void should_supportSetters_when_modifyingFields() {
        Activity activity = new Activity();
        activity.setName("Updated name");
        activity.setStatus(ActivityStatus.CANCELLED);
        activity.setPriceCents(5000);

        assertThat(activity.getName()).isEqualTo("Updated name");
        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
        assertThat(activity.getPriceCents()).isEqualTo(5000);
    }
}
