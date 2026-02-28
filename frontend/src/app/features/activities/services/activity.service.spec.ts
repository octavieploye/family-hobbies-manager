// frontend/src/app/features/activities/services/activity.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { ActivityService } from './activity.service';
import {
  Activity,
  ActivityDetail,
  ActivitySearchRequest,
  Session,
} from '@shared/models/activity.model';
import { PageResponse } from '../../association/models/association.model';
import { environment } from '../../../../environments/environment';

describe('ActivityService', () => {
  let service: ActivityService;
  let httpMock: HttpTestingController;
  const API_BASE = `${environment.apiBaseUrl}/associations`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(ActivityService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getActivities', () => {
    it('should send GET request with associationId and return paginated activities', () => {
      const request: ActivitySearchRequest = {
        associationId: 1,
        page: 0,
        size: 20,
      };

      const mockResponse: PageResponse<Activity> = {
        content: [
          {
            id: 1,
            name: 'Natation enfants',
            category: 'Sport',
            level: 'BEGINNER',
            minAge: 6,
            maxAge: 10,
            priceCents: 15000,
            status: 'ACTIVE',
            sessionCount: 2,
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      };

      service.getActivities(request).subscribe((result) => {
        expect(result).toEqual(mockResponse);
        expect(result.content.length).toBe(1);
        expect(result.totalElements).toBe(1);
      });

      const req = httpMock.expectOne(
        (r) =>
          r.url === `${API_BASE}/1/activities` &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '20'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should include category and level params when provided', () => {
      const request: ActivitySearchRequest = {
        associationId: 2,
        category: 'Danse',
        level: 'BEGINNER',
        page: 0,
        size: 10,
      };

      const mockResponse: PageResponse<Activity> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 10,
      };

      service.getActivities(request).subscribe((result) => {
        expect(result.content.length).toBe(0);
      });

      const req = httpMock.expectOne(
        (r) =>
          r.url === `${API_BASE}/2/activities` &&
          r.params.get('category') === 'Danse' &&
          r.params.get('level') === 'BEGINNER' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '10'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should omit optional params when not provided', () => {
      const request: ActivitySearchRequest = {
        associationId: 1,
      };

      const mockResponse: PageResponse<Activity> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 20,
      };

      service.getActivities(request).subscribe();

      const req = httpMock.expectOne(
        (r) =>
          r.url === `${API_BASE}/1/activities` &&
          !r.params.has('category') &&
          !r.params.has('level') &&
          !r.params.has('page') &&
          !r.params.has('size')
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });
  });

  describe('getActivityDetail', () => {
    it('should send GET request to correct URL and return activity detail', () => {
      const mockSession: Session = {
        id: 1,
        activityId: 5,
        dayOfWeek: 'TUESDAY',
        startTime: '17:00',
        endTime: '18:00',
        location: 'Piscine municipale',
        instructorName: 'Jean Dupont',
        maxCapacity: 20,
        active: true,
      };

      const mockDetail: ActivityDetail = {
        id: 5,
        associationId: 1,
        associationName: 'Lyon Natation Metropole',
        name: 'Natation enfants 6-10 ans',
        description: 'Cours de natation pour enfants de 6 a 10 ans',
        category: 'Sport',
        level: 'BEGINNER',
        minAge: 6,
        maxAge: 10,
        maxCapacity: 20,
        priceCents: 15000,
        seasonStart: '2024-09-01',
        seasonEnd: '2025-06-30',
        status: 'ACTIVE',
        sessions: [mockSession],
        createdAt: '2024-01-15T10:00:00',
        updatedAt: '2024-06-01T14:30:00',
      };

      service.getActivityDetail(1, 5).subscribe((result) => {
        expect(result).toEqual(mockDetail);
        expect(result.id).toBe(5);
        expect(result.sessions.length).toBe(1);
      });

      const req = httpMock.expectOne(`${API_BASE}/1/activities/5`);
      expect(req.request.method).toBe('GET');
      req.flush(mockDetail);
    });

    it('should handle error response', () => {
      service.getActivityDetail(1, 999).subscribe({
        error: (error) => {
          expect(error.status).toBe(404);
        },
      });

      const req = httpMock.expectOne(`${API_BASE}/1/activities/999`);
      req.flush('Not Found', { status: 404, statusText: 'Not Found' });
    });

    it('should call correct URL for different association and activity IDs', () => {
      const mockDetail: ActivityDetail = {
        id: 10,
        associationId: 3,
        associationName: 'Ecole de Danse',
        name: 'Modern jazz ados',
        description: null,
        category: 'Danse',
        level: 'INTERMEDIATE',
        minAge: 12,
        maxAge: 18,
        maxCapacity: null,
        priceCents: 20000,
        seasonStart: null,
        seasonEnd: null,
        status: 'ACTIVE',
        sessions: [],
        createdAt: '2024-03-01T10:00:00',
        updatedAt: '2024-03-01T10:00:00',
      };

      service.getActivityDetail(3, 10).subscribe((result) => {
        expect(result.associationId).toBe(3);
        expect(result.name).toBe('Modern jazz ados');
      });

      const req = httpMock.expectOne(`${API_BASE}/3/activities/10`);
      expect(req.request.method).toBe('GET');
      req.flush(mockDetail);
    });
  });
});
