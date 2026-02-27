// frontend/src/app/features/payments/components/payment-list/payment-list.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { PaymentListComponent } from './payment-list.component';
import { PaymentState, initialPaymentState } from '../../store/payment.state';
import { PaymentActions } from '../../store/payment.actions';
import { PaymentStatus, PaymentSummary } from '@shared/models/payment.model';

describe('PaymentListComponent', () => {
  let component: PaymentListComponent;
  let fixture: ComponentFixture<PaymentListComponent>;
  let store: MockStore;

  const testInitialState: { payments: PaymentState } = {
    payments: { ...initialPaymentState },
  };

  const mockPayment: PaymentSummary = {
    id: 1,
    subscriptionId: 10,
    familyMemberName: 'Jean Dupont',
    associationName: 'Club Sportif Paris',
    activityName: 'Football',
    amount: 150.5,
    paymentType: 'ADHESION',
    status: PaymentStatus.COMPLETED,
    paymentMethod: null,
    paidAt: '2026-02-27T12:00:00',
    invoiceId: null,
    createdAt: '2026-02-27T10:00:00',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentListComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideMockStore({ initialState: testInitialState }),
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(PaymentListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should dispatch loadPayments on init', () => {
    expect(store.dispatch).toHaveBeenCalledWith(
      PaymentActions.loadPayments({
        params: {
          familyId: 1,
          status: undefined,
          page: 0,
          size: 10,
        },
      })
    );
  });

  it('should render table when payments are loaded', () => {
    store.setState({
      payments: {
        ...initialPaymentState,
        payments: [mockPayment],
        totalElements: 1,
        totalPages: 1,
      },
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('table')).toBeTruthy();
    expect(compiled.textContent).toContain('Jean Dupont');
    expect(compiled.textContent).toContain('Club Sportif Paris');
  });

  it('should format amount in EUR when displaying amount', () => {
    const formatted = component.formatAmount(150.5);
    // French locale EUR format includes the amount and EUR symbol
    expect(formatted).toContain('150');
    expect(formatted).toContain('50');
  });

  it('should dispatch setStatusFilter when status filter is changed', () => {
    component.onStatusFilterChange(PaymentStatus.COMPLETED);

    expect(store.dispatch).toHaveBeenCalledWith(
      PaymentActions.setStatusFilter({ status: PaymentStatus.COMPLETED })
    );
  });

  it('should show empty state when there are no payments', () => {
    store.setState({
      payments: {
        ...initialPaymentState,
        payments: [],
      },
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Aucun paiement trouve');
  });

  it('should show error when an error occurs', () => {
    store.setState({
      payments: {
        ...initialPaymentState,
        error: 'Erreur de chargement',
      },
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Erreur de chargement');
  });
});
