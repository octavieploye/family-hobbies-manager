// frontend/src/app/shared/models/subscription.model.ts
// TypeScript interfaces matching backend Subscription DTOs

export type SubscriptionStatus = 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED';
export type SubscriptionType = 'ADHESION' | 'COTISATION';

/**
 * Represents a subscription linking a family member to an activity.
 * Maps to backend SubscriptionResponse DTO field-for-field.
 */
export interface Subscription {
  id: number;
  activityId: number;
  activityName: string;
  associationName: string;
  familyMemberId: number;
  memberFirstName: string;
  memberLastName: string;
  familyId: number;
  userId: number;
  subscriptionType: SubscriptionType;
  status: SubscriptionStatus;
  startDate: string;
  endDate: string | null;
  cancellationReason: string | null;
  cancelledAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Request payload for creating a new subscription.
 * Maps to backend SubscriptionRequest DTO.
 */
export interface SubscriptionRequest {
  activityId: number;
  familyMemberId: number;
  familyId: number;
  subscriptionType: SubscriptionType;
  startDate: string;
  endDate?: string;
}

/**
 * Status configuration for display in UI with French labels and Material color.
 */
export const SUBSCRIPTION_STATUS_CONFIG: Record<SubscriptionStatus, { label: string; color: string }> = {
  PENDING: { label: 'En attente', color: 'warn' },
  ACTIVE: { label: 'Active', color: 'primary' },
  EXPIRED: { label: 'Expir\u00e9e', color: 'accent' },
  CANCELLED: { label: 'Annul\u00e9e', color: '' },
};
