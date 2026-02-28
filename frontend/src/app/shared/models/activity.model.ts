// frontend/src/app/shared/models/activity.model.ts
// TypeScript interfaces matching backend Activity and Session DTOs

/**
 * Summary representation of an activity, used in list views.
 * Maps to backend ActivityResponse DTO field-for-field.
 */
export interface Activity {
  id: number;
  name: string;
  category: string;
  level: string;
  minAge: number | null;
  maxAge: number | null;
  priceCents: number;
  status: string;
  sessionCount: number;
}

/**
 * Full detail of a single activity including its sessions.
 * Maps to backend ActivityDetailResponse DTO field-for-field.
 */
export interface ActivityDetail {
  id: number;
  associationId: number;
  associationName: string;
  name: string;
  description: string | null;
  category: string;
  level: string;
  minAge: number | null;
  maxAge: number | null;
  maxCapacity: number | null;
  priceCents: number;
  seasonStart: string | null;
  seasonEnd: string | null;
  status: string;
  sessions: Session[];
  createdAt: string;
  updatedAt: string;
}

/**
 * Represents a recurring session for an activity.
 * Maps to backend SessionResponse DTO field-for-field.
 */
export interface Session {
  id: number;
  activityId: number;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  location: string | null;
  instructorName: string | null;
  maxCapacity: number | null;
  active: boolean;
}

/**
 * Search request parameters for the activity list endpoint.
 */
export interface ActivitySearchRequest {
  associationId: number;
  category?: string;
  level?: string;
  page?: number;
  size?: number;
}
