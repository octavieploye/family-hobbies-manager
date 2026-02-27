// frontend/src/app/features/family/services/family.service.spec.ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { FamilyService } from './family.service';
import { Family, FamilyMember } from '../models/family.model';

/**
 * Unit tests for FamilyService.
 *
 * Story: S2-002 — Angular Family Feature
 * Tests: 4 test methods
 *
 * These tests verify:
 * 1. getMyFamily() calls GET /families/me and returns a Family
 * 2. createFamily() calls POST /families and returns a Family
 * 3. addMember() calls POST /families/{id}/members and returns a FamilyMember
 * 4. removeMember() calls DELETE /family-members/{id}
 *
 * Uses provideHttpClient() + provideHttpClientTesting() (Angular 17+ API).
 */
describe('FamilyService', () => {
  let service: FamilyService;
  let httpMock: HttpTestingController;

  const mockFamily: Family = {
    id: 1,
    name: 'Famille Dupont',
    createdBy: 100,
    members: [],
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  };

  const mockMember: FamilyMember = {
    id: 10,
    familyId: 1,
    firstName: 'Jean',
    lastName: 'Dupont',
    dateOfBirth: '1985-06-15',
    age: 40,
    relationship: 'PARENT',
    createdAt: '2025-01-01T00:00:00',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        FamilyService,
      ],
    });

    service = TestBed.inject(FamilyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should call GET /families/me and return family when getMyFamily called', () => {
    service.getMyFamily().subscribe((family) => {
      expect(family).toEqual(mockFamily);
    });

    const req = httpMock.expectOne((r) => r.url.endsWith('/families/me'));
    expect(req.request.method).toBe('GET');
    req.flush(mockFamily);
  });

  it('should call POST /families and return family when createFamily called', () => {
    const request = { name: 'Famille Dupont' };

    service.createFamily(request).subscribe((family) => {
      expect(family).toEqual(mockFamily);
    });

    const req = httpMock.expectOne((r) =>
      r.url.endsWith('/families') && !r.url.endsWith('/families/me')
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockFamily);
  });

  it('should call POST /families/1/members and return member when addMember called', () => {
    const request = {
      firstName: 'Jean',
      lastName: 'Dupont',
      dateOfBirth: '1985-06-15',
      relationship: 'PARENT',
    };

    service.addMember(1, request).subscribe((member) => {
      expect(member).toEqual(mockMember);
    });

    const req = httpMock.expectOne((r) => r.url.endsWith('/families/1/members'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockMember);
  });

  it('should call DELETE /family-members/10 when removeMember called', () => {
    service.removeMember(10).subscribe();

    const req = httpMock.expectOne((r) => r.url.endsWith('/family-members/10'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
