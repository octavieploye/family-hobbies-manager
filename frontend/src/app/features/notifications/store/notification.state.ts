// frontend/src/app/features/notifications/store/notification.state.ts
import {
  Notification,
  NotificationPreference,
} from '@shared/models/notification.model';

/**
 * State shape for the Notifications feature store.
 */
export interface NotificationState {
  notifications: Notification[];
  unreadCount: number;
  preferences: NotificationPreference[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  loading: boolean;
  error: string | null;
  dropdownOpen: boolean;
}

/**
 * Initial state with sensible defaults.
 */
export const initialNotificationState: NotificationState = {
  notifications: [],
  unreadCount: 0,
  preferences: [],
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
  pageSize: 10,
  loading: false,
  error: null,
  dropdownOpen: false,
};
