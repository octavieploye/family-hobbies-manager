// frontend/src/app/features/attendance/store/attendance.effects.ts
import { inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { AttendanceService } from '../services/attendance.service';
import { AttendanceActions } from './attendance.actions';

/**
 * Load session attendance effect.
 */
export const loadSessionAttendance$ = createEffect(
  (actions$ = inject(Actions), service = inject(AttendanceService)) =>
    actions$.pipe(
      ofType(AttendanceActions.loadSessionAttendance),
      switchMap(({ sessionId, date }) =>
        service.getBySession(sessionId, date).pipe(
          map((records) =>
            AttendanceActions.loadSessionAttendanceSuccess({ records })
          ),
          catchError((error) =>
            of(
              AttendanceActions.loadSessionAttendanceFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement des pr\u00e9sences',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Load member attendance history effect.
 */
export const loadMemberHistory$ = createEffect(
  (actions$ = inject(Actions), service = inject(AttendanceService)) =>
    actions$.pipe(
      ofType(AttendanceActions.loadMemberHistory),
      switchMap(({ memberId }) =>
        service.getByMember(memberId).pipe(
          map((records) =>
            AttendanceActions.loadMemberHistorySuccess({ records })
          ),
          catchError((error) =>
            of(
              AttendanceActions.loadMemberHistoryFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement de l\'historique',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Load member attendance summary effect.
 */
export const loadMemberSummary$ = createEffect(
  (actions$ = inject(Actions), service = inject(AttendanceService)) =>
    actions$.pipe(
      ofType(AttendanceActions.loadMemberSummary),
      switchMap(({ memberId }) =>
        service.getMemberSummary(memberId).pipe(
          map((summary) =>
            AttendanceActions.loadMemberSummarySuccess({ summary })
          ),
          catchError((error) =>
            of(
              AttendanceActions.loadMemberSummaryFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement du r\u00e9sum\u00e9',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Bulk mark attendance effect.
 */
export const bulkMarkAttendance$ = createEffect(
  (actions$ = inject(Actions), service = inject(AttendanceService)) =>
    actions$.pipe(
      ofType(AttendanceActions.bulkMarkAttendance),
      switchMap(({ request }) =>
        service.markBulk(request).pipe(
          map((records) =>
            AttendanceActions.bulkMarkAttendanceSuccess({ records })
          ),
          catchError((error) =>
            of(
              AttendanceActions.bulkMarkAttendanceFailure({
                error: error?.error?.message || error?.message || 'Erreur lors de l\'enregistrement des pr\u00e9sences',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Update attendance record effect.
 */
export const updateAttendance$ = createEffect(
  (actions$ = inject(Actions), service = inject(AttendanceService)) =>
    actions$.pipe(
      ofType(AttendanceActions.updateAttendance),
      switchMap(({ attendanceId, changes }) =>
        service.update(attendanceId, changes).pipe(
          map((record) =>
            AttendanceActions.updateAttendanceSuccess({ record })
          ),
          catchError((error) =>
            of(
              AttendanceActions.updateAttendanceFailure({
                error: error?.error?.message || error?.message || 'Erreur lors de la mise \u00e0 jour',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);
