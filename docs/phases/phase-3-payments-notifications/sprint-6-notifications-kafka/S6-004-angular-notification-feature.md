# Story S6-004: Implement Angular Notification Feature

> 5 points | Priority: P0 | Service: frontend
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The notification system backend (S6-001 through S6-003) provides a full REST API for managing notifications: fetching paginated lists, tracking unread counts, marking notifications as read, and managing user preferences per category. This story builds the Angular frontend that consumes those endpoints. The user-facing feature includes a toolbar bell icon with a live unread badge, a dropdown for quick access to recent notifications, a full list page with filtering and pagination, and a preferences panel for toggling email/in-app delivery per category. Polling keeps the unread count fresh every 30 seconds. All UI text is in French to match the target audience.

## Cross-References

- **S6-003** (Notification API) defines the 6 backend endpoints consumed here
- **S6-001** (Notification Entities) defines `NotificationCategory` enum values
- **S6-002** (Kafka Consumers) populates notifications from upstream events
- **S6-005** (Seed Email Templates) seeds the email templates used by email notifications

---

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Create notification model | `frontend/src/app/shared/models/notification.model.ts` | TypeScript interfaces and enums for notification data | File compiles, types match API contract |
| 2 | Create NotificationService | `frontend/src/app/features/notifications/services/notification.service.ts` | HTTP service for all 6 notification endpoints | Jest test: service methods call correct URLs |
| 3 | Create NgRx state definition | `frontend/src/app/features/notifications/store/notification.state.ts` | State interface and initial state | File compiles |
| 4 | Create NgRx actions | `frontend/src/app/features/notifications/store/notification.actions.ts` | Action groups for all notification operations | File compiles |
| 5 | Create NgRx reducer | `frontend/src/app/features/notifications/store/notification.reducer.ts` | Reducer handling all notification actions | Jest test: state transitions correct |
| 6 | Create NgRx selectors | `frontend/src/app/features/notifications/store/notification.selectors.ts` | Feature selectors for all state slices | File compiles |
| 7 | Create NgRx effects | `frontend/src/app/features/notifications/store/notification.effects.ts` | Effects for API calls + polling | Jest test: effects dispatch correct actions |
| 8 | Create NotificationBellComponent | `frontend/src/app/features/notifications/components/notification-bell/` | Toolbar icon with unread badge | Jest test: badge renders count |
| 9 | Create NotificationDropdownComponent | `frontend/src/app/features/notifications/components/notification-dropdown/` | Material menu with recent notifications | Jest test: list renders items |
| 10 | Create NotificationListPageComponent | `frontend/src/app/features/notifications/pages/notification-list/` | Full page with table, filters, pagination | Jest test: table renders rows |
| 11 | Create NotificationPreferencesComponent | `frontend/src/app/features/notifications/components/notification-preferences/` | Toggle matrix for email/in-app per category | Jest test: toggles dispatch actions |
| 12 | Create notification routes | `frontend/src/app/features/notifications/notifications.routes.ts` | Route configuration for `/notifications` | Route navigates to list page |
| 13 | Register feature in app | App-level routing and toolbar integration | Bell component in toolbar, lazy-loaded routes | Bell visible in toolbar, route accessible |

---

## Task 1 Detail: Create Notification Model

- **What**: TypeScript interfaces and enums matching the notification API contract from S6-003
- **Where**: `frontend/src/app/shared/models/notification.model.ts`
- **Why**: Shared types used by service, store, and all components
- **Content**:

```typescript
// frontend/src/app/shared/models/notification.model.ts

export enum NotificationCategory {
  WELCOME = 'WELCOME',
  PAYMENT_SUCCESS = 'PAYMENT_SUCCESS',
  PAYMENT_FAILED = 'PAYMENT_FAILED',
  SUBSCRIPTION_CONFIRMED = 'SUBSCRIPTION_CONFIRMED',
  SUBSCRIPTION_CANCELLED = 'SUBSCRIPTION_CANCELLED',
  ATTENDANCE_REMINDER = 'ATTENDANCE_REMINDER',
  SYSTEM = 'SYSTEM'
}

export interface NotificationResponse {
  id: number;
  userId: number;
  category: NotificationCategory;
  title: string;
  message: string;
  actionUrl: string | null;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCountResponse {
  count: number;
}

export interface MarkReadResponse {
  id: number;
  read: boolean;
  readAt: string;
}

export interface MarkAllReadResponse {
  markedCount: number;
  readAt: string;
}

export interface CategoryPreference {
  emailEnabled: boolean;
  inAppEnabled: boolean;
}

export interface NotificationPreferenceResponse {
  userId: number;
  categories: Record<NotificationCategory, CategoryPreference>;
}

export interface NotificationPreferenceRequest {
  categories: Record<NotificationCategory, CategoryPreference>;
}

export interface NotificationQueryParams {
  read?: boolean;
  category?: NotificationCategory;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}

/**
 * Maps NotificationCategory to French labels and chip colors.
 */
export const NOTIFICATION_CATEGORY_CONFIG: Record<
  NotificationCategory,
  { label: string; color: string; cssClass: string }
> = {
  [NotificationCategory.WELCOME]: {
    label: 'Bienvenue',
    color: '#1565C0',
    cssClass: 'notification-chip--welcome'
  },
  [NotificationCategory.PAYMENT_SUCCESS]: {
    label: 'Paiement recu',
    color: '#2E7D32',
    cssClass: 'notification-chip--payment-success'
  },
  [NotificationCategory.PAYMENT_FAILED]: {
    label: 'Paiement echoue',
    color: '#C62828',
    cssClass: 'notification-chip--payment-failed'
  },
  [NotificationCategory.SUBSCRIPTION_CONFIRMED]: {
    label: 'Inscription confirmee',
    color: '#00695C',
    cssClass: 'notification-chip--subscription-confirmed'
  },
  [NotificationCategory.SUBSCRIPTION_CANCELLED]: {
    label: 'Inscription annulee',
    color: '#E65100',
    cssClass: 'notification-chip--subscription-cancelled'
  },
  [NotificationCategory.ATTENDANCE_REMINDER]: {
    label: 'Rappel de presence',
    color: '#6A1B9A',
    cssClass: 'notification-chip--attendance-reminder'
  },
  [NotificationCategory.SYSTEM]: {
    label: 'Systeme',
    color: '#616161',
    cssClass: 'notification-chip--system'
  }
};
```

- **Verify**: `npx tsc --noEmit` passes with no errors on this file

