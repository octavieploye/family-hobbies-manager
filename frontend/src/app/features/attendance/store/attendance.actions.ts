// frontend/src/app/features/attendance/store/attendance.actions.ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import {
  Attendance,
  AttendanceSummary,
  BulkAttendanceRequest,
  UpdateAttendanceRequest,
} from '@shared/models/attendance.model';

/**
 * NgRx action group for the Attendance feature.
 *
 * Follows the [Source] Event naming convention.
 * Each async operation has a triplet: trigger / success / failure.
 */
export const AttendanceActions = createActionGroup({
  source: 'Attendance',
  events: {
    // --- Load by Session ---
    'Load Session Attendance': props<{ sessionId: number; date: string }>(),
    'Load Session Attendance Success': props<{ records: Attendance[] }>(),
    'Load Session Attendance Failure': props<{ error: string }>(),

    // --- Load Member History ---
    'Load Member History': props<{ memberId: number }>(),
    'Load Member History Success': props<{ records: Attendance[] }>(),
    'Load Member History Failure': props<{ error: string }>(),

    // --- Load Member Summary ---
    'Load Member Summary': props<{ memberId: number }>(),
    'Load Member Summary Success': props<{ summary: AttendanceSummary }>(),
    'Load Member Summary Failure': props<{ error: string }>(),

    // --- Bulk Mark ---
    'Bulk Mark Attendance': props<{ request: BulkAttendanceRequest }>(),
    'Bulk Mark Attendance Success': props<{ records: Attendance[] }>(),
    'Bulk Mark Attendance Failure': props<{ error: string }>(),

    // --- Update ---
    'Update Attendance': props<{ attendanceId: number; changes: UpdateAttendanceRequest }>(),
    'Update Attendance Success': props<{ record: Attendance }>(),
    'Update Attendance Failure': props<{ error: string }>(),

    // --- Clear ---
    'Clear Attendance': emptyProps(),
    'Clear Error': emptyProps(),
  },
});
