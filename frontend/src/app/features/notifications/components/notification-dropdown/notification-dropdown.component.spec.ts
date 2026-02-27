// frontend/src/app/features/notifications/components/notification-dropdown/notification-dropdown.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { NotificationDropdownComponent } from './notification-dropdown.component';
import { NotificationState, initialNotificationState } from '../../store/notification.state';
import { NotificationActions } from '../../store/notification.actions';
import {
  Notification,
  NotificationCategory,
  NotificationType,
} from '@shared/models/notification.model';

describe('NotificationDropdownComponent', () => {
  let component: NotificationDropdownComponent;
  let fixture: ComponentFixture<NotificationDropdownComponent>;
  let store: MockStore;

  const mockNotification: Notification = {
    id: 1,
    userId: 1,
    type: NotificationType.IN_APP,
    category: NotificationCategory.PAYMENT,
    title: 'Paiement confirme',
    message: 'Votre paiement de 150 EUR a ete confirme pour le Club Sportif Paris.',
    read: false,
    referenceId: '42',
    referenceType: 'PAYMENT',
    createdAt: '2026-02-27T10:00:00',
    readAt: null,
  };

  const testInitialState: { notifications: NotificationState } = {
    notifications: {
      ...initialNotificationState,
      dropdownOpen: true,
      notifications: [mockNotification],
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationDropdownComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideMockStore({ initialState: testInitialState }),
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(NotificationDropdownComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should dispatch loadNotifications on init', () => {
    expect(store.dispatch).toHaveBeenCalledWith(
      NotificationActions.loadNotifications({ page: 0, size: 5 })
    );
  });

  it('should render notification items when dropdown is open', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Paiement confirme');
  });

  it('should truncate long messages', () => {
    const longMessage = 'A'.repeat(100);
    const truncated = component.truncateMessage(longMessage);
    expect(truncated.length).toBe(83); // 80 + '...'
    expect(truncated.endsWith('...')).toBe(true);
  });

  it('should dispatch markAllAsRead when button is clicked', () => {
    component.markAllAsRead();

    expect(store.dispatch).toHaveBeenCalledWith(
      NotificationActions.markAllAsRead()
    );
  });

  it('should dispatch markAsRead when a notification is clicked', () => {
    component.markAsRead(1);

    expect(store.dispatch).toHaveBeenCalledWith(
      NotificationActions.markAsRead({ id: 1 })
    );
  });
});