---

## Task 2 Detail: Create NotificationService

- **What**: Angular HTTP service consuming all 6 notification API endpoints from S6-003
- **Where**: `frontend/src/app/features/notifications/services/notification.service.ts`
- **Why**: Centralized API access layer for the NgRx effects and components
- **Content**:

```typescript
// frontend/src/app/features/notifications/services/notification.service.ts

import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  NotificationResponse,
  UnreadCountResponse,
  MarkReadResponse,
  MarkAllReadResponse,
  NotificationPreferenceResponse,
  NotificationPreferenceRequest,
  NotificationQueryParams,
  PageResponse
} from '../../../shared/models/notification.model';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/api/v1/notifications`;

  /**
   * Fetches paginated notifications for the current user.
   */
  getMyNotifications(
    params: NotificationQueryParams = {}
  ): Observable<PageResponse<NotificationResponse>> {
    let httpParams = new HttpParams();

    if (params.read !== undefined) {
      httpParams = httpParams.set('read', String(params.read));
    }
    if (params.category) {
      httpParams = httpParams.set('category', params.category);
    }
    if (params.from) {
      httpParams = httpParams.set('from', params.from);
    }
    if (params.to) {
      httpParams = httpParams.set('to', params.to);
    }
    if (params.page !== undefined) {
      httpParams = httpParams.set('page', String(params.page));
    }
    if (params.size !== undefined) {
      httpParams = httpParams.set('size', String(params.size));
    }
    if (params.sort) {
      httpParams = httpParams.set('sort', params.sort);
    }

    return this.http.get<PageResponse<NotificationResponse>>(
      `${this.baseUrl}/me`,
      { params: httpParams }
    );
  }

  /**
   * Fetches the unread notification count for the current user.
   */
  getUnreadCount(): Observable<UnreadCountResponse> {
    return this.http.get<UnreadCountResponse>(
      `${this.baseUrl}/me/unread-count`
    );
  }

  /**
   * Marks a single notification as read.
   */
  markAsRead(notificationId: number): Observable<MarkReadResponse> {
    return this.http.put<MarkReadResponse>(
      `${this.baseUrl}/${notificationId}/read`,
      {}
    );
  }

  /**
   * Marks all notifications as read for the current user.
   */
  markAllAsRead(): Observable<MarkAllReadResponse> {
    return this.http.put<MarkAllReadResponse>(
      `${this.baseUrl}/read-all`,
      {}
    );
  }

  /**
   * Fetches notification preferences for the current user.
   */
  getPreferences(): Observable<NotificationPreferenceResponse> {
    return this.http.get<NotificationPreferenceResponse>(
      `${this.baseUrl}/preferences`
    );
  }

  /**
   * Updates notification preferences for the current user.
   */
  updatePreferences(
    request: NotificationPreferenceRequest
  ): Observable<NotificationPreferenceResponse> {
    return this.http.put<NotificationPreferenceResponse>(
      `${this.baseUrl}/preferences`,
      request
    );
  }
}
```

- **Verify**: `npx jest --testPathPattern=notification.service.spec` passes

---

## Task 3 Detail: Create NgRx State Definition

- **What**: State interface defining the shape of the notification feature store
- **Where**: `frontend/src/app/features/notifications/store/notification.state.ts`
- **Why**: Single source of truth for state shape, used by reducer, selectors, and effects
- **Content**:

```typescript
// frontend/src/app/features/notifications/store/notification.state.ts

import {
  NotificationResponse,
  NotificationPreferenceResponse,
  NotificationCategory,
  PageResponse
} from '../../../shared/models/notification.model';

export const NOTIFICATION_FEATURE_KEY = 'notifications';

