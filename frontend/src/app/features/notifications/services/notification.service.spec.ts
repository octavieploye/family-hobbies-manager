// frontend/src/app/features/notifications/services/notification.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { NotificationService } from './notification.service';
import {
  NotificationCategory,
  NotificationPage,
  NotificationPreference,
  NotificationPreferenceRequest,
  NotificationType,
  UnreadCount,
} from '@shared/models/notification.model';
import { environment } from '@environments/environment';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;
  const API_BASE = `${environment.apiBaseUrl}/notifications`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should call GET /notifications with page and size when loading notifications', () => {
    const mockPage: NotificationPage = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 10,
    };

    service.getNotifications(0, 10).subscribe((result) => {
      expect(result).toEqual(mockPage);
    });

    const req = httpMock.expectOne(
      (r) =>
        r.url === API_BASE &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '10'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should call GET /notifications/unread-count when loading unread count', () => {
    const mockCount: UnreadCount = { count: 5 };

    service.getUnreadCount().subscribe((result) => {
      expect(result).toEqual(mockCount);
      expect(result.count).toBe(5);
    });

    const req = httpMock.expectOne(`${API_BASE}/unread-count`);
    expect(req.request.method).toBe('GET');
    req.flush(mockCount);
  });

  it('should call PATCH /notifications/{id}/read when marking as read', () => {
    service.markAsRead(42).subscribe();

    const req = httpMock.expectOne(`${API_BASE}/42/read`);
    expect(req.request.method).toBe('PATCH');
    req.flush(null);
  });

  it('should call PATCH /notifications/read-all when marking all as read', () => {
    service.markAllAsRead().subscribe();

    const req = httpMock.expectOne(`${API_BASE}/read-all`);
    expect(req.request.method).toBe('PATCH');
    req.flush(null);
  });

  it('should call GET /notifications/preferences when loading preferences', () => {
    const mockPreferences: NotificationPreference[] = [
      {
        id: 1,
        userId: 1,
        category: NotificationCategory.PAYMENT,
        emailEnabled: true,
        inAppEnabled: true,
      },
    ];

    service.getPreferences().subscribe((result) => {
      expect(result).toEqual(mockPreferences);
      expect(result.length).toBe(1);
    });

    const req = httpMock.expectOne(`${API_BASE}/preferences`);
    expect(req.request.method).toBe('GET');
    req.flush(mockPreferences);
  });

  it('should call PUT /notifications/preferences when updating a preference', () => {
    const request: NotificationPreferenceRequest = {
      category: NotificationCategory.PAYMENT,
      emailEnabled: false,
      inAppEnabled: true,
    };

    const mockResponse: NotificationPreference = {
      id: 1,
      userId: 1,
      category: NotificationCategory.PAYMENT,
      emailEnabled: false,
      inAppEnabled: true,
    };

    service.updatePreference(request).subscribe((result) => {
      expect(result).toEqual(mockResponse);
      expect(result.emailEnabled).toBe(false);
    });

    const req = httpMock.expectOne(`${API_BASE}/preferences`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(mockResponse);
  });
});
