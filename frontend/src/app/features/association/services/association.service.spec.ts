// frontend/src/app/features/association/services/association.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { AssociationService } from './association.service';
import {
  Association,
  AssociationDetail,
  AssociationSearchRequest,
  PageResponse,
} from '../models/association.model';
import { environment } from '../../../../environments/environment';

describe('AssociationService', () => {
  let service: AssociationService;
  let httpMock: HttpTestingController;
  const API_BASE = `${environment.apiBaseUrl}/associations`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(AssociationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('search', () => {
    it('should send GET request with query params and return paginated associations', () => {
      const request: AssociationSearchRequest = {
        city: 'Paris',
        category: 'Sport',
        keyword: 'football',
        page: 0,
        size: 20,
      };

      const mockResponse: PageResponse<Association> = {
        content: [
          {
            id: 1,
            name: 'Club Sportif Paris',
            slug: 'club-sportif-paris',
            city: 'Paris',
            postalCode: '75001',
            category: 'Sport',
            status: 'ACTIVE',
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 20,
      };

      service.search(request).subscribe((result) => {
        expect(result).toEqual(mockResponse);
        expect(result.content.length).toBe(1);
        expect(result.totalElements).toBe(1);
      });

      const req = httpMock.expectOne(
        (r) =>
          r.url === API_BASE &&
          r.params.get('city') === 'Paris' &&
          r.params.get('category') === 'Sport' &&
          r.params.get('keyword') === 'football' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '20'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should omit empty search params', () => {
      const request: AssociationSearchRequest = {
        page: 0,
        size: 10,
      };

      const mockResponse: PageResponse<Association> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: 10,
      };

      service.search(request).subscribe((result) => {
        expect(result.content.length).toBe(0);
      });

      const req = httpMock.expectOne(
        (r) =>
          r.url === API_BASE &&
          !r.params.has('city') &&
          !r.params.has('category') &&
          !r.params.has('keyword') &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '10'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });
  });

  describe('getById', () => {
    it('should send GET request to /associations/{id} and return detail', () => {
      const mockDetail: AssociationDetail = {
        id: 1,
        name: 'Club Sportif Paris',
        slug: 'club-sportif-paris',
        description: 'Un club sportif',
        city: 'Paris',
        postalCode: '75001',
        category: 'Sport',
        status: 'ACTIVE',
        createdAt: '2024-01-15T10:00:00',
        updatedAt: '2024-06-01T14:30:00',
      };

      service.getById(1).subscribe((result) => {
        expect(result).toEqual(mockDetail);
        expect(result.id).toBe(1);
        expect(result.name).toBe('Club Sportif Paris');
      });

      const req = httpMock.expectOne(`${API_BASE}/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockDetail);
    });
  });

  describe('getBySlug', () => {
    it('should send GET request to /associations/slug/{slug} and return detail', () => {
      const mockDetail: AssociationDetail = {
        id: 1,
        name: 'Club Sportif Paris',
        slug: 'club-sportif-paris',
        city: 'Paris',
        postalCode: '75001',
        category: 'Sport',
        status: 'ACTIVE',
        createdAt: '2024-01-15T10:00:00',
        updatedAt: '2024-06-01T14:30:00',
      };

      service.getBySlug('club-sportif-paris').subscribe((result) => {
        expect(result).toEqual(mockDetail);
        expect(result.slug).toBe('club-sportif-paris');
      });

      const req = httpMock.expectOne(`${API_BASE}/slug/club-sportif-paris`);
      expect(req.request.method).toBe('GET');
      req.flush(mockDetail);
    });
  });
});
