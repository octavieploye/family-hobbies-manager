// frontend/src/app/features/dashboard/services/dashboard.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { DashboardService } from './dashboard.service';
import { environment } from '../../../../environments/environment';
import { Family } from '../../family/models/family.model';
import { Subscription } from '@shared/models/subscription.model';
import { AttendanceSummary } from '@shared/models/attendance.model';

describe('DashboardService', () => {
  let service: DashboardService;
  let httpMock: HttpTestingController;
  const apiBase = environment.apiBaseUrl;

  const mockFamily: Family = {
    id: 1,
    name: 'Famille Dupont',
    createdBy: 1,
    members: [
      {
        id: 5,
        familyId: 1,
        firstName: 'Marie',
        lastName: 'Dupont',
        dateOfBirth: '2015-03-15',
        age: 9,
        relationship: 'CHILD',
        createdAt: '2024-01-01T10:00:00',
      },
    ],
    createdAt: '2024-01-01T10:00:00',
    updatedAt: '2024-01-01T10:00:00',
  };

  const mockSubscription: Subscription = {
    id: 1,
    activityId: 5,
    activityName: 'Natation enfants',
    associationName: 'Lyon Natation',
    familyMemberId: 5,
    memberFirstName: 'Marie',
    memberLastName: 'Dupont',
    familyId: 1,
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
    service = TestBed.inject(DashboardService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getFamily', () => {
    it('should send GET request to /families/me', () => {
      service.getFamily().subscribe((result) => {
        expect(result).toEqual(mockFamily);
        expect(result.name).toBe('Famille Dupont');
      });

      const req = httpMock.expectOne(`${apiBase}/families/me`);
      expect(req.request.method).toBe('GET');
      req.flush(mockFamily);
    });
  });

  describe('getFamilySubscriptions', () => {
    it('should send GET request to /subscriptions/family/{familyId}', () => {
      service.getFamilySubscriptions(1).subscribe((result) => {
        expect(result.length).toBe(1);
      });

      const req = httpMock.expectOne(`${apiBase}/subscriptions/family/1`);
      expect(req.request.method).toBe('GET');
      req.flush([mockSubscription]);
    });
  });

  describe('mapToSubscriptionSummaries', () => {
    it('should filter and map active subscriptions', () => {
      const result = service.mapToSubscriptionSummaries([
        mockSubscription,
        { ...mockSubscription, id: 2, status: 'CANCELLED' },
      ]);

      expect(result.length).toBe(1);
      expect(result[0].activityName).toBe('Natation enfants');
      expect(result[0].memberName).toBe('Marie Dupont');
    });
  });

  describe('mapToMemberAttendance', () => {
    it('should map attendance summaries to dashboard format', () => {
      const summaries: AttendanceSummary[] = [
        {
          familyMemberId: 5,
          memberFirstName: 'Marie',
          memberLastName: 'Dupont',
          totalSessions: 10,
          presentCount: 8,
          absentCount: 1,
          excusedCount: 1,
          lateCount: 0,
          attendanceRate: 80,
        },
      ];

      const result = service.mapToMemberAttendance(summaries);

      expect(result.length).toBe(1);
      expect(result[0].memberName).toBe('Marie Dupont');
      expect(result[0].attendanceRate).toBe(80);
    });
  });
});
