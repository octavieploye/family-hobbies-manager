# Story S6-004: Angular Notification Feature -- TDD Tests

> Companion file for [S6-004: Angular Notification Feature](./S6-004-angular-notification-feature.md)
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Test File 1: NotificationService Tests

**File**: `frontend/src/app/features/notifications/services/notification.service.spec.ts`

```typescript
// frontend/src/app/features/notifications/services/notification.service.spec.ts

import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { NotificationService } from './notification.service';
import {
  NotificationCategory,
  NotificationResponse,
  PageResponse,
  UnreadCountResponse,
  MarkReadResponse,
  MarkAllReadResponse,
  NotificationPreferenceRequest,
  NotificationPreferenceResponse
} from '../../../shared/models/notification.model';
import { environment } from '../../../../environments/environment';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/api/v1/notifications`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [NotificationService]
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getMyNotifications', () => {
    it('should call GET /me with no params when none provided', () => {
      const mockPage: PageResponse<NotificationResponse> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 20,
        number: 0,
        first: true,
        last: true
      };

      service.getMyNotifications().subscribe((result) => {
        expect(result).toEqual(mockPage);
      });

      const req = httpMock.expectOne(`${baseUrl}/me`);
      expect(req.request.method).toBe('GET');
      req.flush(mockPage);
    });

    it('should pass query params when provided', () => {
      const mockPage: PageResponse<NotificationResponse> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 10,
        number: 0,
        first: true,
        last: true
      };

      service
        .getMyNotifications({
          read: false,
          category: NotificationCategory.PAYMENT_SUCCESS,
          page: 0,
          size: 10,
          sort: 'createdAt,desc'
        })
        .subscribe((result) => {
          expect(result).toEqual(mockPage);
        });

      const req = httpMock.expectOne(
        (r) =>
          r.url === `${baseUrl}/me` &&
          r.params.get('read') === 'false' &&
          r.params.get('category') === 'PAYMENT_SUCCESS' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '10' &&
          r.params.get('sort') === 'createdAt,desc'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockPage);
    });
  });

  describe('getUnreadCount', () => {
    it('should call GET /me/unread-count', () => {
      const mockResponse: UnreadCountResponse = { count: 5 };

      service.getUnreadCount().subscribe((result) => {
        expect(result).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${baseUrl}/me/unread-count`);
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });
  });

  describe('markAsRead', () => {
    it('should call PUT /{id}/read', () => {
      const mockResponse: MarkReadResponse = {
        id: 42,
        read: true,
        readAt: '2026-02-24T10:00:00Z'
      };

      service.markAsRead(42).subscribe((result) => {
        expect(result).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${baseUrl}/42/read`);
      expect(req.request.method).toBe('PUT');
      req.flush(mockResponse);
    });
  });

  describe('markAllAsRead', () => {
    it('should call PUT /read-all', () => {
      const mockResponse: MarkAllReadResponse = {
        markedCount: 3,
        readAt: '2026-02-24T10:00:00Z'
      };

      service.markAllAsRead().subscribe((result) => {
        expect(result).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${baseUrl}/read-all`);
      expect(req.request.method).toBe('PUT');
      req.flush(mockResponse);
    });
  });

  describe('getPreferences', () => {
    it('should call GET /preferences', () => {
      const mockResponse: NotificationPreferenceResponse = {
        userId: 1,
        categories: {
          [NotificationCategory.WELCOME]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.PAYMENT_SUCCESS]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.PAYMENT_FAILED]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.SUBSCRIPTION_CONFIRMED]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.SUBSCRIPTION_CANCELLED]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.ATTENDANCE_REMINDER]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.SYSTEM]: { emailEnabled: false, inAppEnabled: true }
        }
      };

      service.getPreferences().subscribe((result) => {
        expect(result).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${baseUrl}/preferences`);
      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });
  });

  describe('updatePreferences', () => {
    it('should call PUT /preferences with array request body', () => {
      // Backend expects NotificationPreferenceRequest[] (array, not object map)
      const requests: NotificationPreferenceRequest[] = [
        { category: NotificationCategory.WELCOME, emailEnabled: false, inAppEnabled: true },
        { category: NotificationCategory.PAYMENT_SUCCESS, emailEnabled: true, inAppEnabled: true },
        { category: NotificationCategory.PAYMENT_FAILED, emailEnabled: true, inAppEnabled: true },
        { category: NotificationCategory.SUBSCRIPTION_CONFIRMED, emailEnabled: true, inAppEnabled: true },
        { category: NotificationCategory.SUBSCRIPTION_CANCELLED, emailEnabled: true, inAppEnabled: true },
        { category: NotificationCategory.ATTENDANCE_REMINDER, emailEnabled: true, inAppEnabled: true },
        { category: NotificationCategory.SYSTEM, emailEnabled: false, inAppEnabled: true }
      ];
      const mockResponse: NotificationPreferenceResponse = {
        userId: 1,
        categories: {
          [NotificationCategory.WELCOME]: { emailEnabled: false, inAppEnabled: true },
          [NotificationCategory.PAYMENT_SUCCESS]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.PAYMENT_FAILED]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.SUBSCRIPTION_CONFIRMED]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.SUBSCRIPTION_CANCELLED]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.ATTENDANCE_REMINDER]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.SYSTEM]: { emailEnabled: false, inAppEnabled: true }
        }
      };

      service.updatePreferences(requests).subscribe((result) => {
        expect(result).toEqual(mockResponse);
      });

      const req = httpMock.expectOne(`${baseUrl}/preferences`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(requests);
      req.flush(mockResponse);
    });
  });
});
```

---

## Test File 2: Notification Reducer Tests

**File**: `frontend/src/app/features/notifications/store/notification.reducer.spec.ts`

```typescript
// frontend/src/app/features/notifications/store/notification.reducer.spec.ts