export interface NotificationState {
  notifications: NotificationResponse[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  unreadCount: number;
  selectedCategory: NotificationCategory | null;
  showReadOnly: boolean | null;
  preferences: NotificationPreferenceResponse | null;
  loading: boolean;
  loadingPreferences: boolean;
  error: string | null;
}

export const initialNotificationState: NotificationState = {
  notifications: [],
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
  pageSize: 20,
  unreadCount: 0,
  selectedCategory: null,
  showReadOnly: null,
  preferences: null,
  loading: false,
  loadingPreferences: false,
  error: null
};
```

- **Verify**: File compiles with `npx tsc --noEmit`

---

## Task 4 Detail: Create NgRx Actions

- **What**: Action group covering all notification operations
- **Where**: `frontend/src/app/features/notifications/store/notification.actions.ts`
- **Why**: Defines the full contract of events the notification feature can dispatch
- **Content**:

```typescript
// frontend/src/app/features/notifications/store/notification.actions.ts

import { createActionGroup, emptyProps, props } from '@ngrx/store';
import {
  NotificationResponse,
  NotificationCategory,
  NotificationPreferenceResponse,
  NotificationPreferenceRequest,
  PageResponse
} from '../../../shared/models/notification.model';

export const NotificationActions = createActionGroup({
  source: 'Notifications',
  events: {
    // Load notifications
    'Load Notifications': props<{
      page: number;
      size: number;
      category?: NotificationCategory | null;
      read?: boolean | null;
    }>(),
    'Load Notifications Success': props<{
      page: PageResponse<NotificationResponse>;
    }>(),
    'Load Notifications Failure': props<{ error: string }>(),

    // Unread count
    'Load Unread Count': emptyProps(),
    'Load Unread Count Success': props<{ count: number }>(),
    'Load Unread Count Failure': props<{ error: string }>(),

    // Start / stop polling
    'Start Polling': emptyProps(),
    'Stop Polling': emptyProps(),

    // Mark single as read
    'Mark As Read': props<{ notificationId: number }>(),
    'Mark As Read Success': props<{
      notificationId: number;
      readAt: string;
    }>(),
    'Mark As Read Failure': props<{ error: string }>(),

    // Mark all as read
    'Mark All As Read': emptyProps(),
    'Mark All As Read Success': props<{
      markedCount: number;
      readAt: string;
    }>(),
    'Mark All As Read Failure': props<{ error: string }>(),

    // Preferences
    'Load Preferences': emptyProps(),
    'Load Preferences Success': props<{
      preferences: NotificationPreferenceResponse;
    }>(),
    'Load Preferences Failure': props<{ error: string }>(),

    'Update Preferences': props<{
      request: NotificationPreferenceRequest;
    }>(),
    'Update Preferences Success': props<{
      preferences: NotificationPreferenceResponse;
    }>(),
    'Update Preferences Failure': props<{ error: string }>(),

    // Filter changes
    'Set Category Filter': props<{
      category: NotificationCategory | null;
    }>(),
    'Set Read Filter': props<{ read: boolean | null }>(),
  }
});
```

- **Verify**: File compiles with `npx tsc --noEmit`

---

## Task 5 Detail: Create NgRx Reducer

- **What**: Reducer handling all notification actions and state transitions
- **Where**: `frontend/src/app/features/notifications/store/notification.reducer.ts`
- **Why**: Pure state transitions for all notification operations
- **Content**:

```typescript
// frontend/src/app/features/notifications/store/notification.reducer.ts

import { createReducer, on } from '@ngrx/store';
import { NotificationActions } from './notification.actions';
import {
  NotificationState,
  initialNotificationState
} from './notification.state';

export const notificationReducer = createReducer(
  initialNotificationState,

  // Load notifications
  on(NotificationActions.loadNotifications, (state, { page, size, category, read }) => ({
    ...state,
    loading: true,
    error: null,
    currentPage: page,
    pageSize: size,
    selectedCategory: category !== undefined ? category : state.selectedCategory,
    showReadOnly: read !== undefined ? read : state.showReadOnly
  })),
  on(NotificationActions.loadNotificationsSuccess, (state, { page }) => ({
    ...state,
    loading: false,
    notifications: page.content,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
    currentPage: page.number,
    pageSize: page.size
  })),
  on(NotificationActions.loadNotificationsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error
  })),

  // Unread count
  on(NotificationActions.loadUnreadCount, (state) => ({
    ...state
  })),
  on(NotificationActions.loadUnreadCountSuccess, (state, { count }) => ({
    ...state,
    unreadCount: count
  })),
  on(NotificationActions.loadUnreadCountFailure, (state, { error }) => ({
    ...state,
    error
  })),

  // Mark as read
  on(NotificationActions.markAsRead, (state) => ({
    ...state
  })),
  on(NotificationActions.markAsReadSuccess, (state, { notificationId, readAt }) => ({
    ...state,
    notifications: state.notifications.map((n) =>
      n.id === notificationId ? { ...n, read: true, readAt } : n
    ),
    unreadCount: Math.max(0, state.unreadCount - 1)
  })),
  on(NotificationActions.markAsReadFailure, (state, { error }) => ({
    ...state,
    error
  })),

  // Mark all as read
  on(NotificationActions.markAllAsRead, (state) => ({
    ...state
  })),
  on(NotificationActions.markAllAsReadSuccess, (state, { readAt }) => ({
    ...state,
    notifications: state.notifications.map((n) => ({
      ...n,
      read: true,
      readAt: n.readAt || readAt
    })),
    unreadCount: 0
  })),
  on(NotificationActions.markAllAsReadFailure, (state, { error }) => ({
    ...state,
    error
  })),

  // Preferences
  on(NotificationActions.loadPreferences, (state) => ({
    ...state,
    loadingPreferences: true
  })),
  on(NotificationActions.loadPreferencesSuccess, (state, { preferences }) => ({
    ...state,
    loadingPreferences: false,
    preferences
  })),
  on(NotificationActions.loadPreferencesFailure, (state, { error }) => ({
    ...state,
    loadingPreferences: false,
    error
  })),

  on(NotificationActions.updatePreferences, (state) => ({
    ...state,
    loadingPreferences: true
  })),
  on(NotificationActions.updatePreferencesSuccess, (state, { preferences }) => ({
    ...state,
    loadingPreferences: false,
    preferences
  })),
  on(NotificationActions.updatePreferencesFailure, (state, { error }) => ({
    ...state,
    loadingPreferences: false,
    error
  })),

  // Filters
  on(NotificationActions.setCategoryFilter, (state, { category }) => ({
    ...state,
    selectedCategory: category
  })),
  on(NotificationActions.setReadFilter, (state, { read }) => ({
    ...state,
    showReadOnly: read
  }))
);
```

- **Verify**: `npx jest --testPathPattern=notification.reducer.spec` passes

---

## Task 6 Detail: Create NgRx Selectors

- **What**: Feature selectors for all notification state slices
- **Where**: `frontend/src/app/features/notifications/store/notification.selectors.ts`
- **Why**: Decouples components from state shape; enables memoized derived data
- **Content**:

```typescript
// frontend/src/app/features/notifications/store/notification.selectors.ts

import { createFeatureSelector, createSelector } from '@ngrx/store';
import {
  NOTIFICATION_FEATURE_KEY,
  NotificationState
} from './notification.state';

export const selectNotificationState =
  createFeatureSelector<NotificationState>(NOTIFICATION_FEATURE_KEY);

export const selectNotifications = createSelector(
  selectNotificationState,
  (state) => state.notifications
);

export const selectUnreadCount = createSelector(
  selectNotificationState,
  (state) => state.unreadCount
);

export const selectNotificationsLoading = createSelector(
  selectNotificationState,
  (state) => state.loading
);

export const selectNotificationsError = createSelector(
  selectNotificationState,
  (state) => state.error
);

export const selectTotalElements = createSelector(
  selectNotificationState,
  (state) => state.totalElements
);

export const selectTotalPages = createSelector(
  selectNotificationState,
  (state) => state.totalPages
);

export const selectCurrentPage = createSelector(
  selectNotificationState,
  (state) => state.currentPage
);

export const selectPageSize = createSelector(
  selectNotificationState,
  (state) => state.pageSize
);

export const selectSelectedCategory = createSelector(
  selectNotificationState,
  (state) => state.selectedCategory
);

export const selectShowReadOnly = createSelector(
  selectNotificationState,
  (state) => state.showReadOnly
);

export const selectPreferences = createSelector(
  selectNotificationState,
  (state) => state.preferences
);

export const selectLoadingPreferences = createSelector(
  selectNotificationState,
  (state) => state.loadingPreferences
);

export const selectHasUnread = createSelector(
  selectUnreadCount,
  (count) => count > 0
);

export const selectRecentNotifications = createSelector(
  selectNotifications,
  (notifications) => notifications.slice(0, 5)
);
```

- **Verify**: File compiles with `npx tsc --noEmit`

---

## Task 7 Detail: Create NgRx Effects

- **What**: Side effects for API calls and polling of unread count every 30 seconds
- **Where**: `frontend/src/app/features/notifications/store/notification.effects.ts`
- **Why**: Connects NgRx actions to the NotificationService API layer
- **Content**:

```typescript
// frontend/src/app/features/notifications/store/notification.effects.ts

