// frontend/src/app/features/settings/services/rgpd.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  ConsentStatus,
  ConsentRequest,
  UserDataExport,
} from '@shared/models/rgpd.model';

/**
 * HTTP service for RGPD-related API calls.
 *
 * Base URL: environment.apiBaseUrl + '/rgpd'
 * All RGPD endpoints require JWT authentication.
 */
@Injectable({ providedIn: 'root' })
export class RgpdService {
  private readonly API_BASE = `${environment.apiBaseUrl}/rgpd`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Record a consent decision.
   * POST /api/v1/rgpd/consent
   */
  recordConsent(request: ConsentRequest): Observable<ConsentStatus> {
    return this.http.post<ConsentStatus>(`${this.API_BASE}/consent`, request);
  }

  /**
   * Get current consent status for the authenticated user.
   * GET /api/v1/rgpd/consent
   */
  getConsentStatus(): Observable<ConsentStatus[]> {
    return this.http.get<ConsentStatus[]>(`${this.API_BASE}/consent`);
  }

  /**
   * Get consent history (audit trail) for the authenticated user.
   * GET /api/v1/rgpd/consent/history
   */
  getConsentHistory(): Observable<ConsentStatus[]> {
    return this.http.get<ConsentStatus[]>(`${this.API_BASE}/consent/history`);
  }

  /**
   * Export user data (RGPD portability).
   * GET /api/v1/rgpd/export
   */
  exportData(): Observable<UserDataExport> {
    return this.http.get<UserDataExport>(`${this.API_BASE}/export`);
  }

  /**
   * Delete the user's account (RGPD right to erasure).
   * DELETE /api/v1/rgpd/account
   */
  deleteAccount(password: string, reason?: string): Observable<void> {
    return this.http.delete<void>(`${this.API_BASE}/account`, {
      body: { password, reason },
    });
  }
}