import { notificationReducer } from './notification.reducer';
import { NotificationActions } from './notification.actions';
import {
  initialNotificationState,
  NotificationState
} from './notification.state';
import {
  NotificationCategory,
  NotificationResponse,
  PageResponse
} from '../../../shared/models/notification.model';

describe('notificationReducer', () => {
  const mockNotification: NotificationResponse = {
    id: 1,
    userId: 10,
    category: NotificationCategory.WELCOME,
    title: 'Bienvenue !',
    message: 'Bienvenue dans Family Hobbies Manager',
    actionUrl: '/dashboard',
    read: false,
    readAt: null,
    createdAt: '2026-02-24T08:00:00Z'
  };

  const mockPage: PageResponse<NotificationResponse> = {
    content: [mockNotification],
    totalElements: 1,
    totalPages: 1,
    size: 20,
    number: 0,
    first: true,
    last: true
  };

  it('should return the initial state', () => {
    const action = { type: 'unknown' };
    const state = notificationReducer(undefined, action as any);
    expect(state).toEqual(initialNotificationState);
  });

  describe('loadNotifications', () => {
    it('should set loading to true', () => {
      const action = NotificationActions.loadNotifications({
        page: 0,
        size: 20
      });
      const state = notificationReducer(initialNotificationState, action);
      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('should update notifications on success', () => {
      const action = NotificationActions.loadNotificationsSuccess({
        page: mockPage
      });
      const state = notificationReducer(initialNotificationState, action);
      expect(state.loading).toBe(false);
      expect(state.notifications).toEqual([mockNotification]);
      expect(state.totalElements).toBe(1);
    });

    it('should set error on failure', () => {
      const action = NotificationActions.loadNotificationsFailure({
        error: 'Network error'
      });
      const state = notificationReducer(initialNotificationState, action);
      expect(state.loading).toBe(false);
      expect(state.error).toBe('Network error');
    });
  });

  describe('loadUnreadCount', () => {
    it('should update unread count on success', () => {
      const action = NotificationActions.loadUnreadCountSuccess({ count: 5 });
      const state = notificationReducer(initialNotificationState, action);
      expect(state.unreadCount).toBe(5);
    });
  });

  describe('markAsRead', () => {
    it('should mark notification as read and decrement count', () => {
      const prevState: NotificationState = {
        ...initialNotificationState,
        notifications: [mockNotification],
        unreadCount: 3
      };
      const action = NotificationActions.markAsReadSuccess({
        notificationId: 1,
        readAt: '2026-02-24T10:00:00Z'
      });
      const state = notificationReducer(prevState, action);
      expect(state.notifications[0].read).toBe(true);
      expect(state.notifications[0].readAt).toBe('2026-02-24T10:00:00Z');
      expect(state.unreadCount).toBe(2);
    });

    it('should not go below 0 for unread count', () => {
      const prevState: NotificationState = {
        ...initialNotificationState,
        notifications: [mockNotification],
        unreadCount: 0
      };
      const action = NotificationActions.markAsReadSuccess({
        notificationId: 1,
        readAt: '2026-02-24T10:00:00Z'
      });
      const state = notificationReducer(prevState, action);
      expect(state.unreadCount).toBe(0);
    });
  });

  describe('markAllAsRead', () => {
    it('should mark all notifications as read and set count to 0', () => {
      const secondNotification: NotificationResponse = {
        ...mockNotification,
        id: 2,
        title: 'Paiement recu'
      };
      const prevState: NotificationState = {
        ...initialNotificationState,
        notifications: [mockNotification, secondNotification],
        unreadCount: 2
      };
      const action = NotificationActions.markAllAsReadSuccess({
        markedCount: 2,
        readAt: '2026-02-24T10:00:00Z'
      });
      const state = notificationReducer(prevState, action);
      expect(state.notifications.every((n) => n.read)).toBe(true);
      expect(state.unreadCount).toBe(0);
    });
  });

  describe('preferences', () => {
    it('should set loading on loadPreferences', () => {
      const action = NotificationActions.loadPreferences();
      const state = notificationReducer(initialNotificationState, action);
      expect(state.loadingPreferences).toBe(true);
    });

    it('should store preferences on success', () => {
      const prefs = {
        userId: 1,
        categories: {
          [NotificationCategory.WELCOME]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.PAYMENT_SUCCESS]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.PAYMENT_FAILED]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.SUBSCRIPTION_CONFIRMED]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.SUBSCRIPTION_CANCELLED]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.ATTENDANCE_REMINDER]: { emailEnabled: true, inAppEnabled: true },
          [NotificationCategory.SYSTEM]: { emailEnabled: false, inAppEnabled: true }
        }
      };
      const action = NotificationActions.loadPreferencesSuccess({
        preferences: prefs
      });
      const state = notificationReducer(initialNotificationState, action);
      expect(state.loadingPreferences).toBe(false);
      expect(state.preferences).toEqual(prefs);
    });
  });

  describe('filters', () => {
    it('should update selected category', () => {
      const action = NotificationActions.setCategoryFilter({
        category: NotificationCategory.PAYMENT_SUCCESS
      });
      const state = notificationReducer(initialNotificationState, action);
      expect(state.selectedCategory).toBe(NotificationCategory.PAYMENT_SUCCESS);
    });

    it('should update read filter', () => {
      const action = NotificationActions.setReadFilter({ read: false });
      const state = notificationReducer(initialNotificationState, action);
      expect(state.showReadOnly).toBe(false);
    });
  });
});
```

---

## Test File 3: NotificationBellComponent Tests

**File**: `frontend/src/app/features/notifications/components/notification-bell/notification-bell.component.spec.ts`

```typescript
// frontend/src/app/features/notifications/components/notification-bell/notification-bell.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { NotificationBellComponent } from './notification-bell.component';
import {
  selectUnreadCount,
  selectHasUnread
} from '../../store/notification.selectors';
import { NotificationActions } from '../../store/notification.actions';
import { By } from '@angular/platform-browser';