import { Injectable, inject, DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import {
  switchMap,
  map,
  catchError,
  exhaustMap,
  interval,
  takeUntil,
  startWith
} from 'rxjs';
import { of } from 'rxjs';
import { NotificationActions } from './notification.actions';
import { NotificationService } from '../services/notification.service';
import { selectSelectedCategory, selectShowReadOnly } from './notification.selectors';

const POLLING_INTERVAL_MS = 30_000;

@Injectable()
export class NotificationEffects {
  private readonly actions$ = inject(Actions);
  private readonly notificationService = inject(NotificationService);
  private readonly store = inject(Store);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * Loads paginated notifications from the API.
   */
  loadNotifications$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.loadNotifications),
      switchMap(({ page, size, category, read }) =>
        this.notificationService
          .getMyNotifications({
            page,
            size,
            category: category ?? undefined,
            read: read ?? undefined,
            sort: 'createdAt,desc'
          })
          .pipe(
            map((pageResponse) =>
              NotificationActions.loadNotificationsSuccess({
                page: pageResponse
              })
            ),
            catchError((error) =>
              of(
                NotificationActions.loadNotificationsFailure({
                  error: error?.error?.message || 'Erreur lors du chargement des notifications'
                })
              )
            )
          )
      )
    )
  );

  /**
   * Loads the current unread count.
   */
  loadUnreadCount$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.loadUnreadCount),
      exhaustMap(() =>
        this.notificationService.getUnreadCount().pipe(
          map(({ count }) =>
            NotificationActions.loadUnreadCountSuccess({ count })
          ),
          catchError((error) =>
            of(
              NotificationActions.loadUnreadCountFailure({
                error: error?.error?.message || 'Erreur lors du chargement du compteur'
              })
            )
          )
        )
      )
    )
  );

  /**
   * Polls unread count every 30 seconds when polling is started.
   * Stops when StopPolling is dispatched or component is destroyed.
   */
  startPolling$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.startPolling),
      switchMap(() =>
        interval(POLLING_INTERVAL_MS).pipe(
          startWith(0),
          takeUntil(this.actions$.pipe(ofType(NotificationActions.stopPolling))),
          takeUntilDestroyed(this.destroyRef),
          map(() => NotificationActions.loadUnreadCount())
        )
      )
    )
  );

  /**
   * Marks a single notification as read.
   */
  markAsRead$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.markAsRead),
      exhaustMap(({ notificationId }) =>
        this.notificationService.markAsRead(notificationId).pipe(
          map((response) =>
            NotificationActions.markAsReadSuccess({
              notificationId: response.id,
              readAt: response.readAt
            })
          ),
          catchError((error) =>
            of(
              NotificationActions.markAsReadFailure({
                error: error?.error?.message || 'Erreur lors du marquage comme lu'
              })
            )
          )
        )
      )
    )
  );

  /**
   * Marks all notifications as read.
   */
  markAllAsRead$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.markAllAsRead),
      exhaustMap(() =>
        this.notificationService.markAllAsRead().pipe(
          map((response) =>
            NotificationActions.markAllAsReadSuccess({
              markedCount: response.markedCount,
              readAt: response.readAt
            })
          ),
          catchError((error) =>
            of(
              NotificationActions.markAllAsReadFailure({
                error: error?.error?.message || 'Erreur lors du marquage global'
              })
            )
          )
        )
      )
    )
  );

  /**
   * Loads notification preferences.
   */
  loadPreferences$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.loadPreferences),
      exhaustMap(() =>
        this.notificationService.getPreferences().pipe(
          map((preferences) =>
            NotificationActions.loadPreferencesSuccess({ preferences })
          ),
          catchError((error) =>
            of(
              NotificationActions.loadPreferencesFailure({
                error: error?.error?.message || 'Erreur lors du chargement des preferences'
              })
            )
          )
        )
      )
    )
  );

  /**
   * Updates notification preferences.
   */
  updatePreferences$ = createEffect(() =>
    this.actions$.pipe(
      ofType(NotificationActions.updatePreferences),
      exhaustMap(({ request }) =>
        this.notificationService.updatePreferences(request).pipe(
          map((preferences) =>
            NotificationActions.updatePreferencesSuccess({ preferences })
          ),
          catchError((error) =>
            of(
              NotificationActions.updatePreferencesFailure({
                error: error?.error?.message || 'Erreur lors de la mise a jour des preferences'
              })
            )
          )
        )
      )
    )
  );
}
```

- **Verify**: `npx jest --testPathPattern=notification.effects.spec` passes

---

## Task 8 Detail: Create NotificationBellComponent

- **What**: Toolbar bell icon with Material badge showing unread count; click opens dropdown
- **Where**: `frontend/src/app/features/notifications/components/notification-bell/`
- **Why**: Always-visible entry point for the notification system
- **Content**:

**notification-bell.component.ts**:
```typescript
// frontend/src/app/features/notifications/components/notification-bell/notification-bell.component.ts

import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule, MatMenuTrigger } from '@angular/material/menu';
import { NotificationActions } from '../../store/notification.actions';
import {
  selectUnreadCount,
  selectHasUnread
} from '../../store/notification.selectors';
import { NotificationDropdownComponent } from '../notification-dropdown/notification-dropdown.component';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [
    CommonModule,
    MatIconModule,
    MatBadgeModule,
    MatButtonModule,
    MatMenuModule,
    NotificationDropdownComponent
  ],
  templateUrl: './notification-bell.component.html',
  styleUrls: ['./notification-bell.component.scss']
})
export class NotificationBellComponent implements OnInit {
  private readonly store = inject(Store);

  readonly unreadCount$ = this.store.select(selectUnreadCount);
  readonly hasUnread$ = this.store.select(selectHasUnread);

  ngOnInit(): void {
    this.store.dispatch(NotificationActions.startPolling());
  }

  onMenuOpened(): void {
    this.store.dispatch(
      NotificationActions.loadNotifications({
        page: 0,
        size: 5
      })
    );
  }
}
```

**notification-bell.component.html**:
```html
<!-- frontend/src/app/features/notifications/components/notification-bell/notification-bell.component.html -->

<button
  mat-icon-button
  [matMenuTriggerFor]="notificationMenu"
  (menuOpened)="onMenuOpened()"
  aria-label="Notifications"
  class="notification-bell">
  <mat-icon
    [matBadge]="(unreadCount$ | async) || ''"
    [matBadgeHidden]="!(hasUnread$ | async)"
    matBadgeColor="warn"
    matBadgeSize="small"
    matBadgePosition="above after">
    notifications
  </mat-icon>
