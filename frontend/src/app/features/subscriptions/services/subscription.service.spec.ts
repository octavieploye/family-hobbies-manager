// frontend/src/app/features/subscriptions/services/subscription.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { SubscriptionService } from './subscription.service';
import { Subscription, SubscriptionRequest } from '@shared/models/subscription.model';
import { environment } from '../../../../environments/environment';

describe('SubscriptionService', () => {
  let service: SubscriptionService;
  let httpMock: HttpTestingController;
  const API_BASE = `${environment.apiBaseUrl}/subscriptions`;

  const mockSubscription: Subscription = {
    id: 1,
    activityId: 5,
    activityName: 'Natation enfants',
    associationName: 'Lyon Natation Metropole',
    familyMemberId: 10,
    memberFirstName: 'Marie',
    memberLastName: 'Dupont',
    familyId: 3,
    userId: 1,
    subscriptionType: 'ADHESION',
    status: 'ACTIVE',
    startDate: '2024-09-01',
    endDate: null,
    cancellationReason: null,
    cancelledAt: null,
    createdAt: '2024-08-15T10:00:00',
    updatedAt: '2024-08-15T10:00:00',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(SubscriptionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('create', () => {
    it('should send POST request and return new subscription', () => {
      const request: SubscriptionRequest = {
        activityId: 5,
        familyMemberId: 10,
        familyId: 3,
        subscriptionType: 'ADHESION',
        startDate: '2024-09-01',
      };

      service.create(request).subscribe((result) => {
        expect(result).toEqual(mockSubscription);
        expect(result.id).toBe(1);
      });

      const req = httpMock.expectOne(API_BASE);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush(mockSubscription);
    });
  });

  describe('getByFamily', () => {
    it('should send GET request to /subscriptions/family/{familyId}', () => {
      const mockList: Subscription[] = [mockSubscription];

      service.getByFamily(3).subscribe((result) => {
        expect(result).toEqual(mockList);
        expect(result.length).toBe(1);
      });

      const req = httpMock.expectOne(`${API_BASE}/family/3`);
      expect(req.request.method).toBe('GET');
      req.flush(mockList);
    });
  });

  describe('getByMember', () => {
    it('should send GET request to /subscriptions/member/{memberId}', () => {
      const mockList: Subscription[] = [mockSubscription];

      service.getByMember(10).subscribe((result) => {
        expect(result).toEqual(mockList);
      });

      const req = httpMock.expectOne(`${API_BASE}/member/10`);
      expect(req.request.method).toBe('GET');
      req.flush(mockList);
    });
  });

  describe('getById', () => {
    it('should send GET request to /subscriptions/{id}', () => {
      service.getById(1).subscribe((result) => {
        expect(result).toEqual(mockSubscription);
        expect(result.activityName).toBe('Natation enfants');
      });

      const req = httpMock.expectOne(`${API_BASE}/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockSubscription);
    });
  });

  describe('cancel', () => {
    it('should send PUT request to /subscriptions/{id}/cancel', () => {
      const cancelledSubscription: Subscription = {
        ...mockSubscription,
        status: 'CANCELLED',
        cancellationReason: 'User requested',
        cancelledAt: '2024-10-01T10:00:00',
      };

      service.cancel(1).subscribe((result) => {
        expect(result.status).toBe('CANCELLED');
        expect(result.cancelledAt).toBeTruthy();
      });

      const req = httpMock.expectOne(`${API_BASE}/1/cancel`);
      expect(req.request.method).toBe('PUT');
      req.flush(cancelledSubscription);
    });
  });

  describe('error handling', () => {
    it('should handle 409 conflict on duplicate subscription', () => {
      const request: SubscriptionRequest = {
        activityId: 5,
        familyMemberId: 10,
        familyId: 3,
        subscriptionType: 'ADHESION',
        startDate: '2024-09-01',
      };

      service.create(request).subscribe({
        error: (error) => {
          expect(error.status).toBe(409);
        },
      });

      const req = httpMock.expectOne(API_BASE);
      req.flush('Conflict', { status: 409, statusText: 'Conflict' });
    });
  });
});
