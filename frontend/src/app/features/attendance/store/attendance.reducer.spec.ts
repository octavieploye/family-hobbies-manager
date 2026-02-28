// frontend/src/app/features/attendance/store/attendance.reducer.spec.ts
import {
  attendanceReducer,
  initialAttendanceState,
  AttendanceState,
} from './attendance.reducer';
import { AttendanceActions } from './attendance.actions';
import { Attendance, AttendanceSummary } from '@shared/models/attendance.model';

describe('AttendanceReducer', () => {
  const mockAttendance: Attendance = {
    id: 1,
    sessionId: 10,
    familyMemberId: 5,
    memberFirstName: 'Marie',
    memberLastName: 'Dupont',
    subscriptionId: 3,
    sessionDate: '2024-09-15',
    status: 'PRESENT',
    note: null,
    markedBy: 1,
    createdAt: '2024-09-15T10:00:00',
    updatedAt: '2024-09-15T10:00:00',
  };

  const mockSummary: AttendanceSummary = {
    familyMemberId: 5,
    memberFirstName: 'Marie',
    memberLastName: 'Dupont',
    totalSessions: 10,
    presentCount: 8,
    absentCount: 1,
    excusedCount: 1,
    lateCount: 0,
    attendanceRate: 80,
  };

  it('should return the initial state on unknown action', () => {
    const action = { type: 'UNKNOWN' } as any;
    const state = attendanceReducer(undefined, action);
    expect(state).toEqual(initialAttendanceState);
  });

  describe('Load Session Attendance', () => {
    it('should set loading to true on loadSessionAttendance', () => {
      const action = AttendanceActions.loadSessionAttendance({ sessionId: 10, date: '2024-09-15' });
      const state = attendanceReducer(initialAttendanceState, action);

      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('should populate records on loadSessionAttendanceSuccess', () => {
      const action = AttendanceActions.loadSessionAttendanceSuccess({
        records: [mockAttendance],
      });
      const state = attendanceReducer(
        { ...initialAttendanceState, loading: true },
        action
      );

      expect(state.records).toEqual([mockAttendance]);
      expect(state.loading).toBe(false);
      expect(state.error).toBeNull();
    });

    it('should set error on loadSessionAttendanceFailure', () => {
      const action = AttendanceActions.loadSessionAttendanceFailure({
        error: 'Network error',
      });
      const state = attendanceReducer(
        { ...initialAttendanceState, loading: true },
        action
      );

      expect(state.loading).toBe(false);
      expect(state.error).toBe('Network error');
    });
  });

  describe('Load Member Summary', () => {
    it('should set summary on loadMemberSummarySuccess', () => {
      const action = AttendanceActions.loadMemberSummarySuccess({ summary: mockSummary });
      const state = attendanceReducer(
        { ...initialAttendanceState, loading: true },
        action
      );

      expect(state.summary).toEqual(mockSummary);
      expect(state.loading).toBe(false);
    });

    it('should set error on loadMemberSummaryFailure', () => {
      const action = AttendanceActions.loadMemberSummaryFailure({ error: 'Not found' });
      const state = attendanceReducer(
        { ...initialAttendanceState, loading: true },
        action
      );

      expect(state.loading).toBe(false);
      expect(state.error).toBe('Not found');
    });
  });

  describe('Bulk Mark Attendance', () => {
    it('should set loading on bulkMarkAttendance', () => {
      const action = AttendanceActions.bulkMarkAttendance({
        request: {
          sessionId: 10,
          sessionDate: '2024-09-15',
          marks: [{ familyMemberId: 5, subscriptionId: 3, status: 'PRESENT' }],
        },
      });
      const state = attendanceReducer(initialAttendanceState, action);

      expect(state.loading).toBe(true);
    });

    it('should update records on bulkMarkAttendanceSuccess', () => {
      const action = AttendanceActions.bulkMarkAttendanceSuccess({
        records: [mockAttendance],
      });
      const state = attendanceReducer(
        { ...initialAttendanceState, loading: true },
        action
      );

      expect(state.records).toEqual([mockAttendance]);
      expect(state.loading).toBe(false);
    });
  });

  describe('Update Attendance', () => {
    it('should update a record on updateAttendanceSuccess', () => {
      const updatedRecord: Attendance = { ...mockAttendance, status: 'EXCUSED' };
      const stateWithRecords: AttendanceState = {
        ...initialAttendanceState,
        records: [mockAttendance],
        loading: true,
      };

      const action = AttendanceActions.updateAttendanceSuccess({ record: updatedRecord });
      const state = attendanceReducer(stateWithRecords, action);

      expect(state.records[0].status).toBe('EXCUSED');
      expect(state.loading).toBe(false);
    });
  });

  describe('Clear', () => {
    it('should reset to initial state on clearAttendance', () => {
      const populatedState: AttendanceState = {
        records: [mockAttendance],
        summary: mockSummary,
        loading: false,
        error: null,
      };
      const action = AttendanceActions.clearAttendance();
      const state = attendanceReducer(populatedState, action);

      expect(state).toEqual(initialAttendanceState);
    });

    it('should clear error on clearError', () => {
      const stateWithError: AttendanceState = {
        ...initialAttendanceState,
        error: 'Some error',
      };
      const action = AttendanceActions.clearError();
      const state = attendanceReducer(stateWithError, action);

      expect(state.error).toBeNull();
    });
  });
});