</button>

<mat-menu #notificationMenu="matMenu" class="notification-bell__menu">
  <app-notification-dropdown />
</mat-menu>
```

**notification-bell.component.scss**:
```scss
// frontend/src/app/features/notifications/components/notification-bell/notification-bell.component.scss

.notification-bell {
  position: relative;

  &__menu {
    min-width: 360px;
    max-width: 400px;
  }
}

::ng-deep .notification-bell__menu {
  .mat-mdc-menu-content {
    padding: 0;
  }
}
```

- **Verify**: `npx jest --testPathPattern=notification-bell` passes; bell icon visible in toolbar

---

## Task 9 Detail: Create NotificationDropdownComponent

- **What**: Material menu content showing recent notifications, "Mark all read" button, "View all" link
- **Where**: `frontend/src/app/features/notifications/components/notification-dropdown/`
- **Why**: Quick-access panel for recent notifications without navigating away
- **Content**:

**notification-dropdown.component.ts**:
```typescript
// frontend/src/app/features/notifications/components/notification-dropdown/notification-dropdown.component.ts

import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { NotificationActions } from '../../store/notification.actions';
import {
  selectRecentNotifications,
  selectNotificationsLoading,
  selectHasUnread
} from '../../store/notification.selectors';
import {
  NotificationResponse,
  NOTIFICATION_CATEGORY_CONFIG
} from '../../../../shared/models/notification.model';

@Component({
  selector: 'app-notification-dropdown',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDividerModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './notification-dropdown.component.html',
  styleUrls: ['./notification-dropdown.component.scss']
})
export class NotificationDropdownComponent {
  private readonly store = inject(Store);

  readonly recentNotifications$ = this.store.select(selectRecentNotifications);
  readonly loading$ = this.store.select(selectNotificationsLoading);
  readonly hasUnread$ = this.store.select(selectHasUnread);
  readonly categoryConfig = NOTIFICATION_CATEGORY_CONFIG;

  onMarkAllRead(event: MouseEvent): void {
    event.stopPropagation();
    this.store.dispatch(NotificationActions.markAllAsRead());
  }

  onMarkAsRead(notification: NotificationResponse, event: MouseEvent): void {
    event.stopPropagation();
    if (!notification.read) {
      this.store.dispatch(
        NotificationActions.markAsRead({ notificationId: notification.id })
      );
    }
  }

  getCategoryClass(notification: NotificationResponse): string {
    return this.categoryConfig[notification.category]?.cssClass || '';
  }

  getCategoryLabel(notification: NotificationResponse): string {
    return this.categoryConfig[notification.category]?.label || notification.category;
  }
}
```

**notification-dropdown.component.html**:
```html
<!-- frontend/src/app/features/notifications/components/notification-dropdown/notification-dropdown.component.html -->

<div class="notification-dropdown" (click)="$event.stopPropagation()">
  <div class="notification-dropdown__header">
    <h3 class="notification-dropdown__title">Notifications</h3>
    @if (hasUnread$ | async) {
      <button
        mat-button
        color="primary"
        class="notification-dropdown__mark-all"
        (click)="onMarkAllRead($event)">
        Tout marquer comme lu
      </button>
    }
  </div>

  <mat-divider />

  @if (loading$ | async) {
    <div class="notification-dropdown__loading">
      <mat-spinner diameter="32" />
    </div>
  } @else {
    @let notifications = recentNotifications$ | async;
    @if (notifications && notifications.length > 0) {
      <mat-nav-list class="notification-dropdown__list">
        @for (notification of notifications; track notification.id) {
          <a
            mat-list-item
            class="notification-dropdown__item"
            [class.notification-dropdown__item--unread]="!notification.read"
            [routerLink]="notification.actionUrl"
            (click)="onMarkAsRead(notification, $event)">
            <div class="notification-dropdown__item-content">
              <div class="notification-dropdown__item-header">
                <mat-chip
                  [class]="getCategoryClass(notification)"
                  class="notification-dropdown__chip">
                  {{ getCategoryLabel(notification) }}
                </mat-chip>
                @if (!notification.read) {
                  <span class="notification-dropdown__unread-dot"></span>
                }
              </div>
              <span class="notification-dropdown__item-title">
                {{ notification.title }}
              </span>
              <span class="notification-dropdown__item-message">
                {{ notification.message }}
              </span>
              <span class="notification-dropdown__item-time">
                {{ notification.createdAt | date:'dd/MM/yyyy HH:mm' }}
              </span>
            </div>
          </a>
        }
      </mat-nav-list>
    } @else {
      <div class="notification-dropdown__empty">
        <mat-icon>notifications_none</mat-icon>
        <span>Aucune notification</span>
      </div>
    }
  }

  <mat-divider />

  <div class="notification-dropdown__footer">
    <a mat-button color="primary" routerLink="/notifications">
      Voir tout
    </a>
  </div>
</div>
```

**notification-dropdown.component.scss**:
```scss
// frontend/src/app/features/notifications/components/notification-dropdown/notification-dropdown.component.scss

.notification-dropdown {
  min-width: 360px;
  max-height: 480px;
  overflow-y: auto;

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 12px 16px 8px;
  }

  &__title {
    margin: 0;
    font-size: 16px;
    font-weight: 500;
  }

  &__mark-all {
    font-size: 12px;
  }

  &__loading {
    display: flex;
    justify-content: center;
    padding: 24px;
  }

  &__list {
    padding: 0;
  }

  &__item {
    border-left: 3px solid transparent;

    &--unread {
      background-color: rgba(25, 118, 210, 0.04);
      border-left-color: #1976d2;
    }
  }

  &__item-content {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 4px 0;
    width: 100%;
  }

  &__item-header {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  &__chip {
    font-size: 10px;
    min-height: 20px;
    padding: 0 8px;
  }

  &__unread-dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background-color: #1976d2;
    flex-shrink: 0;
  }

  &__item-title {
    font-size: 14px;
    font-weight: 500;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__item-message {
    font-size: 12px;
    color: rgba(0, 0, 0, 0.6);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  &__item-time {
    font-size: 11px;
    color: rgba(0, 0, 0, 0.4);
  }

  &__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 32px;
    color: rgba(0, 0, 0, 0.4);
  }

  &__footer {
    display: flex;
    justify-content: center;
    padding: 8px;
  }
}

