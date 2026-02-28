package com.familyhobbies.associationservice.repository;

import com.familyhobbies.associationservice.entity.Session;
import com.familyhobbies.associationservice.entity.enums.DayOfWeekEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Session} entities.
 * Provides custom queries for activity-scoped session lookups.
 */
public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByActivity_IdAndActiveTrue(Long activityId);

    List<Session> findByActivity_Id(Long activityId);

    Optional<Session> findByIdAndActivity_Id(Long id, Long activityId);

    List<Session> findByDayOfWeekAndActiveTrue(DayOfWeekEnum dayOfWeek);
}
