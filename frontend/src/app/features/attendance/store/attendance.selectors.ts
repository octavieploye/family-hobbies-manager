// frontend/src/app/features/attendance/store/attendance.selectors.ts
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AttendanceState } from './attendance.reducer';

/**
 * Feature selector for the 'attendance' state slice.
 * The key 'attendance' must match the key used in provideState().
 */
export const selectAttendanceState =
  createFeatureSelector<AttendanceState>('attendance');

/** Select all attendance records. */
export const selectAttendanceRecords = createSelector(
  selectAttendanceState,
  (state) => state.records
);

/** Select the member attendance summary. */
export const selectAttendanceSummary = createSelector(
  selectAttendanceState,
  (state) => state.summary
);

/** Select whether a request is in progress. */
export const selectAttendanceLoading = createSelector(
  selectAttendanceState,
  (state) => state.loading
);

/** Select the current error message, if any. */
export const selectAttendanceError = createSelector(
  selectAttendanceState,
  (state) => state.error
);
