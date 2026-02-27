package com.familyhobbies.paymentservice.repository;

import com.familyhobbies.paymentservice.entity.Payment;
import com.familyhobbies.paymentservice.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
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
}
