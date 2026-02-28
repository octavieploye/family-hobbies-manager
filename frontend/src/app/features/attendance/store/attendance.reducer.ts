// frontend/src/app/features/attendance/store/attendance.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { Attendance, AttendanceSummary } from '@shared/models/attendance.model';
import { AttendanceActions } from './attendance.actions';

/**
 * State shape for the Attendance feature store.
 */
export interface AttendanceState {
  records: Attendance[];
  summary: AttendanceSummary | null;
  loading: boolean;
  error: string | null;
}

/**
 * Initial state with sensible defaults.
 */
export const initialAttendanceState: AttendanceState = {
  records: [],
  summary: null,
  loading: false,
  error: null,
};

/**
 * Attendance feature reducer.
 * Handles session attendance, member history, summary, bulk marking, and updates.
 */
export const attendanceReducer = createReducer(
  initialAttendanceState,

  // --- Load Session Attendance ---
  on(AttendanceActions.loadSessionAttendance, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AttendanceActions.loadSessionAttendanceSuccess, (state, { records }) => ({
    ...state,
    records,
    loading: false,
    error: null,
  })),

  on(AttendanceActions.loadSessionAttendanceFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Load Member History ---
  on(AttendanceActions.loadMemberHistory, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AttendanceActions.loadMemberHistorySuccess, (state, { records }) => ({
    ...state,
    records,
    loading: false,
    error: null,
  })),

  on(AttendanceActions.loadMemberHistoryFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Load Member Summary ---
  on(AttendanceActions.loadMemberSummary, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AttendanceActions.loadMemberSummarySuccess, (state, { summary }) => ({
    ...state,
    summary,
    loading: false,
    error: null,
  })),

  on(AttendanceActions.loadMemberSummaryFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Bulk Mark Attendance ---
  on(AttendanceActions.bulkMarkAttendance, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AttendanceActions.bulkMarkAttendanceSuccess, (state, { records }) => ({
    ...state,
    records,
    loading: false,
    error: null,
  })),

  on(AttendanceActions.bulkMarkAttendanceFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Update Attendance ---
  on(AttendanceActions.updateAttendance, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AttendanceActions.updateAttendanceSuccess, (state, { record }) => ({
    ...state,
    records: state.records.map((r) => (r.id === record.id ? record : r)),
    loading: false,
    error: null,
  })),

  on(AttendanceActions.updateAttendanceFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Clear ---
  on(AttendanceActions.clearAttendance, () => ({
    ...initialAttendanceState,
  })),

  on(AttendanceActions.clearError, (state) => ({
    ...state,
    error: null,
  }))
);