describe('NotificationBellComponent', () => {
  let component: NotificationBellComponent;
  let fixture: ComponentFixture<NotificationBellComponent>;
  let store: MockStore;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationBellComponent, NoopAnimationsModule],
      providers: [
        provideMockStore({
          selectors: [
            { selector: selectUnreadCount, value: 0 },
            { selector: selectHasUnread, value: false }
          ]
        })
      ]
    }).compileComponents();

    store = TestBed.inject(MockStore);
    fixture = TestBed.createComponent(NotificationBellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch startPolling on init', () => {
    const dispatchSpy = jest.spyOn(store, 'dispatch');
    component.ngOnInit();
    expect(dispatchSpy).toHaveBeenCalledWith(
      NotificationActions.startPolling()
    );
  });

  it('should hide badge when unread count is 0', () => {
    store.overrideSelector(selectHasUnread, false);
    store.overrideSelector(selectUnreadCount, 0);
    store.refreshState();
    fixture.detectChanges();

    const icon = fixture.debugElement.query(By.css('mat-icon'));
    expect(icon.attributes['matBadgeHidden']).toBeTruthy();
  });

  it('should show badge with count when there are unread notifications', () => {
    store.overrideSelector(selectHasUnread, true);
    store.overrideSelector(selectUnreadCount, 7);
    store.refreshState();
    fixture.detectChanges();

    const icon = fixture.debugElement.query(By.css('mat-icon'));
    expect(icon.attributes['matBadgeHidden']).toBeFalsy();
  });
});
```

---

## Test File 4: NotificationDropdownComponent Tests

**File**: `frontend/src/app/features/notifications/components/notification-dropdown/notification-dropdown.component.spec.ts`

```typescript
// frontend/src/app/features/notifications/components/notification-dropdown/notification-dropdown.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { NotificationDropdownComponent } from './notification-dropdown.component';
import {
  selectRecentNotifications,
  selectNotificationsLoading,
  selectHasUnread
} from '../../store/notification.selectors';
import { NotificationActions } from '../../store/notification.actions';
import {
  NotificationCategory,
  NotificationResponse
} from '../../../../shared/models/notification.model';
import { By } from '@angular/platform-browser';

