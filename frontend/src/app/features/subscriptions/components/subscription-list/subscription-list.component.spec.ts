// frontend/src/app/features/subscriptions/components/subscription-list/subscription-list.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { SubscriptionListComponent } from './subscription-list.component';
import { SubscriptionState, initialSubscriptionState } from '../../store/subscription.reducer';
import { Subscription } from '@shared/models/subscription.model';

describe('SubscriptionListComponent', () => {
  let component: SubscriptionListComponent;
  let fixture: ComponentFixture<SubscriptionListComponent>;
  let store: MockStore;

  const mockSubscription: Subscription = {
    id: 1,
    activityId: 5,
    activityName: 'Natation enfants',
    associationName: 'Lyon Natation Metropole',
    familyMemberId: 10,
    memberFirstName: 'Marie',
    memberLastName: 'Dupont',
    familyId: 3,
    userId: 1,
    subscriptionType: 'ADHESION',
    status: 'ACTIVE',
    startDate: '2024-09-01',
    endDate: null,
    cancellationReason: null,
    cancelledAt: null,
    createdAt: '2024-08-15T10:00:00',
    updatedAt: '2024-08-15T10:00:00',
  };

  const initialState: { subscriptions: SubscriptionState } = {
    subscriptions: { ...initialSubscriptionState },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SubscriptionListComponent, NoopAnimationsModule],
      providers: [
        provideMockStore({ initialState }),
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(SubscriptionListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch loadSubscriptions on init', () => {
    expect(store.dispatch).toHaveBeenCalled();
  });

  it('should display page title', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Mes inscriptions');
  });

  it('should display empty state when no subscriptions', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Aucune inscription');
  });

  it('should compute canCancel correctly for ACTIVE subscription', () => {
    expect(component.canCancel(mockSubscription)).toBe(true);
  });

  it('should compute canCancel correctly for CANCELLED subscription', () => {
    const cancelled = { ...mockSubscription, status: 'CANCELLED' as const };
    expect(component.canCancel(cancelled)).toBe(false);
  });

  it('should return correct status labels in French', () => {
    expect(component.getStatusLabel('PENDING')).toBe('En attente');
    expect(component.getStatusLabel('ACTIVE')).toBe('Active');
    expect(component.getStatusLabel('EXPIRED')).toContain('Expir');
    expect(component.getStatusLabel('CANCELLED')).toContain('Annul');
  });

  it('should return correct type labels in French', () => {
    expect(component.getTypeLabel('ADHESION')).toContain('Adh');
    expect(component.getTypeLabel('COTISATION')).toBe('Cotisation');
  });
});
