// frontend/src/app/features/notifications/components/notification-bell/notification-bell.component.spec.ts
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { NotificationBellComponent } from './notification-bell.component';
import { NotificationState, initialNotificationState } from '../../store/notification.state';
import { NotificationActions } from '../../store/notification.actions';

describe('NotificationBellComponent', () => {
  let component: NotificationBellComponent;
  let fixture: ComponentFixture<NotificationBellComponent>;
  let store: MockStore;

  const testInitialState: { notifications: NotificationState } = {
    notifications: { ...initialNotificationState },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationBellComponent, NoopAnimationsModule],
      providers: [
        provideMockStore({ initialState: testInitialState }),
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(NotificationBellComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    component.ngOnDestroy();
  });

  it('should dispatch loadUnreadCount on init', () => {
    expect(store.dispatch).toHaveBeenCalledWith(
      NotificationActions.loadUnreadCount()
    );
  });

  it('should render the notifications icon', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('mat-icon')).toBeTruthy();
  });

  it('should dispatch toggleDropdown when bell is clicked', () => {
    component.toggleDropdown();

    expect(store.dispatch).toHaveBeenCalledWith(
      NotificationActions.toggleDropdown()
    );
  });

  it('should show badge when there are unread notifications', () => {
    store.setState({
      notifications: {
        ...initialNotificationState,
        unreadCount: 3,
      },
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const icon = compiled.querySelector('mat-icon.mat-badge');
    expect(icon).toBeTruthy();
  });

  it('should show notifications_none icon when unread count is 0', () => {
    store.setState({
      notifications: {
        ...initialNotificationState,
        unreadCount: 0,
      },
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const icon = compiled.querySelector('mat-icon');
    expect(icon?.textContent?.trim()).toBe('notifications_none');
  });
});
