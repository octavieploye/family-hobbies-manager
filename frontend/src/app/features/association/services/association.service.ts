// frontend/src/app/features/association/services/association.service.ts
import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  Association,
  AssociationDetail,
  AssociationSearchRequest,
  PageResponse,
} from '../models/association.model';

/**
 * HTTP service for association-related API calls.
 *
 * Base URL: environment.apiBaseUrl + '/associations'
 * All association search endpoints are PUBLIC (no JWT required).
 */
@Injectable({ providedIn: 'root' })
export class AssociationService {
  private readonly API_BASE = `${environment.apiBaseUrl}/associations`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Search associations with optional filters and pagination.
   * GET /api/v1/associations?city=X&category=Y&keyword=Z&page=N&size=N
   */
  search(request: AssociationSearchRequest): Observable<PageResponse<Association>> {
    let params = new HttpParams();

    if (request.city) {
      params = params.set('city', request.city);
    }
    if (request.category) {
      params = params.set('category', request.category);
    }
    if (request.keyword) {
      params = params.set('keyword', request.keyword);
    }
    if (request.page !== undefined && request.page !== null) {
      params = params.set('page', request.page.toString());
    }
    if (request.size !== undefined && request.size !== null) {
      params = params.set('size', request.size.toString());
    }

    return this.http.get<PageResponse<Association>>(this.API_BASE, { params });
  }

  /**
   * Get association detail by numeric ID.
   * GET /api/v1/associations/{id}
   */
  getById(id: number): Observable<AssociationDetail> {
    return this.http.get<AssociationDetail>(`${this.API_BASE}/${id}`);
  }

  /**
   * Get association detail by slug.
   * GET /api/v1/associations/slug/{slug}
   */
  getBySlug(slug: string): Observable<AssociationDetail> {
    return this.http.get<AssociationDetail>(`${this.API_BASE}/slug/${slug}`);
  }
}