describe('NotificationDropdownComponent', () => {
  let component: NotificationDropdownComponent;
  let fixture: ComponentFixture<NotificationDropdownComponent>;
  let store: MockStore;

  const mockNotifications: NotificationResponse[] = [
    {
      id: 1,
      userId: 10,
      category: NotificationCategory.WELCOME,
      title: 'Bienvenue !',
      message: 'Bienvenue dans Family Hobbies Manager',
      actionUrl: '/dashboard',
      read: false,
      readAt: null,
      createdAt: '2026-02-24T08:00:00Z'
    },
    {
      id: 2,
      userId: 10,
      category: NotificationCategory.PAYMENT_SUCCESS,
      title: 'Paiement recu',
      message: 'Votre paiement de 150.00 EUR a ete recu',
      actionUrl: '/payments/42',
      read: true,
      readAt: '2026-02-24T09:00:00Z',
      createdAt: '2026-02-24T08:30:00Z'
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        NotificationDropdownComponent,
        NoopAnimationsModule,
        RouterTestingModule
      ],
      providers: [
        provideMockStore({
          selectors: [
            { selector: selectRecentNotifications, value: mockNotifications },
            { selector: selectNotificationsLoading, value: false },
            { selector: selectHasUnread, value: true }
          ]
        })
      ]
    }).compileComponents();

    store = TestBed.inject(MockStore);
    fixture = TestBed.createComponent(NotificationDropdownComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should render the notification list with correct items', () => {
    const items = fixture.debugElement.queryAll(
      By.css('.notification-dropdown__item')
    );
    expect(items.length).toBe(2);
  });

  it('should show "Tout marquer comme lu" button when there are unread', () => {
    const markAllBtn = fixture.debugElement.query(
      By.css('.notification-dropdown__mark-all')
    );
    expect(markAllBtn).toBeTruthy();
    expect(markAllBtn.nativeElement.textContent).toContain(
      'Tout marquer comme lu'
    );
  });

  it('should dispatch markAllAsRead on button click', () => {
    const dispatchSpy = jest.spyOn(store, 'dispatch');
    const markAllBtn = fixture.debugElement.query(
      By.css('.notification-dropdown__mark-all')
    );
    markAllBtn.nativeElement.click();
    expect(dispatchSpy).toHaveBeenCalledWith(
      NotificationActions.markAllAsRead()
    );
  });

  it('should show empty message when no notifications', () => {
    store.overrideSelector(selectRecentNotifications, []);
    store.overrideSelector(selectHasUnread, false);
    store.refreshState();
    fixture.detectChanges();

    const empty = fixture.debugElement.query(
      By.css('.notification-dropdown__empty')
    );
    expect(empty).toBeTruthy();
    expect(empty.nativeElement.textContent).toContain('Aucune notification');
  });
});
```

---

## Test File 5: NotificationListComponent Tests

**File**: `frontend/src/app/features/notifications/pages/notification-list/notification-list.component.spec.ts`

```typescript
// frontend/src/app/features/notifications/pages/notification-list/notification-list.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { NotificationListComponent } from './notification-list.component';
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
import { NotificationActions } from '../../store/notification.actions';
import {
  NotificationCategory,
  NotificationResponse
} from '../../../../shared/models/notification.model';
import { By } from '@angular/platform-browser';

