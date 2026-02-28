// frontend/src/app/features/attendance/services/attendance.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  Attendance,
  AttendanceSummary,
  BulkAttendanceRequest,
} from '@shared/models/attendance.model';

/**
 * HTTP service for attendance-related API calls.
 *
 * Base URL: environment.apiBaseUrl + '/attendance'
 * All attendance endpoints require JWT authentication.
 */
@Injectable({ providedIn: 'root' })
export class AttendanceService {
  private readonly API_BASE = `${environment.apiBaseUrl}/attendance`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Mark a single attendance record.
   * POST /api/v1/attendance
   */
  markSingle(request: Attendance): Observable<Attendance> {
    return this.http.post<Attendance>(this.API_BASE, request);
  }

  /**
   * Mark attendance in bulk for a session.
   * POST /api/v1/attendance/bulk
   */
  markBulk(request: BulkAttendanceRequest): Observable<Attendance[]> {
    return this.http.post<Attendance[]>(`${this.API_BASE}/bulk`, request);
  }

  /**
   * Get attendance records for a session on a specific date.
   * GET /api/v1/attendance/session/{sessionId}?date=YYYY-MM-DD
   */
  getBySession(sessionId: number, date: string): Observable<Attendance[]> {
    const params = new HttpParams().set('date', date);
    return this.http.get<Attendance[]>(`${this.API_BASE}/session/${sessionId}`, { params });
  }

  /**
   * Get attendance history for a member.
   * GET /api/v1/attendance/member/{memberId}
   */
  getByMember(memberId: number): Observable<Attendance[]> {
    return this.http.get<Attendance[]>(`${this.API_BASE}/member/${memberId}`);
  }

  /**
   * Get attendance summary for a member.
   * GET /api/v1/attendance/member/{memberId}/summary
   */
  getMemberSummary(memberId: number): Observable<AttendanceSummary> {
    return this.http.get<AttendanceSummary>(`${this.API_BASE}/member/${memberId}/summary`);
  }

  /**
   * Get attendance records by subscription.
   * GET /api/v1/attendance/subscription/{subscriptionId}
   */
  getBySubscription(subscriptionId: number): Observable<Attendance[]> {
    return this.http.get<Attendance[]>(`${this.API_BASE}/subscription/${subscriptionId}`);
  }

  /**
   * Update an existing attendance record.
   * PUT /api/v1/attendance/{attendanceId}
   */
  update(attendanceId: number, request: Partial<Attendance>): Observable<Attendance> {
    return this.http.put<Attendance>(`${this.API_BASE}/${attendanceId}`, request);
  }
}
