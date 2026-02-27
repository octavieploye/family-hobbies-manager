// frontend/src/app/features/notifications/services/notification.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@environments/environment';
import {
  NotificationPage,
  NotificationPreference,
  NotificationPreferenceRequest,
  UnreadCount,
} from '@shared/models/notification.model';

/**
 * Service for notification-related HTTP operations.
 *
 * All endpoints target the notification-service via the API gateway.
 * Authentication is handled by the JWT interceptor.
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/notifications`;

  /**
   * Load paginated notifications for the current user.
   */
  getNotifications(page: number, size: number): Observable<NotificationPage> {
    const params = new HttpParams()
      .set('page', page.toString())
      .set('size', size.toString());
    return this.http.get<NotificationPage>(this.baseUrl, { params });
  }

  /**
   * Get the count of unread notifications.
   */
  getUnreadCount(): Observable<UnreadCount> {
    return this.http.get<UnreadCount>(`${this.baseUrl}/unread-count`);
  }

  /**
   * Mark a single notification as read.
   */
  markAsRead(id: number): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/${id}/read`, {});
  }

  /**
   * Mark all notifications as read.
   */
  markAllAsRead(): Observable<void> {
    return this.http.patch<void>(`${this.baseUrl}/read-all`, {});
  }

  /**
   * Load notification preferences for the current user.
   */
  getPreferences(): Observable<NotificationPreference[]> {
    return this.http.get<NotificationPreference[]>(`${this.baseUrl}/preferences`);
  }

  /**
   * Update a notification preference for a given category.
   */
  updatePreference(request: NotificationPreferenceRequest): Observable<NotificationPreference> {
    return this.http.put<NotificationPreference>(`${this.baseUrl}/preferences`, request);
  }
}