describe('NotificationListComponent', () => {
  let component: NotificationListComponent;
  let fixture: ComponentFixture<NotificationListComponent>;
  let store: MockStore;

  const mockNotifications: NotificationResponse[] = [
    {
      id: 1,
      userId: 10,
      category: NotificationCategory.WELCOME,
      title: 'Bienvenue Famille Dupont !',
      message: 'Bienvenue dans Family Hobbies Manager',
      actionUrl: '/dashboard',
      read: false,
      readAt: null,
      createdAt: '2026-02-24T08:00:00Z'
    },
    {
      id: 2,
      userId: 10,
      category: NotificationCategory.SUBSCRIPTION_CONFIRMED,
      title: 'Inscription confirmee',
      message: 'Lucas Dupont inscrit au Judo Club Lyon',
      actionUrl: '/subscriptions/5',
      read: true,
      readAt: '2026-02-24T09:00:00Z',
      createdAt: '2026-02-23T14:00:00Z'
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        NotificationListComponent,
        NoopAnimationsModule,
        RouterTestingModule
      ],
      providers: [
        provideMockStore({
          selectors: [
            { selector: selectNotifications, value: mockNotifications },
            { selector: selectNotificationsLoading, value: false },
            { selector: selectTotalElements, value: 2 },
            { selector: selectCurrentPage, value: 0 },
            { selector: selectPageSize, value: 20 },
            { selector: selectSelectedCategory, value: null },
            { selector: selectShowReadOnly, value: null },
            { selector: selectHasUnread, value: true }
          ]
        })
      ]
    }).compileComponents();

    store = TestBed.inject(MockStore);
    fixture = TestBed.createComponent(NotificationListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch loadNotifications on init', () => {
    const dispatchSpy = jest.spyOn(store, 'dispatch');
    component.ngOnInit();
    expect(dispatchSpy).toHaveBeenCalledWith(
      NotificationActions.loadNotifications({ page: 0, size: 20 })
    );
  });

  it('should render table rows for each notification', () => {
    const rows = fixture.debugElement.queryAll(By.css('tr[mat-row]'));
    expect(rows.length).toBe(2);
  });

  it('should show page title as "Notifications"', () => {
    const title = fixture.debugElement.query(
      By.css('.notification-list__title')
    );
    expect(title.nativeElement.textContent).toContain('Notifications');
  });

  it('should dispatch loadNotifications with category on filter change', () => {
    const dispatchSpy = jest.spyOn(store, 'dispatch');
    component.onCategoryChange(NotificationCategory.PAYMENT_SUCCESS);
    expect(dispatchSpy).toHaveBeenCalledWith(
      NotificationActions.setCategoryFilter({
        category: NotificationCategory.PAYMENT_SUCCESS
      })
    );
    expect(dispatchSpy).toHaveBeenCalledWith(
      NotificationActions.loadNotifications({
        page: 0,
        size: 20,
        category: NotificationCategory.PAYMENT_SUCCESS
      })
    );
  });
});
```

---

## Test File 6: NotificationPreferencesComponent Tests

**File**: `frontend/src/app/features/notifications/components/notification-preferences/notification-preferences.component.spec.ts`

```typescript
// frontend/src/app/features/notifications/components/notification-preferences/notification-preferences.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { NotificationPreferencesComponent } from './notification-preferences.component';
import {
  selectPreferences,
  selectLoadingPreferences
} from '../../store/notification.selectors';
import { NotificationActions } from '../../store/notification.actions';
import {
  NotificationCategory,
  NotificationPreferenceResponse
} from '../../../../shared/models/notification.model';
import { By } from '@angular/platform-browser';

