// frontend/src/app/features/subscriptions/services/subscription.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  Subscription,
  SubscriptionRequest,
} from '@shared/models/subscription.model';

/**
 * HTTP service for subscription-related API calls.
 *
 * Base URL: environment.apiBaseUrl + '/subscriptions'
 * All subscription endpoints require JWT authentication.
 */
@Injectable({ providedIn: 'root' })
export class SubscriptionService {
  private readonly API_BASE = `${environment.apiBaseUrl}/subscriptions`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Create a new subscription.
   * POST /api/v1/subscriptions
   */
  create(request: SubscriptionRequest): Observable<Subscription> {
    return this.http.post<Subscription>(this.API_BASE, request);
  }

  /**
   * List subscriptions for a family.
   * GET /api/v1/subscriptions/family/{familyId}
   */
  getByFamily(familyId: number): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`${this.API_BASE}/family/${familyId}`);
  }

  /**
   * List subscriptions for a family member.
   * GET /api/v1/subscriptions/member/{memberId}
   */
  getByMember(memberId: number): Observable<Subscription[]> {
    return this.http.get<Subscription[]>(`${this.API_BASE}/member/${memberId}`);
  }

  /**
   * Get subscription detail by ID.
   * GET /api/v1/subscriptions/{subscriptionId}
   */
  getById(subscriptionId: number): Observable<Subscription> {
    return this.http.get<Subscription>(`${this.API_BASE}/${subscriptionId}`);
  }

  /**
   * Cancel a subscription with an optional reason.
   * PUT /api/v1/subscriptions/{subscriptionId}/cancel?reason=...
   */
  cancel(subscriptionId: number, reason?: string): Observable<Subscription> {
    let url = `${this.API_BASE}/${subscriptionId}/cancel`;
    if (reason) {
      url += `?reason=${encodeURIComponent(reason)}`;
    }
    return this.http.put<Subscription>(url, {});
  }
}
