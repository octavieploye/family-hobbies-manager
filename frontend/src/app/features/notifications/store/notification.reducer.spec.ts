// frontend/src/app/features/notifications/store/notification.reducer.spec.ts
import { notificationReducer } from './notification.reducer';
import { NotificationState, initialNotificationState } from './notification.state';
import { NotificationActions } from './notification.actions';
import {
  Notification,
  NotificationCategory,
  NotificationPage,
  NotificationPreference,
  NotificationType,
} from '@shared/models/notification.model';

describe('NotificationReducer', () => {
  const mockNotification: Notification = {
    id: 1,
    userId: 1,
    type: NotificationType.IN_APP,
    category: NotificationCategory.PAYMENT,
    title: 'Paiement confirme',
    message: 'Votre paiement de 150 EUR a ete confirme.',
    read: false,
    referenceId: '42',
    referenceType: 'PAYMENT',
    createdAt: '2026-02-27T10:00:00',
    readAt: null,
  };

  const mockPage: NotificationPage = {
    content: [mockNotification],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 10,
  };

  const mockPreference: NotificationPreference = {
    id: 1,
    userId: 1,
    category: NotificationCategory.PAYMENT,
    emailEnabled: true,
    inAppEnabled: true,
  };

  it('should return the initial state when action is undefined', () => {
    const action = { type: 'UNKNOWN' } as any;
    const state = notificationReducer(undefined, action);
    expect(state).toEqual(initialNotificationState);
  });

  it('should set loading to true when loadNotifications is dispatched', () => {
    const action = NotificationActions.loadNotifications({ page: 0, size: 10 });
    const state = notificationReducer(initialNotificationState, action);

    expect(state.loading).toBe(true);
    expect(state.error).toBeNull();
  });

  it('should store notifications when loadNotificationsSuccess is dispatched', () => {
    const action = NotificationActions.loadNotificationsSuccess({ notificationPage: mockPage });
    const state = notificationReducer(
      { ...initialNotificationState, loading: true },
      action
    );

    expect(state.notifications).toEqual([mockNotification]);
    expect(state.totalElements).toBe(1);
    expect(state.totalPages).toBe(1);
    expect(state.currentPage).toBe(0);
    expect(state.pageSize).toBe(10);
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should store error when loadNotificationsFailure is dispatched', () => {
    const action = NotificationActions.loadNotificationsFailure({
      error: 'Erreur reseau',
    });
    const state = notificationReducer(
      { ...initialNotificationState, loading: true },
      action
    );

    expect(state.loading).toBe(false);
    expect(state.error).toBe('Erreur reseau');
  });

  it('should update unread count when loadUnreadCountSuccess is dispatched', () => {
    const action = NotificationActions.loadUnreadCountSuccess({
      unreadCount: { count: 3 },
    });
    const state = notificationReducer(initialNotificationState, action);

    expect(state.unreadCount).toBe(3);
  });

  it('should mark a notification as read when markAsReadSuccess is dispatched', () => {
    const stateWithUnread: NotificationState = {
      ...initialNotificationState,
      notifications: [mockNotification],
      unreadCount: 1,
    };

    const action = NotificationActions.markAsReadSuccess({ id: 1 });
    const state = notificationReducer(stateWithUnread, action);

    expect(state.notifications[0].read).toBe(true);
    expect(state.notifications[0].readAt).toBeTruthy();
    expect(state.unreadCount).toBe(0);
  });

  it('should mark all notifications as read when markAllAsReadSuccess is dispatched', () => {
    const secondNotification: Notification = {
      ...mockNotification,
      id: 2,
      title: 'Bienvenue',
    };

    const stateWithUnread: NotificationState = {
      ...initialNotificationState,
      notifications: [mockNotification, secondNotification],
      unreadCount: 2,
    };

    const action = NotificationActions.markAllAsReadSuccess();
    const state = notificationReducer(stateWithUnread, action);

    expect(state.notifications.every((n) => n.read)).toBe(true);
    expect(state.unreadCount).toBe(0);
  });

  it('should store preferences when loadPreferencesSuccess is dispatched', () => {
    const action = NotificationActions.loadPreferencesSuccess({
      preferences: [mockPreference],
    });
    const state = notificationReducer(
      { ...initialNotificationState, loading: true },
      action
    );

    expect(state.preferences).toEqual([mockPreference]);
    expect(state.loading).toBe(false);
  });

  it('should update a preference when updatePreferenceSuccess is dispatched', () => {
    const updatedPreference: NotificationPreference = {
      ...mockPreference,
      emailEnabled: false,
    };

    const stateWithPreferences: NotificationState = {
      ...initialNotificationState,
      preferences: [mockPreference],
    };

    const action = NotificationActions.updatePreferenceSuccess({
      preference: updatedPreference,
    });
    const state = notificationReducer(stateWithPreferences, action);

    expect(state.preferences[0].emailEnabled).toBe(false);
    expect(state.preferences[0].inAppEnabled).toBe(true);
  });

  it('should toggle dropdown open/close when toggleDropdown is dispatched', () => {
    const action = NotificationActions.toggleDropdown();

    // First toggle: open
    const state1 = notificationReducer(initialNotificationState, action);
    expect(state1.dropdownOpen).toBe(true);

    // Second toggle: close
    const state2 = notificationReducer(state1, action);
    expect(state2.dropdownOpen).toBe(false);
  });
});