// Category chip colors (BEM modifiers)
.notification-chip {
  &--welcome {
    background-color: #e3f2fd !important;
    color: #1565c0 !important;
  }

  &--payment-success {
    background-color: #e8f5e9 !important;
    color: #2e7d32 !important;
  }

  &--payment-failed {
    background-color: #ffebee !important;
    color: #c62828 !important;
  }

  &--subscription-confirmed {
    background-color: #e0f2f1 !important;
    color: #00695c !important;
  }

  &--subscription-cancelled {
    background-color: #fff3e0 !important;
    color: #e65100 !important;
  }

  &--attendance-reminder {
    background-color: #f3e5f5 !important;
    color: #6a1b9a !important;
  }

  &--system {
    background-color: #f5f5f5 !important;
    color: #616161 !important;
  }
}
```

- **Verify**: `npx jest --testPathPattern=notification-dropdown` passes

---

## Task 10 Detail: Create NotificationListPageComponent

- **What**: Full page with Material table listing all notifications, category filter, read/unread filter, pagination
- **Where**: `frontend/src/app/features/notifications/pages/notification-list/`
- **Why**: Complete notification management view with filtering and pagination
- **Content**:

**notification-list.component.ts**:
```typescript
// frontend/src/app/features/notifications/pages/notification-list/notification-list.component.ts

import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { NotificationActions } from '../../store/notification.actions';
import {
  selectNotifications,
  selectNotificationsLoading,
  selectTotalElements,
  selectCurrentPage,
  selectPageSize,
  selectSelectedCategory,
  selectShowReadOnly,
  selectHasUnread
} from '../../store/notification.selectors';
import {
  NotificationCategory,
  NotificationResponse,
  NOTIFICATION_CATEGORY_CONFIG
} from '../../../../shared/models/notification.model';

@Component({
  selector: 'app-notification-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
    MatTooltipModule
  ],
  templateUrl: './notification-list.component.html',
  styleUrls: ['./notification-list.component.scss']
})
export class NotificationListComponent implements OnInit {
  private readonly store = inject(Store);

  readonly notifications$ = this.store.select(selectNotifications);
  readonly loading$ = this.store.select(selectNotificationsLoading);
  readonly totalElements$ = this.store.select(selectTotalElements);
  readonly currentPage$ = this.store.select(selectCurrentPage);
  readonly pageSize$ = this.store.select(selectPageSize);
  readonly selectedCategory$ = this.store.select(selectSelectedCategory);
  readonly showReadOnly$ = this.store.select(selectShowReadOnly);
  readonly hasUnread$ = this.store.select(selectHasUnread);

  readonly displayedColumns = ['status', 'category', 'title', 'message', 'date', 'actions'];
  readonly categories = Object.values(NotificationCategory);
  readonly categoryConfig = NOTIFICATION_CATEGORY_CONFIG;

  readonly readFilterOptions = [
    { value: null, label: 'Toutes' },
    { value: false, label: 'Non lues' },
    { value: true, label: 'Lues' }
  ];

  ngOnInit(): void {
    this.store.dispatch(
      NotificationActions.loadNotifications({ page: 0, size: 20 })
    );
  }

  onPageChange(event: PageEvent): void {
    this.store.dispatch(
      NotificationActions.loadNotifications({
        page: event.pageIndex,
        size: event.pageSize
      })
    );
  }

  onCategoryChange(category: NotificationCategory | null): void {
    this.store.dispatch(
      NotificationActions.setCategoryFilter({ category })
    );
    this.store.dispatch(
      NotificationActions.loadNotifications({
        page: 0,
        size: 20,
        category
      })
    );
  }

  onReadFilterChange(read: boolean | null): void {
    this.store.dispatch(NotificationActions.setReadFilter({ read }));
    this.store.dispatch(
      NotificationActions.loadNotifications({
        page: 0,
        size: 20,
        read
      })
    );
  }

  onMarkAsRead(notification: NotificationResponse): void {
    if (!notification.read) {
      this.store.dispatch(
        NotificationActions.markAsRead({ notificationId: notification.id })
      );
    }
  }

  onMarkAllAsRead(): void {
    this.store.dispatch(NotificationActions.markAllAsRead());
  }

  getCategoryClass(notification: NotificationResponse): string {
    return this.categoryConfig[notification.category]?.cssClass || '';
  }

  getCategoryLabel(notification: NotificationResponse): string {
    return this.categoryConfig[notification.category]?.label || notification.category;
  }
}
```

**notification-list.component.html**:
```html
<!-- frontend/src/app/features/notifications/pages/notification-list/notification-list.component.html -->

