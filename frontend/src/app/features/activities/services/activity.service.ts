// frontend/src/app/features/activities/services/activity.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  Activity,
  ActivityDetail,
  ActivitySearchRequest,
} from '@shared/models/activity.model';
import { PageResponse } from '../../association/models/association.model';

/**
 * HTTP service for activity-related API calls.
 *
 * Base URL: environment.apiBaseUrl + '/associations/{associationId}/activities'
 * All activity browse endpoints are PUBLIC (no JWT required).
 */
@Injectable({ providedIn: 'root' })
export class ActivityService {
  private readonly API_BASE = `${environment.apiBaseUrl}/associations`;

  constructor(private readonly http: HttpClient) {}

  /**
   * List activities for an association with optional filters and pagination.
   * GET /api/v1/associations/{associationId}/activities?category=X&level=Y&page=N&size=N
   */
  getActivities(request: ActivitySearchRequest): Observable<PageResponse<Activity>> {
    let params = new HttpParams();

    if (request.category) {
      params = params.set('category', request.category);
    }
    if (request.level) {
      params = params.set('level', request.level);
    }
    if (request.page !== undefined && request.page !== null) {
      params = params.set('page', request.page.toString());
    }
    if (request.size !== undefined && request.size !== null) {
      params = params.set('size', request.size.toString());
    }

    return this.http.get<PageResponse<Activity>>(
      `${this.API_BASE}/${request.associationId}/activities`,
      { params }
    );
  }

  /**
   * Get activity detail by activity ID within an association.
   * GET /api/v1/associations/{associationId}/activities/{activityId}
   */
  getActivityDetail(associationId: number, activityId: number): Observable<ActivityDetail> {
    return this.http.get<ActivityDetail>(
      `${this.API_BASE}/${associationId}/activities/${activityId}`
    );
  }
}
