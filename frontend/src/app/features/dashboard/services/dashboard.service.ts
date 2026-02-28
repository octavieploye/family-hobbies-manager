// frontend/src/app/features/dashboard/services/dashboard.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, forkJoin, of } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import {
  DashboardData,
  SubscriptionSummary,
  MemberAttendance,
} from '@shared/models/dashboard.model';
import { Family } from '../../family/models/family.model';
import { Subscription } from '@shared/models/subscription.model';
import { AttendanceSummary } from '@shared/models/attendance.model';

/**
 * Aggregation service for the family dashboard.
 *
 * Combines data from multiple backend endpoints:
 * - GET /families/me (family info)
 * - GET /subscriptions/family/{familyId} (active subscriptions)
 * - GET /attendance/member/{memberId}/summary (per member attendance)
 */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);
  private readonly apiBase = environment.apiBaseUrl;

  /**
   * Load family info.
   * GET /api/v1/families/me
   */
  getFamily(): Observable<Family> {
    return this.http.get<Family>(`${this.apiBase}/families/me`);
  }

  /**
   * Load subscriptions for a family.
   * GET /api/v1/subscriptions/family/{familyId}
   */
  getFamilySubscriptions(familyId: number): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`${this.apiBase}/subscriptions/family/${familyId}`);
  }

  /**
   * Load attendance summary for a member.
   * GET /api/v1/attendance/member/{memberId}/summary
   */
  getMemberAttendanceSummary(memberId: number): Observable<AttendanceSummary> {
    return this.http.get<AttendanceSummary>(
      `${this.apiBase}/attendance/member/${memberId}/summary`
    );
  }

  /**
   * Map subscriptions to the dashboard-friendly summary format.
   */
  mapToSubscriptionSummaries(subscriptions: Subscription[]): SubscriptionSummary[] {
    return subscriptions
      .filter((s) => s.status === 'ACTIVE' || s.status === 'PENDING')
      .map((s) => ({
        subscriptionId: s.id,
        memberName: `${s.memberFirstName} ${s.memberLastName}`,
        activityName: s.activityName,
        associationName: s.associationName,
        status: s.status,
        startDate: s.startDate,
      }));
  }

  /**
   * Map attendance summaries to the dashboard-friendly format.
   */
  mapToMemberAttendance(summaries: AttendanceSummary[]): MemberAttendance[] {
    return summaries.map((s) => ({
      memberId: s.familyMemberId,
      memberName: `${s.memberFirstName} ${s.memberLastName}`,
      attendanceRate: s.attendanceRate,
      totalSessions: s.totalSessions,
    }));
  }

  /**
   * Load attendance summaries for multiple members in parallel.
   * Returns an empty array if any call fails (graceful degradation).
   */
  getMemberAttendanceSummaries(memberIds: number[]): Observable<AttendanceSummary[]> {
    if (memberIds.length === 0) {
      return of([]);
    }
    const calls = memberIds.map((id) =>
      this.getMemberAttendanceSummary(id).pipe(
        catchError(() => of(null))
      )
    );
    return forkJoin(calls).pipe(
      map((results) => results.filter((r): r is AttendanceSummary => r !== null))
    );
  }
}
