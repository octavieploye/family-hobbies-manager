// frontend/src/app/features/notifications/store/notification.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { NotificationActions } from './notification.actions';
import { NotificationState, initialNotificationState } from './notification.state';

/**
 * Notifications feature reducer.
 * Handles notification loading, unread count, preferences, and dropdown state.
 */
export const notificationReducer = createReducer(
  initialNotificationState,

  // --- Load Notifications ---
  on(NotificationActions.loadNotifications, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(NotificationActions.loadNotificationsSuccess, (state, { notificationPage }) => ({
    ...state,
    notifications: notificationPage.content,
    totalElements: notificationPage.totalElements,
    totalPages: notificationPage.totalPages,
    currentPage: notificationPage.number,
    pageSize: notificationPage.size,
    loading: false,
    error: null,
  })),

  on(NotificationActions.loadNotificationsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Unread Count ---
  on(NotificationActions.loadUnreadCount, (state) => ({
    ...state,
  })),

  on(NotificationActions.loadUnreadCountSuccess, (state, { unreadCount }) => ({
    ...state,
    unreadCount: unreadCount.count,
  })),

  on(NotificationActions.loadUnreadCountFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  // --- Mark As Read ---
  on(NotificationActions.markAsRead, (state) => ({
    ...state,
  })),

  on(NotificationActions.markAsReadSuccess, (state, { id }) => ({
    ...state,
    notifications: state.notifications.map((n) =>
      n.id === id ? { ...n, read: true, readAt: new Date().toISOString() } : n
    ),
    unreadCount: Math.max(0, state.unreadCount - 1),
  })),

  on(NotificationActions.markAsReadFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  // --- Mark All As Read ---
  on(NotificationActions.markAllAsRead, (state) => ({
    ...state,
  })),

  on(NotificationActions.markAllAsReadSuccess, (state) => ({
    ...state,
    notifications: state.notifications.map((n) => ({
      ...n,
      read: true,
      readAt: n.readAt ?? new Date().toISOString(),
    })),
    unreadCount: 0,
  })),

  on(NotificationActions.markAllAsReadFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  // --- Preferences ---
  on(NotificationActions.loadPreferences, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(NotificationActions.loadPreferencesSuccess, (state, { preferences }) => ({
    ...state,
    preferences,
    loading: false,
    error: null,
  })),

  on(NotificationActions.loadPreferencesFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(NotificationActions.updatePreference, (state) => ({
    ...state,
  })),

  on(NotificationActions.updatePreferenceSuccess, (state, { preference }) => ({
    ...state,
    preferences: state.preferences.map((p) =>
      p.category === preference.category ? preference : p
    ),
  })),

  on(NotificationActions.updatePreferenceFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  // --- UI ---
  on(NotificationActions.toggleDropdown, (state) => ({
    ...state,
    dropdownOpen: !state.dropdownOpen,
  }))
);
