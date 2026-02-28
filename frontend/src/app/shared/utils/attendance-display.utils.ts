// frontend/src/app/shared/utils/attendance-display.utils.ts
// Shared utility functions for attendance display formatting.

import { AttendanceStatus, ATTENDANCE_STATUS_CONFIG } from '@shared/models/attendance.model';

/**
 * Get Material progress bar color based on attendance rate.
 *
 * @param rate Attendance rate as a percentage (0-100).
 * @returns Material color string: 'primary' (>= 80), 'accent' (>= 50), or 'warn' (< 50).
 */
export function getProgressColor(rate: number): string {
  if (rate >= 80) {
    return 'primary';
  }
  if (rate >= 50) {
    return 'accent';
  }
  return 'warn';
}

/**
 * Get the French label for an attendance status.
 *
 * @param status The attendance status enum value.
 * @returns French label string from ATTENDANCE_STATUS_CONFIG, or the raw status if not found.
 */
export function getAttendanceStatusLabel(status: AttendanceStatus): string {
  return ATTENDANCE_STATUS_CONFIG[status]?.label || status;
}

/**
 * Get the Material icon name for an attendance status.
 *
 * @param status The attendance status enum value.
 * @returns Icon name from ATTENDANCE_STATUS_CONFIG, or 'help' if not found.
 */
export function getAttendanceStatusIcon(status: AttendanceStatus): string {
  return ATTENDANCE_STATUS_CONFIG[status]?.icon || 'help';
}

/**
 * Get the hex color for an attendance status.
 *
 * @param status The attendance status enum value.
 * @returns Hex color string from ATTENDANCE_STATUS_CONFIG, or '#999' if not found.
 */
export function getAttendanceStatusColor(status: AttendanceStatus): string {
  return ATTENDANCE_STATUS_CONFIG[status]?.color || '#999';
}
