package com.familyhobbies.userservice.repository;

import com.familyhobbies.userservice.entity.RgpdCleanupLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for RGPD cleanup audit logs.
 */
@Repository
public interface RgpdCleanupLogRepository extends JpaRepository<RgpdCleanupLog, Long> {

    /**
     * Find all cleanup logs ordered by execution timestamp descending (most recent first).
     */
    Page<RgpdCleanupLog> findAllByOrderByExecutionTimestampDesc(Pageable pageable);

    /**
     * Find cleanup logs within a date range (for audit reporting).
     */
    List<RgpdCleanupLog> findByExecutionTimestampBetweenOrderByExecutionTimestampDesc(
            Instant from, Instant to
    );
}
