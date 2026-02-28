package com.familyhobbies.associationservice.entity.enums;

/**
 * Status of an attendance record for a session occurrence.
 * Stored as VARCHAR in PostgreSQL for non-breaking additions.
 */
public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    EXCUSED,
    LATE
}
