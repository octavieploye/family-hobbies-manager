// frontend/src/app/shared/models/dashboard.model.ts
// TypeScript interfaces for the family dashboard feature

/**
 * Aggregated dashboard data for a family.
 * Composed from multiple API calls in the DashboardService.
 */
export interface DashboardData {
  familyName: string;
  memberCount: number;
  activeSubscriptions: SubscriptionSummary[];
  upcomingSessions: UpcomingSession[];
  attendanceOverview: MemberAttendance[];
  recentNotifications: number;
}

/**
 * Lightweight subscription info for dashboard display.
 */
export interface SubscriptionSummary {
  subscriptionId: number;
  memberName: string;
  activityName: string;
  associationName: string;
  status: string;
  startDate: string;
}

/**
 * Upcoming session info for the next 7 days widget.
 */
export interface UpcomingSession {
  sessionId: number;
  activityName: string;
  associationName: string;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  location: string | null;
  memberNames: string[];
}

/**
 * Per-member attendance rate for dashboard overview.
 */
export interface MemberAttendance {
  memberId: number;
  memberName: string;
  attendanceRate: number;
  totalSessions: number;
}
