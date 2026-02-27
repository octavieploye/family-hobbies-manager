// frontend/src/app/features/family/services/family.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environment';
import {
  Family,
  FamilyMember,
  FamilyMemberRequest,
  FamilyRequest,
} from '../models/family.model';

/**
 * Service for family and family member CRUD operations.
 *
 * All endpoints require JWT + FAMILY role.
 * Base URL: environment.apiBaseUrl + '/families' (apiBaseUrl already includes /api/v1).
 *
 * Backend API mapping (from code-dev2.md):
 * - GET    /families/me             -> getMyFamily()
 * - POST   /families                -> createFamily()
 * - PUT    /families/{id}           -> updateFamily()
 * - POST   /families/{id}/members   -> addMember()
 * - PUT    /family-members/{id}     -> updateMember()
 * - DELETE /family-members/{id}     -> removeMember()
 */
@Injectable({ providedIn: 'root' })
export class FamilyService {
  private readonly familiesUrl = `${environment.apiBaseUrl}/families`;
  private readonly membersUrl = `${environment.apiBaseUrl}/family-members`;

  constructor(private readonly http: HttpClient) {}

  /**
   * Get the authenticated user's family with all members.
   * GET /families/me
   */
  getMyFamily(): Observable<Family> {
    return this.http.get<Family>(`${this.familiesUrl}/me`);
  }

  /**
   * Create a new family for the authenticated user.
   * POST /families
   */
  createFamily(request: FamilyRequest): Observable<Family> {
    return this.http.post<Family>(this.familiesUrl, request);
  }

  /**
   * Update an existing family's details.
   * PUT /families/{id}
   */
  updateFamily(id: number, request: FamilyRequest): Observable<Family> {
    return this.http.put<Family>(`${this.familiesUrl}/${id}`, request);
  }

  /**
   * Add a new member to a family.
   * POST /families/{familyId}/members
   */
  addMember(familyId: number, request: FamilyMemberRequest): Observable<FamilyMember> {
    return this.http.post<FamilyMember>(
      `${this.familiesUrl}/${familyId}/members`,
      request
    );
  }

  /**
   * Update an existing family member.
   * PUT /family-members/{memberId}
   */
  updateMember(memberId: number, request: FamilyMemberRequest): Observable<FamilyMember> {
    return this.http.put<FamilyMember>(
      `${this.membersUrl}/${memberId}`,
      request
    );
  }

  /**
   * Remove a member from a family.
   * DELETE /family-members/{memberId}
   */
  removeMember(memberId: number): Observable<void> {
    return this.http.delete<void>(`${this.membersUrl}/${memberId}`);
  }
}