describe('NotificationPreferencesComponent', () => {
  let component: NotificationPreferencesComponent;
  let fixture: ComponentFixture<NotificationPreferencesComponent>;
  let store: MockStore;

  const mockPreferences: NotificationPreferenceResponse = {
    userId: 1,
    categories: {
      [NotificationCategory.WELCOME]: { emailEnabled: true, inAppEnabled: true },
      [NotificationCategory.PAYMENT_SUCCESS]: { emailEnabled: true, inAppEnabled: true },
      [NotificationCategory.PAYMENT_FAILED]: { emailEnabled: true, inAppEnabled: true },
      [NotificationCategory.SUBSCRIPTION_CONFIRMED]: { emailEnabled: true, inAppEnabled: true },
      [NotificationCategory.SUBSCRIPTION_CANCELLED]: { emailEnabled: true, inAppEnabled: true },
      [NotificationCategory.ATTENDANCE_REMINDER]: { emailEnabled: true, inAppEnabled: true },
      [NotificationCategory.SYSTEM]: { emailEnabled: false, inAppEnabled: true }
    }
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationPreferencesComponent, NoopAnimationsModule],
      providers: [
        provideMockStore({
          selectors: [
            { selector: selectPreferences, value: mockPreferences },
            { selector: selectLoadingPreferences, value: false }
          ]
        })
      ]
    }).compileComponents();

    store = TestBed.inject(MockStore);
    fixture = TestBed.createComponent(NotificationPreferencesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch loadPreferences on init', () => {
    const dispatchSpy = jest.spyOn(store, 'dispatch');
    component.ngOnInit();
    expect(dispatchSpy).toHaveBeenCalledWith(
      NotificationActions.loadPreferences()
    );
  });

  it('should render rows for all 7 categories', () => {
    const rows = fixture.debugElement.queryAll(By.css('tr[mat-row]'));
    expect(rows.length).toBe(7);
  });

  it('should dispatch updatePreferences when toggle is changed', () => {
    const dispatchSpy = jest.spyOn(store, 'dispatch');

    // Simulate toggling the WELCOME email preference off
    component.onToggle(
      {
        category: NotificationCategory.WELCOME,
        label: 'Bienvenue',
        emailEnabled: true,
        inAppEnabled: true
      },
      'email',
      { checked: false } as any
    );

    expect(dispatchSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        type: '[Notifications] Update Preferences'
      })
    );
  });
});
```

---

## Test Summary

| Test File | Test Count | What It Verifies |
|-----------|-----------|------------------|
| `notification.service.spec.ts` | 6 | All 6 HTTP endpoints call correct URLs with correct methods and params |
| `notification.reducer.spec.ts` | 12 | All state transitions: load, unread count, mark read, mark all, preferences, filters |
| `notification-bell.component.spec.ts` | 4 | Badge visibility, polling dispatch, unread count display |
| `notification-dropdown.component.spec.ts` | 5 | List rendering, mark all button, empty state, action dispatch |
| `notification-list.component.spec.ts` | 5 | Table rows, title, filter dispatch, initial load |
| `notification-preferences.component.spec.ts` | 4 | Category rows, toggle dispatch, initial load |
| **Total** | **36** | |

### Run All Notification Tests

```bash
cd frontend
npx jest --testPathPattern="features/notifications" --verbose
```

Expected: all 36 tests pass (will initially fail -- TDD red phase).
