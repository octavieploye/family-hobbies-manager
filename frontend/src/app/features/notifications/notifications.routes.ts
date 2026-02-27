// frontend/src/app/features/notifications/notifications.routes.ts
import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { notificationReducer } from './store/notification.reducer';
import * as notificationEffects from './store/notification.effects';

/**
 * Lazy-loaded routes for the notifications feature.
 *
 * All notification routes require authentication (authGuard applied at app.routes.ts level).
 * The feature NgRx store and effects are registered at the route level
 * so they are only loaded when the user navigates to /notifications.
 */
export const NOTIFICATION_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState('notifications', notificationReducer),
      provideEffects(notificationEffects),
    ],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./components/notification-list/notification-list.component').then(
            (m) => m.NotificationListComponent
          ),
        title: 'Mes notifications',
      },
      {
        path: 'preferences',
        loadComponent: () =>
          import('./components/notification-preferences/notification-preferences.component').then(
            (m) => m.NotificationPreferencesComponent
          ),
        title: 'Preferences de notification',
      },
    ],
  },
];
