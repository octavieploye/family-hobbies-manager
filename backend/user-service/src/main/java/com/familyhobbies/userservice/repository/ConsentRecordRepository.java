package com.familyhobbies.userservice.repository;

import com.familyhobbies.userservice.entity.ConsentRecord;
import com.familyhobbies.userservice.entity.enums.ConsentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ConsentRecord} entities.
 * Provides queries for consent status lookups and audit trail retrieval.
 */
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    /**
     * Get all consent records for a user (full audit trail).
     */
    List<ConsentRecord> findByUser_Id(Long userId);

    /**
     * Get all consent records of a specific type for a user.
     */
    List<ConsentRecord> findByUser_IdAndConsentType(Long userId, ConsentType consentType);

    /**
     * Get the latest consent record for a user and consent type.
     * Used to determine the current consent status.
     */
    Optional<ConsentRecord> findTopByUser_IdAndConsentTypeOrderByConsentedAtDesc(
            Long userId, ConsentType consentType);
}
