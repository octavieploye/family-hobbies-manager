// frontend/src/app/features/settings/services/rgpd.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { RgpdService } from './rgpd.service';
import { ConsentStatus, UserDataExport } from '@shared/models/rgpd.model';
import { environment } from '../../../../environments/environment';

describe('RgpdService', () => {
  let service: RgpdService;
  let httpMock: HttpTestingController;
  const API_BASE = `${environment.apiBaseUrl}/rgpd`;

  const mockConsent: ConsentStatus = {
    id: 1,
    userId: 1,
    consentType: 'TERMS_OF_SERVICE',
    granted: true,
    version: '1.0',
    consentedAt: '2024-09-15T10:00:00',
  };

  const mockExport: UserDataExport = {
    userId: 1,
    email: 'test@example.com',
    firstName: 'Jean',
    lastName: 'Dupont',
    phone: null,
    role: 'FAMILY',
    status: 'ACTIVE',
    createdAt: '2024-01-01T10:00:00',
    lastLoginAt: '2024-09-15T10:00:00',
    family: null,
    consentHistory: [mockConsent],
    exportedAt: '2024-09-15T10:00:00',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(RgpdService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getConsentStatus', () => {
    it('should send GET request to /rgpd/consent', () => {
      service.getConsentStatus().subscribe((result) => {
        expect(result).toEqual([mockConsent]);
      });

      const req = httpMock.expectOne(`${API_BASE}/consent`);
      expect(req.request.method).toBe('GET');
      req.flush([mockConsent]);
    });
  });

  describe('recordConsent', () => {
    it('should send POST request to /rgpd/consent', () => {
      service.recordConsent({ consentType: 'MARKETING_EMAIL', granted: true }).subscribe((result) => {
        expect(result.granted).toBe(true);
      });

      const req = httpMock.expectOne(`${API_BASE}/consent`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ consentType: 'MARKETING_EMAIL', granted: true });
      req.flush({ ...mockConsent, consentType: 'MARKETING_EMAIL' });
    });
  });

  describe('exportData', () => {
    it('should send GET request to /rgpd/export', () => {
      service.exportData().subscribe((result) => {
        expect(result.email).toBe('test@example.com');
        expect(result.consentHistory.length).toBe(1);
      });

      const req = httpMock.expectOne(`${API_BASE}/export`);
      expect(req.request.method).toBe('GET');
      req.flush(mockExport);
    });
  });

  describe('deleteAccount', () => {
    it('should send DELETE request to /rgpd/account', () => {
      service.deleteAccount('password123', 'Plus besoin').subscribe();

      const req = httpMock.expectOne(`${API_BASE}/account`);
      expect(req.request.method).toBe('DELETE');
      expect(req.request.body).toEqual({ password: 'password123', reason: 'Plus besoin' });
      req.flush(null);
    });
  });
});
