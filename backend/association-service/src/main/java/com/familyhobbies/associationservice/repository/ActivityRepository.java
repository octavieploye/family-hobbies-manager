package com.familyhobbies.associationservice.repository;

import com.familyhobbies.associationservice.entity.Activity;
import com.familyhobbies.associationservice.entity.enums.ActivityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Activity} entities.
 * Provides custom queries for association-scoped activity lookups.
 */
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    Page<Activity> findByAssociation_IdAndStatus(Long associationId, ActivityStatus status, Pageable pageable);

    Page<Activity> findByAssociation_Id(Long associationId, Pageable pageable);

    List<Activity> findByAssociation_IdAndStatusOrderByNameAsc(Long associationId, ActivityStatus status);

    Optional<Activity> findByIdAndAssociation_Id(Long id, Long associationId);
}