<div class="notification-list">
  <div class="notification-list__header">
    <h1 class="notification-list__title">Notifications</h1>
    <div class="notification-list__actions">
      @if (hasUnread$ | async) {
        <button
          mat-stroked-button
          color="primary"
          (click)="onMarkAllAsRead()">
          <mat-icon>done_all</mat-icon>
          Tout marquer comme lu
        </button>
      }
    </div>
  </div>

  <div class="notification-list__filters">
    <mat-form-field appearance="outline" class="notification-list__filter">
      <mat-label>Categorie</mat-label>
      <mat-select
        [value]="selectedCategory$ | async"
        (selectionChange)="onCategoryChange($event.value)">
        <mat-option [value]="null">Toutes les categories</mat-option>
        @for (cat of categories; track cat) {
          <mat-option [value]="cat">
            {{ categoryConfig[cat].label }}
          </mat-option>
        }
      </mat-select>
    </mat-form-field>

    <mat-form-field appearance="outline" class="notification-list__filter">
      <mat-label>Statut</mat-label>
      <mat-select
        [value]="showReadOnly$ | async"
        (selectionChange)="onReadFilterChange($event.value)">
        @for (option of readFilterOptions; track option.value) {
          <mat-option [value]="option.value">
            {{ option.label }}
          </mat-option>
        }
      </mat-select>
    </mat-form-field>
  </div>

  @if (loading$ | async) {
    <div class="notification-list__loading">
      <mat-spinner diameter="48" />
    </div>
  } @else {
    @let notifs = notifications$ | async;
    @if (notifs && notifs.length > 0) {
      <table mat-table [dataSource]="notifs" class="notification-list__table">

        <!-- Status Column -->
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let notification">
            @if (!notification.read) {
              <span class="notification-list__unread-dot" matTooltip="Non lue"></span>
            }
          </td>
        </ng-container>

        <!-- Category Column -->
        <ng-container matColumnDef="category">
          <th mat-header-cell *matHeaderCellDef>Categorie</th>
          <td mat-cell *matCellDef="let notification">
            <mat-chip [class]="getCategoryClass(notification)">
              {{ getCategoryLabel(notification) }}
            </mat-chip>
          </td>
        </ng-container>

        <!-- Title Column -->
        <ng-container matColumnDef="title">
          <th mat-header-cell *matHeaderCellDef>Titre</th>
          <td mat-cell *matCellDef="let notification">
            <span [class.notification-list__text--bold]="!notification.read">
              {{ notification.title }}
            </span>
          </td>
        </ng-container>

        <!-- Message Column -->
        <ng-container matColumnDef="message">
          <th mat-header-cell *matHeaderCellDef>Message</th>
          <td mat-cell *matCellDef="let notification">
            {{ notification.message }}
          </td>
        </ng-container>

        <!-- Date Column -->
        <ng-container matColumnDef="date">
          <th mat-header-cell *matHeaderCellDef>Date</th>
          <td mat-cell *matCellDef="let notification">
            {{ notification.createdAt | date:'dd/MM/yyyy HH:mm' }}
          </td>
        </ng-container>

        <!-- Actions Column -->
        <ng-container matColumnDef="actions">
          <th mat-header-cell *matHeaderCellDef></th>
          <td mat-cell *matCellDef="let notification">
            @if (!notification.read) {
              <button
                mat-icon-button
                matTooltip="Marquer comme lue"
                (click)="onMarkAsRead(notification)">
                <mat-icon>check_circle_outline</mat-icon>
              </button>
            }
            @if (notification.actionUrl) {
              <a
                mat-icon-button
                [routerLink]="notification.actionUrl"
                matTooltip="Voir">
                <mat-icon>open_in_new</mat-icon>
              </a>
            }
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr
          mat-row
          *matRowDef="let row; columns: displayedColumns"
          [class.notification-list__row--unread]="!row.read">
        </tr>
      </table>

      <mat-paginator
        [length]="totalElements$ | async"
        [pageIndex]="(currentPage$ | async) ?? 0"
        [pageSize]="(pageSize$ | async) ?? 20"
        [pageSizeOptions]="[10, 20, 50]"
        (page)="onPageChange($event)"
        showFirstLastButtons>
      </mat-paginator>
    } @else {
      <div class="notification-list__empty">
        <mat-icon>notifications_none</mat-icon>
        <p>Aucune notification</p>
      </div>
    }
  }
</div>
```

**notification-list.component.scss**:
```scss
// frontend/src/app/features/notifications/pages/notification-list/notification-list.component.scss

.notification-list {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 24px;
  }

  &__title {
    margin: 0;
    font-size: 24px;
    font-weight: 500;
  }

  &__filters {
    display: flex;
    gap: 16px;
    margin-bottom: 16px;
  }

  &__filter {
    min-width: 200px;
  }

  &__loading {
    display: flex;
    justify-content: center;
    padding: 48px;
  }

  &__table {
    width: 100%;
  }

  &__unread-dot {
    display: inline-block;
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background-color: #1976d2;
  }

  &__text--bold {
    font-weight: 600;
  }

  &__row--unread {
    background-color: rgba(25, 118, 210, 0.04);
  }

  &__empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 16px;
    padding: 64px;
    color: rgba(0, 0, 0, 0.4);

    mat-icon {
      font-size: 48px;
      width: 48px;
      height: 48px;
    }
  }
}
```

- **Verify**: `npx jest --testPathPattern=notification-list` passes

---

## Task 11 Detail: Create NotificationPreferencesComponent

- **What**: Toggle matrix embedded in settings page — rows are notification categories, columns are email/in-app
- **Where**: `frontend/src/app/features/notifications/components/notification-preferences/`
- **Why**: Lets users control which notifications they receive and how
- **Content**:

**notification-preferences.component.ts**:
```typescript
// frontend/src/app/features/notifications/components/notification-preferences/notification-preferences.component.ts

import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Store } from '@ngrx/store';
import { MatSlideToggleModule, MatSlideToggleChange } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatCardModule } from '@angular/material/card';
import { NotificationActions } from '../../store/notification.actions';
import {
  selectPreferences,
  selectLoadingPreferences
} from '../../store/notification.selectors';
import {
  NotificationCategory,
  NotificationPreferenceResponse,
  CategoryPreference,
  NOTIFICATION_CATEGORY_CONFIG
} from '../../../../shared/models/notification.model';

interface PreferenceRow {
  category: NotificationCategory;
  label: string;
  emailEnabled: boolean;
  inAppEnabled: boolean;
}

@Component({
  selector: 'app-notification-preferences',
  standalone: true,
  imports: [
    CommonModule,
    MatSlideToggleModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatCardModule
  ],
  templateUrl: './notification-preferences.component.html',
  styleUrls: ['./notification-preferences.component.scss']
})
export class NotificationPreferencesComponent implements OnInit {
  private readonly store = inject(Store);

  readonly preferences$ = this.store.select(selectPreferences);
  readonly loading$ = this.store.select(selectLoadingPreferences);
  readonly displayedColumns = ['category', 'email', 'inApp'];
  readonly categoryConfig = NOTIFICATION_CATEGORY_CONFIG;

  preferenceRows: PreferenceRow[] = [];

  ngOnInit(): void {
    this.store.dispatch(NotificationActions.loadPreferences());

    this.preferences$.subscribe((prefs) => {
      if (prefs) {
        this.preferenceRows = this.buildRows(prefs);
      }
    });
  }

  onToggle(
    row: PreferenceRow,
    channel: 'email' | 'inApp',
    event: MatSlideToggleChange
  ): void {
    const updatedRow = {
      ...row,
      emailEnabled: channel === 'email' ? event.checked : row.emailEnabled,
      inAppEnabled: channel === 'inApp' ? event.checked : row.inAppEnabled
    };

    // Update local row immediately for responsive UI
    const index = this.preferenceRows.findIndex(
      (r) => r.category === row.category
    );
    if (index >= 0) {
      this.preferenceRows = [
        ...this.preferenceRows.slice(0, index),
        updatedRow,
        ...this.preferenceRows.slice(index + 1)
      ];
    }

    // Build full request from current rows
    const categories: Record<string, CategoryPreference> = {};
    for (const r of this.preferenceRows) {
      categories[r.category] = {
        emailEnabled: r.emailEnabled,
        inAppEnabled: r.inAppEnabled
      };
    }

    this.store.dispatch(
      NotificationActions.updatePreferences({
        request: { categories: categories as Record<NotificationCategory, CategoryPreference> }
      })
    );
  }

