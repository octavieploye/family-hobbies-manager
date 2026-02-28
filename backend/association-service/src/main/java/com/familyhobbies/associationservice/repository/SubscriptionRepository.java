package com.familyhobbies.associationservice.repository;

import com.familyhobbies.associationservice.entity.Subscription;
import com.familyhobbies.associationservice.entity.enums.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /**
     * Find all subscriptions with the given status whose end date
     * is before the specified cutoff date.
     *
     * <p>Used by the subscription expiry batch job reader to fetch
     * ACTIVE subscriptions that have passed their end date.
     *
     * @param status  the subscription status to filter by (ACTIVE)
     * @param cutoff  the date cutoff (today at batch execution time)
     * @param pageable page request
     * @return page of matching subscriptions
     */
    Page<Subscription> findByStatusAndEndDateBefore(
            SubscriptionStatus status,
            LocalDate cutoff,
            Pageable pageable);

    /**
     * Count subscriptions matching status and end date cutoff.
     * Used for batch listener logging (total expired count).
     */
    long countByStatusAndEndDateBefore(
            SubscriptionStatus status,
            LocalDate cutoff);
}
