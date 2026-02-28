package com.familyhobbies.userservice.repository;

import com.familyhobbies.userservice.entity.User;
import com.familyhobbies.userservice.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Find users eligible for RGPD anonymization.
     *
     * <p>Criteria:
     * <ul>
     *     <li>{@code status = DELETED} -- user has requested account deletion</li>
     *     <li>{@code updated_at < cutoff} -- at least 30 days have passed since deletion</li>
     *     <li>{@code anonymized = false} -- data has not yet been anonymized</li>
     * </ul>
     *
     * @param status the user status to filter (DELETED)
     * @param cutoff users deleted before this timestamp are eligible
     * @return list of users whose PII must be anonymized
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.status = :status
              AND u.updatedAt < :cutoff
              AND u.anonymized = false
            ORDER BY u.updatedAt ASC
            """)
    List<User> findEligibleForAnonymization(
            @Param("status") UserStatus status,
            @Param("cutoff") Instant cutoff
    );
}
