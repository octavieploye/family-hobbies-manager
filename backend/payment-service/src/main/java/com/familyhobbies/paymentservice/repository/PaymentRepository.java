package com.familyhobbies.paymentservice.repository;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link Payment} entities.
 * No @Repository annotation -- Spring Data auto-detects JpaRepository interfaces.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByHelloassoCheckoutId(String helloassoCheckoutId);

    boolean existsBySubscriptionIdAndStatus(Long subscriptionId, PaymentStatus status);

    /**
     * Finds payments for a given family with optional filtering by status and date range.
     */
    @Query("SELECT p FROM Payment p WHERE p.familyId = :familyId " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:fromDate IS NULL OR p.createdAt >= :fromDate) " +
           "AND (:toDate IS NULL OR p.createdAt <= :toDate)")
    Page<Payment> findByFamilyIdWithFilters(
            @Param("familyId") Long familyId,
            @Param("status") PaymentStatus status,
            @Param("fromDate") OffsetDateTime fromDate,
            @Param("toDate") OffsetDateTime toDate,
            Pageable pageable);

    /**
     * Find all payments with the given status that were created before the cutoff time.
     * Used by the reconciliation batch to find stale PENDING payments (>24h old).
     *
     * @param status  the payment status to filter by (typically PENDING)
     * @param cutoff  the timestamp cutoff (payments created before this time are returned)
     * @return list of stale payments needing reconciliation
     */
    @Query("SELECT p FROM Payment p WHERE p.status = :status AND p.createdAt < :cutoff ORDER BY p.createdAt ASC")
    List<Payment> findByStatusAndCreatedAtBefore(
            @Param("status") PaymentStatus status,
            @Param("cutoff") OffsetDateTime cutoff);
}
