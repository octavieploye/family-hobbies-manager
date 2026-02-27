// frontend/src/app/features/notifications/store/notification.selectors.ts
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { NotificationState } from './notification.state';

/**
 * Feature selector for the 'notifications' state slice.
 * The key 'notifications' must match the key used in provideState().
 */
export const selectNotificationState =
  createFeatureSelector<NotificationState>('notifications');

/** Select the list of notifications. */
export const selectNotifications = createSelector(
  selectNotificationState,
  (state) => state.notifications
);

/** Select the unread notification count. */
export const selectUnreadCount = createSelector(
  selectNotificationState,
  (state) => state.unreadCount
);

/** Select notification preferences. */
export const selectPreferences = createSelector(
  selectNotificationState,
  (state) => state.preferences
);

/** Select total number of notification records (across all pages). */
export const selectTotalElements = createSelector(
  selectNotificationState,
  (state) => state.totalElements
);

/** Select the current page index (0-based). */
export const selectCurrentPage = createSelector(
  selectNotificationState,
  (state) => state.currentPage
);

/** Select the page size. */
export const selectPageSize = createSelector(
  selectNotificationState,
  (state) => state.pageSize
);

/** Select whether notifications are loading. */
export const selectLoading = createSelector(
  selectNotificationState,
  (state) => state.loading
);

/** Select the current error message, if any. */
export const selectError = createSelector(
  selectNotificationState,
  (state) => state.error
);

/** Select whether the dropdown is open. */
export const selectDropdownOpen = createSelector(
  selectNotificationState,
  (state) => state.dropdownOpen
);

/**
 * Composite selector for pagination state.
 * Useful for binding directly to MatPaginator.
 */
export const selectPagination = createSelector(
  selectTotalElements,
  selectCurrentPage,
  selectPageSize,
  (totalElements, currentPage, pageSize) => ({
    totalElements,
    currentPage,
    pageSize,
  })
);

/** Select the last 5 notifications for the dropdown. */
export const selectRecentNotifications = createSelector(
  selectNotifications,
  (notifications) => notifications.slice(0, 5)
);
