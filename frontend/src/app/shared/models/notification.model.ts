// frontend/src/app/shared/models/notification.model.ts

export enum NotificationType {
  EMAIL = 'EMAIL',
  IN_APP = 'IN_APP',
  PUSH = 'PUSH',
}

export enum NotificationCategory {
  WELCOME = 'WELCOME',
  PAYMENT = 'PAYMENT',
  SUBSCRIPTION = 'SUBSCRIPTION',
  ATTENDANCE = 'ATTENDANCE',
  SYSTEM = 'SYSTEM',
}

export interface Notification {
  id: number;
  userId: number;
  type: NotificationType;
  category: NotificationCategory;
  title: string;
  message: string;
  read: boolean;
  referenceId: string | null;
  referenceType: string | null;
  createdAt: string;
  readAt: string | null;
}

export interface UnreadCount {
  count: number;
}

export interface NotificationPreference {
  id: number;
  userId: number;
  category: NotificationCategory;
  emailEnabled: boolean;
  inAppEnabled: boolean;
}

export interface NotificationPreferenceRequest {
  category: NotificationCategory;
  emailEnabled: boolean;
  inAppEnabled: boolean;
}

export interface NotificationPage {
  content: Notification[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const CATEGORY_LABELS: Record<NotificationCategory, string> = {
  [NotificationCategory.WELCOME]: 'Bienvenue',
  [NotificationCategory.PAYMENT]: 'Paiement',
  [NotificationCategory.SUBSCRIPTION]: 'Inscription',
  [NotificationCategory.ATTENDANCE]: 'Presence',
  [NotificationCategory.SYSTEM]: 'Systeme',
};
