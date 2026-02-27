// frontend/src/app/features/notifications/components/notification-list/notification-list.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { NotificationListComponent } from './notification-list.component';
import { NotificationState, initialNotificationState } from '../../store/notification.state';
import { NotificationActions } from '../../store/notification.actions';
import {
  Notification,
  NotificationCategory,
  NotificationType,
} from '@shared/models/notification.model';

describe('NotificationListComponent', () => {
  let component: NotificationListComponent;
  let fixture: ComponentFixture<NotificationListComponent>;
  let store: MockStore;

  const mockNotification: Notification = {
    id: 1,
    userId: 1,
    type: NotificationType.IN_APP,
    category: NotificationCategory.PAYMENT,
    title: 'Paiement confirme',
    message: 'Votre paiement de 150 EUR a ete confirme.',
    read: false,
    referenceId: '42',
    referenceType: 'PAYMENT',
    createdAt: '2026-02-27T10:00:00',
    readAt: null,
  };

  const testInitialState: { notifications: NotificationState } = {
    notifications: { ...initialNotificationState },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationListComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideMockStore({ initialState: testInitialState }),
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(NotificationListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should dispatch loadNotifications on init', () => {
    expect(store.dispatch).toHaveBeenCalledWith(
      NotificationActions.loadNotifications({ page: 0, size: 10 })
    );
  });

  it('should render table when notifications are loaded', () => {
    store.setState({
      notifications: {
        ...initialNotificationState,
        notifications: [mockNotification],
        totalElements: 1,
        totalPages: 1,
      },
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('table')).toBeTruthy();
    expect(compiled.textContent).toContain('Paiement confirme');
  });

  it('should show empty state when there are no notifications', () => {
    store.setState({
      notifications: {
        ...initialNotificationState,
        notifications: [],
      },
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Aucune notification');
  });

  it('should get correct category label', () => {
    expect(component.getCategoryLabel(NotificationCategory.PAYMENT)).toBe('Paiement');
    expect(component.getCategoryLabel(NotificationCategory.WELCOME)).toBe('Bienvenue');
  });

  it('should dispatch markAsRead when notification row is clicked', () => {
    component.onNotificationClick(mockNotification);

    expect(store.dispatch).toHaveBeenCalledWith(
      NotificationActions.markAsRead({ id: 1 })
    );
  });
});
