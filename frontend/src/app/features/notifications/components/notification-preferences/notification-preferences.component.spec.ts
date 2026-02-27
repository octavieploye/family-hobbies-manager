// frontend/src/app/features/notifications/components/notification-preferences/notification-preferences.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { NotificationPreferencesComponent } from './notification-preferences.component';
import { NotificationState, initialNotificationState } from '../../store/notification.state';
import { NotificationActions } from '../../store/notification.actions';
import {
  NotificationCategory,
  NotificationPreference,
} from '@shared/models/notification.model';

describe('NotificationPreferencesComponent', () => {
  let component: NotificationPreferencesComponent;
  let fixture: ComponentFixture<NotificationPreferencesComponent>;
  let store: MockStore;

  const mockPreference: NotificationPreference = {
    id: 1,
    userId: 1,
    category: NotificationCategory.PAYMENT,
    emailEnabled: true,
    inAppEnabled: true,
  };

  const testInitialState: { notifications: NotificationState } = {
    notifications: { ...initialNotificationState },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationPreferencesComponent, NoopAnimationsModule],
      providers: [
        provideMockStore({ initialState: testInitialState }),
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(NotificationPreferencesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should dispatch loadPreferences on init', () => {
    expect(store.dispatch).toHaveBeenCalledWith(
      NotificationActions.loadPreferences()
    );
  });

  it('should render preferences table when preferences are loaded', () => {
    store.setState({
      notifications: {
        ...initialNotificationState,
        preferences: [mockPreference],
        loading: false,
      },
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('table')).toBeTruthy();
    expect(compiled.textContent).toContain('Paiement');
  });

  it('should get correct category label', () => {
    expect(component.getCategoryLabel(NotificationCategory.PAYMENT)).toBe('Paiement');
    expect(component.getCategoryLabel(NotificationCategory.SYSTEM)).toBe('Systeme');
  });

  it('should dispatch updatePreference when email toggle is changed', () => {
    const event = { checked: false } as any;
    component.onEmailToggle(mockPreference, event);

    expect(store.dispatch).toHaveBeenCalledWith(
      NotificationActions.updatePreference({
        request: {
          category: NotificationCategory.PAYMENT,
          emailEnabled: false,
          inAppEnabled: true,
        },
      })
    );
  });

  it('should dispatch updatePreference when in-app toggle is changed', () => {
    const event = { checked: false } as any;
    component.onInAppToggle(mockPreference, event);

    expect(store.dispatch).toHaveBeenCalledWith(
      NotificationActions.updatePreference({
        request: {
          category: NotificationCategory.PAYMENT,
          emailEnabled: true,
          inAppEnabled: false,
        },
      })
    );
  });
});
