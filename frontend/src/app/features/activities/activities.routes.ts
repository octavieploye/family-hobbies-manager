// frontend/src/app/features/activities/activities.routes.ts
import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { activityReducer } from './store/activity.reducer';
import * as activityEffects from './store/activity.effects';

/**
 * Lazy-loaded routes for the activities feature.
 *
 * Activities are nested under /associations/:associationId/activities.
 * All activity browse routes are PUBLIC (no authGuard required).
 * The feature NgRx store and effects are registered at the route level
 * so they are only loaded when the user navigates to activities.
 */
export const ACTIVITY_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState('activities', activityReducer),
      provideEffects(activityEffects),
    ],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./components/activity-list/activity-list.component').then(
            (m) => m.ActivityListComponent
          ),
        title: 'Activit\u00e9s de l\'association',
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./components/activity-detail/activity-detail.component').then(
            (m) => m.ActivityDetailComponent
          ),
        title: 'D\u00e9tail de l\'activit\u00e9',
      },
    ],
  },
];
