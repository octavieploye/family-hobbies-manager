// frontend/src/app/features/notifications/store/notification.effects.ts
import { inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { NotificationService } from '../services/notification.service';
import { NotificationActions } from './notification.actions';

/**
 * Load notifications list effect.
 *
 * Listens for [Notifications] Load Notifications action,
 * calls NotificationService.getNotifications(), then dispatches success or failure.
 */
export const loadNotifications$ = createEffect(
  (actions$ = inject(Actions), service = inject(NotificationService)) =>
    actions$.pipe(
      ofType(NotificationActions.loadNotifications),
      switchMap(({ page, size }) =>
        service.getNotifications(page, size).pipe(
          map((notificationPage) =>
            NotificationActions.loadNotificationsSuccess({ notificationPage })
          ),
          catchError((error) =>
            of(
              NotificationActions.loadNotificationsFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement des notifications',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Load unread count effect.
 */
export const loadUnreadCount$ = createEffect(
  (actions$ = inject(Actions), service = inject(NotificationService)) =>
    actions$.pipe(
      ofType(NotificationActions.loadUnreadCount),
      switchMap(() =>
        service.getUnreadCount().pipe(
          map((unreadCount) =>
            NotificationActions.loadUnreadCountSuccess({ unreadCount })
          ),
          catchError((error) =>
            of(
              NotificationActions.loadUnreadCountFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement du compteur',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Mark a single notification as read.
 */
export const markAsRead$ = createEffect(
  (actions$ = inject(Actions), service = inject(NotificationService)) =>
    actions$.pipe(
      ofType(NotificationActions.markAsRead),
      switchMap(({ id }) =>
        service.markAsRead(id).pipe(
          map(() => NotificationActions.markAsReadSuccess({ id })),
          catchError((error) =>
            of(
              NotificationActions.markAsReadFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du marquage comme lu',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Mark all notifications as read.
 */
export const markAllAsRead$ = createEffect(
  (actions$ = inject(Actions), service = inject(NotificationService)) =>
    actions$.pipe(
      ofType(NotificationActions.markAllAsRead),
      switchMap(() =>
        service.markAllAsRead().pipe(
          map(() => NotificationActions.markAllAsReadSuccess()),
          catchError((error) =>
            of(
              NotificationActions.markAllAsReadFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du marquage comme lu',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Load notification preferences.
 */
export const loadPreferences$ = createEffect(
  (actions$ = inject(Actions), service = inject(NotificationService)) =>
    actions$.pipe(
      ofType(NotificationActions.loadPreferences),
      switchMap(() =>
        service.getPreferences().pipe(
          map((preferences) =>
            NotificationActions.loadPreferencesSuccess({ preferences })
          ),
          catchError((error) =>
            of(
              NotificationActions.loadPreferencesFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement des preferences',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Update a notification preference.
 */
export const updatePreference$ = createEffect(
  (actions$ = inject(Actions), service = inject(NotificationService)) =>
    actions$.pipe(
      ofType(NotificationActions.updatePreference),
      switchMap(({ request }) =>
        service.updatePreference(request).pipe(
          map((preference) =>
            NotificationActions.updatePreferenceSuccess({ preference })
          ),
          catchError((error) =>
            of(
              NotificationActions.updatePreferenceFailure({
                error: error?.error?.message || error?.message || 'Erreur lors de la mise a jour de la preference',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);
