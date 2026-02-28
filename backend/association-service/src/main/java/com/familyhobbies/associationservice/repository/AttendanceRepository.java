package com.familyhobbies.associationservice.repository;

import com.familyhobbies.associationservice.entity.Attendance;
import com.familyhobbies.associationservice.entity.enums.AttendanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for {@link Attendance} entities.
 * Provides custom queries for session attendance, member history, and summary counts.
 */
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /**
     * Get all attendance records for a session on a specific date.
     */
    List<Attendance> findBySession_IdAndSessionDate(Long sessionId, LocalDate sessionDate);

    /**
     * Get all attendance records for a family member (history).
     */
    List<Attendance> findByFamilyMemberId(Long familyMemberId);

    /**
     * Get all attendance records for a subscription.
     */
    List<Attendance> findBySubscription_Id(Long subscriptionId);

    /**
     * Count attendance records by member and status (for summary).
     */
    long countByFamilyMemberIdAndStatus(Long familyMemberId, AttendanceStatus status);

    /**
     * Count total attendance records for a member.
     */
    long countByFamilyMemberId(Long familyMemberId);
}
