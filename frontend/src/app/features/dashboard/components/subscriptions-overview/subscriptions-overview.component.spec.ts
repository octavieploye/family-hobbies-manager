// frontend/src/app/features/dashboard/components/subscriptions-overview/subscriptions-overview.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { SubscriptionsOverviewComponent } from './subscriptions-overview.component';
import { SubscriptionSummary } from '@shared/models/dashboard.model';

describe('SubscriptionsOverviewComponent', () => {
  let component: SubscriptionsOverviewComponent;
  let fixture: ComponentFixture<SubscriptionsOverviewComponent>;

  const mockSub: SubscriptionSummary = {
    subscriptionId: 1,
    memberName: 'Marie Dupont',
    activityName: 'Natation enfants',
    associationName: 'Lyon Natation',
    status: 'ACTIVE',
    startDate: '2024-09-01',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SubscriptionsOverviewComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(SubscriptionsOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display empty state when no subscriptions', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Aucune inscription active');
  });

  it('should return correct status labels in French', () => {
    expect(component.getStatusLabel('ACTIVE')).toBe('Active');
    expect(component.getStatusLabel('PENDING')).toBe('En attente');
  });
});