  private buildRows(prefs: NotificationPreferenceResponse): PreferenceRow[] {
    return Object.values(NotificationCategory).map((category) => ({
      category,
      label: this.categoryConfig[category]?.label || category,
      emailEnabled: prefs.categories[category]?.emailEnabled ?? true,
      inAppEnabled: prefs.categories[category]?.inAppEnabled ?? true
    }));
  }
}
```

**notification-preferences.component.html**:
```html
<!-- frontend/src/app/features/notifications/components/notification-preferences/notification-preferences.component.html -->

<mat-card class="notification-preferences">
  <mat-card-header>
    <mat-card-title>Preferences de notification</mat-card-title>
    <mat-card-subtitle>
      Configurez vos preferences de notification par categorie
    </mat-card-subtitle>
  </mat-card-header>

  <mat-card-content>
    @if (loading$ | async) {
      <div class="notification-preferences__loading">
        <mat-spinner diameter="32" />
      </div>
    } @else {
      <table
        mat-table
        [dataSource]="preferenceRows"
        class="notification-preferences__table">

        <!-- Category Column -->
        <ng-container matColumnDef="category">
          <th mat-header-cell *matHeaderCellDef>Categorie</th>
          <td mat-cell *matCellDef="let row">{{ row.label }}</td>
        </ng-container>

        <!-- Email Column -->
        <ng-container matColumnDef="email">
          <th mat-header-cell *matHeaderCellDef>Email</th>
          <td mat-cell *matCellDef="let row">
            <mat-slide-toggle
              [checked]="row.emailEnabled"
              (change)="onToggle(row, 'email', $event)"
              [attr.aria-label]="'Email pour ' + row.label">
            </mat-slide-toggle>
          </td>
        </ng-container>

        <!-- In-App Column -->
        <ng-container matColumnDef="inApp">
          <th mat-header-cell *matHeaderCellDef>In-app</th>
          <td mat-cell *matCellDef="let row">
            <mat-slide-toggle
              [checked]="row.inAppEnabled"
              (change)="onToggle(row, 'inApp', $event)"
              [attr.aria-label]="'In-app pour ' + row.label">
            </mat-slide-toggle>
          </td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      </table>
    }
  </mat-card-content>
</mat-card>
```

**notification-preferences.component.scss**:
```scss
// frontend/src/app/features/notifications/components/notification-preferences/notification-preferences.component.scss

.notification-preferences {
  max-width: 600px;

  &__loading {
    display: flex;
    justify-content: center;
    padding: 24px;
  }

  &__table {
    width: 100%;
  }
}
```

- **Verify**: `npx jest --testPathPattern=notification-preferences` passes

---

## Task 12 Detail: Create Notification Routes

- **What**: Route configuration for the notifications feature module
- **Where**: `frontend/src/app/features/notifications/notifications.routes.ts`
- **Why**: Enables lazy-loaded routing to `/notifications`
- **Content**:

```typescript
// frontend/src/app/features/notifications/notifications.routes.ts

import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { NOTIFICATION_FEATURE_KEY } from './store/notification.state';
import { notificationReducer } from './store/notification.reducer';
import { NotificationEffects } from './store/notification.effects';

export const NOTIFICATION_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState(NOTIFICATION_FEATURE_KEY, notificationReducer),
      provideEffects(NotificationEffects)
    ],
    children: [
      {
        path: '',
        loadComponent: () =>
          import(
            './pages/notification-list/notification-list.component'
          ).then((m) => m.NotificationListComponent),
        title: 'Notifications'
      }
    ]
  }
];
```

- **Verify**: Navigate to `/notifications` in the browser; list page renders

---

## Task 13 Detail: Register Feature in App

- **What**: Integration of notification routes into the app router and bell component into the toolbar
- **Where**: `frontend/src/app/app.routes.ts` (routes) and app toolbar component (bell)
- **Why**: Connects the notification feature to the application shell
- **Content**:

**Add to app.routes.ts**:
```typescript
// Add this route to the existing app.routes.ts routes array:
{
  path: 'notifications',
  loadChildren: () =>
    import('./features/notifications/notifications.routes').then(
      (m) => m.NOTIFICATION_ROUTES
    )
}
```

**Add to toolbar template** (e.g., `app.component.html` or `toolbar.component.html`):
```html
<!-- Add inside the toolbar, before the user menu -->
<app-notification-bell />
```

**Add import to toolbar component**:
```typescript
// Add to the toolbar/app component imports array:
import { NotificationBellComponent } from './features/notifications/components/notification-bell/notification-bell.component';

// In @Component imports:
imports: [
  // ... existing imports
  NotificationBellComponent
]
```

- **Verify**: Bell icon visible in toolbar. Clicking navigates to `/notifications`.

---

## Failing Tests (TDD Contract)

Tests are defined in the companion file: [S6-004-angular-notification-feature-tests.md](./S6-004-angular-notification-feature-tests.md)

The companion file contains the complete Jest test source code for:
1. `notification.service.spec.ts` -- HTTP service tests (6 tests)
2. `notification.reducer.spec.ts` -- Reducer state transition tests (12 tests)
3. `notification-bell.component.spec.ts` -- Bell badge rendering tests (4 tests)
4. `notification-dropdown.component.spec.ts` -- Dropdown list and actions tests (5 tests)
5. `notification-list.component.spec.ts` -- List page table and filter tests (5 tests)
6. `notification-preferences.component.spec.ts` -- Toggle dispatch tests (4 tests)

---

## Acceptance Criteria

- [ ] Bell icon in toolbar shows unread count badge (hidden when 0)
- [ ] Clicking bell opens dropdown with up to 5 recent notifications
- [ ] Dropdown shows category chips with correct colors
- [ ] "Tout marquer comme lu" button marks all as read
- [ ] "Voir tout" link navigates to `/notifications`
- [ ] List page shows all notifications in a Material table
- [ ] List page supports filtering by category and read/unread status
- [ ] List page supports pagination with page size options
- [ ] Individual "mark as read" works from both dropdown and list
- [ ] Preferences page shows toggle matrix for all 7 categories
- [ ] Toggle changes dispatch update to API immediately
- [ ] Polling refreshes unread count every 30 seconds
- [ ] All French UI text is correct
- [ ] All Jest tests pass
