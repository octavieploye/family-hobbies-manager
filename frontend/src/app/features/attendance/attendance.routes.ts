// frontend/src/app/features/attendance/attendance.routes.ts
import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { attendanceReducer } from './store/attendance.reducer';
import * as attendanceEffects from './store/attendance.effects';

/**
 * Lazy-loaded routes for the attendance feature.
 *
 * All attendance routes require authentication (authGuard applied at app.routes.ts level).
 * The feature NgRx store and effects are registered at the route level
 * so they are only loaded when the user navigates to /attendance.
 */
export const ATTENDANCE_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState('attendance', attendanceReducer),
      provideEffects(attendanceEffects),
    ],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./components/attendance-sheet/attendance-sheet.component').then(
            (m) => m.AttendanceSheetComponent
          ),
        title: 'Feuille de pr\u00e9sence',
      },
      {
        path: 'member/:memberId',
        loadComponent: () =>
          import('./components/attendance-history/attendance-history.component').then(
            (m) => m.AttendanceHistoryComponent
          ),
        title: 'Historique de pr\u00e9sence',
      },
      {
        path: 'member/:memberId/summary',
        loadComponent: () =>
          import('./components/attendance-summary/attendance-summary.component').then(
            (m) => m.AttendanceSummaryComponent
          ),
        title: 'R\u00e9sum\u00e9 de pr\u00e9sence',
      },
    ],
  },
];
