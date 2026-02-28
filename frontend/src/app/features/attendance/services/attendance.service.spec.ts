// frontend/src/app/features/attendance/services/attendance.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { AttendanceService } from './attendance.service';
import {
  Attendance,
  AttendanceSummary,
  BulkAttendanceRequest,
} from '@shared/models/attendance.model';
import { environment } from '../../../../environments/environment';

describe('AttendanceService', () => {
  let service: AttendanceService;
  let httpMock: HttpTestingController;
  const API_BASE = `${environment.apiBaseUrl}/attendance`;

  const mockAttendance: Attendance = {
    id: 1,
    sessionId: 10,
    familyMemberId: 5,
    memberFirstName: 'Marie',
    memberLastName: 'Dupont',
    subscriptionId: 3,
    sessionDate: '2024-09-15',
    status: 'PRESENT',
    note: null,
    markedBy: 1,
    createdAt: '2024-09-15T10:00:00',
    updatedAt: '2024-09-15T10:00:00',
  };

  const mockSummary: AttendanceSummary = {
    familyMemberId: 5,
    memberFirstName: 'Marie',
    memberLastName: 'Dupont',
    totalSessions: 10,
    presentCount: 8,
    absentCount: 1,
    excusedCount: 1,
    lateCount: 0,
    attendanceRate: 80,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(AttendanceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getBySession', () => {
    it('should send GET request to /attendance/session/{sessionId} with date param', () => {
      service.getBySession(10, '2024-09-15').subscribe((result) => {
        expect(result).toEqual([mockAttendance]);
        expect(result.length).toBe(1);
      });

      const req = httpMock.expectOne(`${API_BASE}/session/10?date=2024-09-15`);
      expect(req.request.method).toBe('GET');
      req.flush([mockAttendance]);
    });
  });

  describe('getByMember', () => {
    it('should send GET request to /attendance/member/{memberId}', () => {
      service.getByMember(5).subscribe((result) => {
        expect(result).toEqual([mockAttendance]);
      });

      const req = httpMock.expectOne(`${API_BASE}/member/5`);
      expect(req.request.method).toBe('GET');
      req.flush([mockAttendance]);
    });
  });

  describe('getMemberSummary', () => {
    it('should send GET request to /attendance/member/{memberId}/summary', () => {
      service.getMemberSummary(5).subscribe((result) => {
        expect(result).toEqual(mockSummary);
        expect(result.attendanceRate).toBe(80);
      });

      const req = httpMock.expectOne(`${API_BASE}/member/5/summary`);
      expect(req.request.method).toBe('GET');
      req.flush(mockSummary);
    });
  });

  describe('markBulk', () => {
    it('should send POST request to /attendance/bulk', () => {
      const request: BulkAttendanceRequest = {
        sessionId: 10,
        sessionDate: '2024-09-15',
        marks: [
          { familyMemberId: 5, subscriptionId: 3, status: 'PRESENT' },
          { familyMemberId: 6, subscriptionId: 4, status: 'ABSENT', note: 'Malade' },
        ],
      };

      service.markBulk(request).subscribe((result) => {
        expect(result.length).toBe(2);
      });

      const req = httpMock.expectOne(`${API_BASE}/bulk`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush([mockAttendance, { ...mockAttendance, id: 2, familyMemberId: 6, status: 'ABSENT' }]);
    });
  });

  describe('update', () => {
    it('should send PUT request to /attendance/{attendanceId}', () => {
      const updated: Attendance = { ...mockAttendance, status: 'EXCUSED', note: 'Certificat m\u00e9dical' };

      service.update(1, { status: 'EXCUSED', note: 'Certificat m\u00e9dical' }).subscribe((result) => {
        expect(result.status).toBe('EXCUSED');
        expect(result.note).toBe('Certificat m\u00e9dical');
      });

      const req = httpMock.expectOne(`${API_BASE}/1`);
      expect(req.request.method).toBe('PUT');
      req.flush(updated);
    });
  });

  describe('getBySubscription', () => {
    it('should send GET request to /attendance/subscription/{subscriptionId}', () => {
      service.getBySubscription(3).subscribe((result) => {
        expect(result).toEqual([mockAttendance]);
      });

      const req = httpMock.expectOne(`${API_BASE}/subscription/3`);
      expect(req.request.method).toBe('GET');
      req.flush([mockAttendance]);
    });
  });
});
