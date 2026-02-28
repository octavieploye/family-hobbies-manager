package com.familyhobbies.associationservice.repository;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Subscription} entities.
 * Provides custom queries for subscription lookups by family, member, activity.
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByFamilyId(Long familyId);

    List<Subscription> findByFamilyMemberId(Long memberId);

    Optional<Subscription> findByIdAndUserId(Long id, Long userId);

    List<Subscription> findByActivity_IdAndFamilyMemberIdAndStatusIn(
            Long activityId, Long familyMemberId, List<SubscriptionStatus> statuses);

    List<Subscription> findByActivity_Id(Long activityId);

    List<Subscription> findByUserId(Long userId);
}
