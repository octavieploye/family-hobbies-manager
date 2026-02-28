// frontend/src/app/features/attendance/store/attendance.effects.spec.ts
import { TestBed } from '@angular/core/testing';
import { Actions } from '@ngrx/effects';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of, throwError } from 'rxjs';

import * as fromEffects from './attendance.effects';
import { AttendanceActions } from './attendance.actions';
import { AttendanceService } from '../services/attendance.service';
import { Attendance, BulkAttendanceRequest } from '@shared/models/attendance.model';

describe('Attendance Effects', () => {
  let actions$: Observable<any>;
  let attendanceService: jest.Mocked<AttendanceService>;

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

  beforeEach(() => {
    attendanceService = {
      getBySession: jest.fn(),
      getByMember: jest.fn(),
      getMemberSummary: jest.fn(),
      markSingle: jest.fn(),
      markBulk: jest.fn(),
      getBySubscription: jest.fn(),
      update: jest.fn(),
    } as any;

    TestBed.configureTestingModule({
      providers: [
        provideMockActions(() => actions$),
        { provide: AttendanceService, useValue: attendanceService },
      ],
    });
  });

  describe('loadSessionAttendance$', () => {
    it('should dispatch loadSessionAttendanceSuccess on service success', (done) => {
      actions$ = of(AttendanceActions.loadSessionAttendance({ sessionId: 10, date: '2024-09-15' }));
      attendanceService.getBySession.mockReturnValue(of([mockAttendance]));

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.loadSessionAttendance$(
          TestBed.inject(Actions),
          attendanceService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            AttendanceActions.loadSessionAttendanceSuccess({ records: [mockAttendance] })
          );
          expect(attendanceService.getBySession).toHaveBeenCalledWith(10, '2024-09-15');
          done();
        });
      });
    });

    it('should dispatch loadSessionAttendanceFailure on service error', (done) => {
      actions$ = of(AttendanceActions.loadSessionAttendance({ sessionId: 10, date: '2024-09-15' }));
      attendanceService.getBySession.mockReturnValue(
        throwError(() => ({ message: 'Session not found' }))
      );

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.loadSessionAttendance$(
          TestBed.inject(Actions),
          attendanceService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            AttendanceActions.loadSessionAttendanceFailure({ error: 'Session not found' })
          );
          done();
        });
      });
    });
  });

  describe('bulkMarkAttendance$', () => {
    it('should dispatch bulkMarkAttendanceSuccess on service success', (done) => {
      const request: BulkAttendanceRequest = {
        sessionId: 10,
        sessionDate: '2024-09-15',
        marks: [
          { familyMemberId: 5, subscriptionId: 3, status: 'PRESENT' },
        ],
      };
      actions$ = of(AttendanceActions.bulkMarkAttendance({ request }));
      attendanceService.markBulk.mockReturnValue(of([mockAttendance]));

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.bulkMarkAttendance$(
          TestBed.inject(Actions),
          attendanceService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            AttendanceActions.bulkMarkAttendanceSuccess({ records: [mockAttendance] })
          );
          expect(attendanceService.markBulk).toHaveBeenCalledWith(request);
          done();
        });
      });
    });

    it('should dispatch bulkMarkAttendanceFailure on service error', (done) => {
      const request: BulkAttendanceRequest = {
        sessionId: 10,
        sessionDate: '2024-09-15',
        marks: [],
      };
      actions$ = of(AttendanceActions.bulkMarkAttendance({ request }));
      attendanceService.markBulk.mockReturnValue(
        throwError(() => ({ message: 'Bulk save failed' }))
      );

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.bulkMarkAttendance$(
          TestBed.inject(Actions),
          attendanceService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            AttendanceActions.bulkMarkAttendanceFailure({ error: 'Bulk save failed' })
          );
          done();
        });
      });
    });
  });
});
