// frontend/src/app/shared/models/attendance.model.ts
// TypeScript interfaces matching backend Attendance DTOs

export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'EXCUSED' | 'LATE';

/**
 * Represents a single attendance record for a session.
 * Maps to backend AttendanceResponse DTO field-for-field.
 */
export interface Attendance {
  id: number;
  sessionId: number;
  familyMemberId: number;
  memberFirstName: string;
  memberLastName: string;
  subscriptionId: number;
  sessionDate: string;
  status: AttendanceStatus;
  note: string | null;
  markedBy: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request payload for marking a single attendance.
 * Maps to backend AttendanceMark DTO.
 */
export interface AttendanceMark {
  familyMemberId: number;
  subscriptionId: number;
  status: AttendanceStatus;
  note?: string;
}

/**
 * Request payload for bulk attendance marking.
 * Maps to backend BulkAttendanceRequest DTO.
 */
export interface BulkAttendanceRequest {
  sessionId: number;
  sessionDate: string;
  marks: AttendanceMark[];
}

/**
 * Summary statistics for a member's attendance.
 * Maps to backend AttendanceSummary DTO.
 */
export interface AttendanceSummary {
  familyMemberId: number;
  memberFirstName: string;
  memberLastName: string;
  totalSessions: number;
  presentCount: number;
  absentCount: number;
  excusedCount: number;
  lateCount: number;
  attendanceRate: number;
}

/**
 * Status configuration for display in UI with French labels, Material color, and icon.
 */
export const ATTENDANCE_STATUS_CONFIG: Record<AttendanceStatus, { label: string; color: string; icon: string }> = {
  PRESENT: { label: 'Pr\u00e9sent', color: '#4caf50', icon: 'check_circle' },
  ABSENT: { label: 'Absent', color: '#f44336', icon: 'cancel' },
  EXCUSED: { label: 'Excus\u00e9', color: '#ff9800', icon: 'info' },
  LATE: { label: 'En retard', color: '#2196f3', icon: 'schedule' },
};
