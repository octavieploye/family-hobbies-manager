// frontend/src/app/features/notifications/store/notification.actions.ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import {
  Notification,
  NotificationPage,
  NotificationPreference,
  NotificationPreferenceRequest,
  UnreadCount,
} from '@shared/models/notification.model';

/**
 * NgRx action group for the Notifications feature.
 *
 * Follows the [Source] Event naming convention.
 * Each async operation has a triplet: trigger / success / failure.
 */
export const NotificationActions = createActionGroup({
  source: 'Notifications',
  events: {
    // --- Load Notifications ---
    'Load Notifications': props<{ page: number; size: number }>(),
    'Load Notifications Success': props<{ notificationPage: NotificationPage }>(),
    'Load Notifications Failure': props<{ error: string }>(),

    // --- Unread Count ---
    'Load Unread Count': emptyProps(),
    'Load Unread Count Success': props<{ unreadCount: UnreadCount }>(),
    'Load Unread Count Failure': props<{ error: string }>(),

    // --- Mark As Read ---
    'Mark As Read': props<{ id: number }>(),
    'Mark As Read Success': props<{ id: number }>(),
    'Mark As Read Failure': props<{ error: string }>(),

    // --- Mark All As Read ---
    'Mark All As Read': emptyProps(),
    'Mark All As Read Success': emptyProps(),
    'Mark All As Read Failure': props<{ error: string }>(),

    // --- Preferences ---
    'Load Preferences': emptyProps(),
    'Load Preferences Success': props<{ preferences: NotificationPreference[] }>(),
    'Load Preferences Failure': props<{ error: string }>(),

    'Update Preference': props<{ request: NotificationPreferenceRequest }>(),
    'Update Preference Success': props<{ preference: NotificationPreference }>(),
    'Update Preference Failure': props<{ error: string }>(),

    // --- UI ---
    'Toggle Dropdown': emptyProps(),
  },
});
