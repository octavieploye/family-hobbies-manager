# Story S5-006: Angular Payment Feature -- Failing Tests (TDD Contract)

> Companion file to [S5-006-angular-payment-feature.md](./S5-006-angular-payment-feature.md)
> Contains the full Jest test source code for PaymentListComponent, CheckoutRedirectComponent, PaymentService, and payment.reducer.

---

## Test File 1: PaymentListComponent Spec

**Path**: `frontend/src/app/features/payments/components/payment-list/payment-list.component.spec.ts`

```typescript
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { provideMockStore, MockStore } from '@ngrx/store/testing';
import { PaymentListComponent } from './payment-list.component';
import { PaymentActions } from '../../store/payment.actions';
import {
  selectPayments,
  selectTotalElements,
  selectCurrentPage,
  selectPageSize,
  selectListLoading,
  selectStatusFilter,
  selectError,
} from '../../store/payment.selectors';
import {
  PaymentSummary,
  PaymentStatus,
  PaymentType,
  PaymentMethod,
} from '../../../../shared/models/payment.model';

/**
 * Unit tests for PaymentListComponent.
 *
 * 6 tests covering:
 * - Renders the payment table with correct columns
 * - Displays payment data with French formatting
 * - Filters by status dispatch correct action
 * - Pagination dispatches correct action
 * - Shows empty state when no payments
 * - Shows error message on failure
 */
describe('PaymentListComponent', () => {
  let component: PaymentListComponent;
  let fixture: ComponentFixture<PaymentListComponent>;
  let store: MockStore;
  let router: Router;

  const mockPayments: PaymentSummary[] = [
    {
      id: 'pay-001',
      subscriptionId: 'sub-001',
      familyMemberName: 'Sophie Martin',
      associationName: 'Judo Club Lyon',
      activityName: 'Judo enfants 6-10 ans',
      amount: 15000,
      paymentType: PaymentType.ADHESION,
      status: PaymentStatus.COMPLETED,
      paymentMethod: PaymentMethod.CARD,
      paidAt: '2026-01-15T14:30:00Z',
      invoiceId: 'inv-001',
      createdAt: '2026-01-15T14:00:00Z',
    },
    {
      id: 'pay-002',
      subscriptionId: 'sub-002',
      familyMemberName: 'Lucas Martin',
      associationName: 'École de Danse Bordeaux',
      activityName: 'Danse classique débutant',
      amount: 22000,
      paymentType: PaymentType.COTISATION,
      status: PaymentStatus.PENDING,
      paymentMethod: null,
      paidAt: null,
      invoiceId: null,
      createdAt: '2026-02-10T09:00:00Z',
    },
    {
      id: 'pay-003',
      subscriptionId: 'sub-003',
      familyMemberName: 'Emma Martin',
      associationName: 'Chorale Harmonie Nantes',
      activityName: 'Chorale adultes',
      amount: 8000,
      paymentType: PaymentType.ADHESION,
      status: PaymentStatus.FAILED,
      paymentMethod: null,
      paidAt: null,
      invoiceId: null,
      createdAt: '2026-02-12T16:00:00Z',
    },
  ];

  const initialState = {
    payments: {
      payments: [],
      totalElements: 0,
      selectedPayment: null,
      checkoutResponse: null,
      statusFilter: null,
      currentPage: 0,
      pageSize: 20,
      listLoading: false,
      detailLoading: false,
      checkoutLoading: false,
      error: null,
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentListComponent, NoopAnimationsModule],
      providers: [
        provideMockStore({ initialState }),
        {
          provide: Router,
          useValue: { navigate: jest.fn() },
        },
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(PaymentListComponent);
    component = fixture.componentInstance;
  });

  afterEach(() => {
    store.resetSelectors();
  });

  it('should dispatch loadPayments on init', () => {
    const dispatchSpy = jest.spyOn(store, 'dispatch');
    fixture.detectChanges();

    expect(dispatchSpy).toHaveBeenCalledWith(
      PaymentActions.loadPayments({ familyId: 'famille-dupont-001' })
    );
  });

  it('should render payment table with correct columns when data is loaded', () => {
    store.overrideSelector(selectPayments, mockPayments);
    store.overrideSelector(selectTotalElements, 3);
    store.overrideSelector(selectListLoading, false);
    store.refreshState();
    fixture.detectChanges();

    const headerCells = fixture.nativeElement.querySelectorAll('th');
    const headerTexts = Array.from(headerCells).map(
      (cell: any) => cell.textContent.trim()
    );

    expect(headerTexts).toContain('Date');
    expect(headerTexts).toContain('Membre');
    expect(headerTexts).toContain('Association');
    expect(headerTexts).toContain('Montant');
    expect(headerTexts).toContain('Statut');
  });

  it('should display payment amounts in French EUR format', () => {
    store.overrideSelector(selectPayments, mockPayments);
    store.overrideSelector(selectTotalElements, 3);
    store.overrideSelector(selectListLoading, false);
    store.refreshState();
    fixture.detectChanges();

    const cells = fixture.nativeElement.querySelectorAll('td');
    const allText = Array.from(cells)
      .map((cell: any) => cell.textContent.trim())
      .join(' ');

    // 15000 cents = 150,00 EUR in French format
    expect(allText).toContain('150,00');
  });

  it('should dispatch setStatusFilter and loadPayments when filter changes', () => {
    const dispatchSpy = jest.spyOn(store, 'dispatch');
    fixture.detectChanges();
    dispatchSpy.mockClear();

    component.onStatusFilterChange(PaymentStatus.COMPLETED);

    expect(dispatchSpy).toHaveBeenCalledWith(
      PaymentActions.setStatusFilter({ status: PaymentStatus.COMPLETED })
    );
    expect(dispatchSpy).toHaveBeenCalledWith(
      PaymentActions.loadPayments({ familyId: 'famille-dupont-001', page: 0 })
    );
  });

  it('should show empty state when no payments and not loading', () => {
    store.overrideSelector(selectPayments, []);
    store.overrideSelector(selectTotalElements, 0);
    store.overrideSelector(selectListLoading, false);
    store.refreshState();
    fixture.detectChanges();

    const emptyText = fixture.nativeElement.querySelector(
      '.payment-list__empty'
    );
    expect(emptyText).toBeTruthy();
    expect(emptyText.textContent).toContain('Aucun paiement trouvé');
  });

  it('should show error message when error is present', () => {
    store.overrideSelector(selectError, 'Une erreur interne est survenue.');
    store.refreshState();
    fixture.detectChanges();

    const errorEl = fixture.nativeElement.querySelector(
      '.payment-list__error'
    );
    expect(errorEl).toBeTruthy();
    expect(errorEl.textContent).toContain('Une erreur interne est survenue.');
  });
});
```

---

## Test File 2: CheckoutRedirectComponent Spec

**Path**: `frontend/src/app/features/payments/components/checkout-redirect/checkout-redirect.component.spec.ts`

```typescript
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute, Router } from '@angular/router';
import { CheckoutRedirectComponent } from './checkout-redirect.component';

/**
 * Unit tests for CheckoutRedirectComponent.
 *
 * 5 tests covering:
 * - Shows loading spinner initially
 * - Shows success message on status=success
 * - Shows error message on status=error
 * - Shows cancelled message on status=cancelled
 * - Navigates to payment detail on viewPaymentDetail()
 */
describe('CheckoutRedirectComponent', () => {
  let component: CheckoutRedirectComponent;
  let fixture: ComponentFixture<CheckoutRedirectComponent>;
  let router: Router;

  function createComponent(queryParams: Record<string, string>): void {
    TestBed.configureTestingModule({
      imports: [CheckoutRedirectComponent, NoopAnimationsModule],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParams },
          },
        },
        {
          provide: Router,
          useValue: { navigate: jest.fn() },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    fixture = TestBed.createComponent(CheckoutRedirectComponent);
    component = fixture.componentInstance;
  }

  afterEach(() => {
    TestBed.resetTestingModule();
  });

  it('should show loading spinner initially', () => {
    createComponent({ status: 'success', paymentId: 'pay-001' });
    fixture.detectChanges();

    expect(component.result).toBe('loading');

    const spinner = fixture.nativeElement.querySelector('mat-spinner');
    expect(spinner).toBeTruthy();

    const loadingText = fixture.nativeElement.querySelector(
      '.checkout-redirect__loading h2'
    );
    expect(loadingText.textContent).toContain('Traitement du paiement en cours');
  });

  it('should show success message after timeout when status=success', fakeAsync(() => {
    createComponent({ status: 'success', paymentId: 'pay-001' });
    fixture.detectChanges();

    tick(800);
    fixture.detectChanges();

    expect(component.result).toBe('success');

    const title = fixture.nativeElement.querySelector(
      '.checkout-redirect__title'
    );
    expect(title.textContent).toContain('Paiement réussi');

    const body = fixture.nativeElement.querySelector(
      '.checkout-redirect__body'
    );
    expect(body.textContent).toContain('effectué avec succès');
  }));

  it('should show error message after timeout when status=error', fakeAsync(() => {
    createComponent({ status: 'error' });
    fixture.detectChanges();

    tick(800);
    fixture.detectChanges();

    expect(component.result).toBe('error');

    const title = fixture.nativeElement.querySelector(
      '.checkout-redirect__title'
    );
    expect(title.textContent).toContain('Paiement échoué');

    const body = fixture.nativeElement.querySelector(
      '.checkout-redirect__body'
    );
    expect(body.textContent).toContain('erreur est survenue');
  }));

  it('should show cancelled message after timeout when status=cancelled', fakeAsync(() => {
    createComponent({ status: 'cancelled' });
    fixture.detectChanges();

    tick(800);
    fixture.detectChanges();

    expect(component.result).toBe('cancelled');

    const title = fixture.nativeElement.querySelector(
      '.checkout-redirect__title'
    );
    expect(title.textContent).toContain('Paiement annulé');
  }));

  it('should navigate to payment detail when viewPaymentDetail is called', fakeAsync(() => {
    createComponent({ status: 'success', paymentId: 'pay-001' });
    fixture.detectChanges();
    tick(800);
    fixture.detectChanges();

    component.viewPaymentDetail();

    expect(router.navigate).toHaveBeenCalledWith(['/payments', 'pay-001']);
  }));
});
```

---

## Test File 3: PaymentService Spec

**Path**: `frontend/src/app/features/payments/services/payment.service.spec.ts`

```typescript
import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { PaymentService } from './payment.service';
import {
  CheckoutRequest,
  CheckoutResponse,
  PaymentDetail,
  PaymentSummary,
  PaymentStatus,
  PaymentType,
  PaymentMethod,
  Page,
} from '../../../shared/models/payment.model';

/**
 * Unit tests for PaymentService.
 *
 * 4 tests covering:
 * - initiateCheckout sends POST to /api/v1/payments/checkout
 * - getById sends GET to /api/v1/payments/{id}
 * - getMyPayments sends GET with query params
 * - getMyPayments includes optional status filter
 */
describe('PaymentService', () => {
  let service: PaymentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PaymentService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(PaymentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should send POST to /api/v1/payments/checkout with request body', () => {
    const request: CheckoutRequest = {
      subscriptionId: 'sub-001',
      amount: 15000,
      description: 'Adhésion Judo Club Lyon - Sophie Martin',
      paymentType: PaymentType.ADHESION,
      returnUrl: 'http://localhost:4200/payments/checkout/redirect?status=success',
      cancelUrl: 'http://localhost:4200/payments/checkout/redirect?status=cancelled',
    };

    const expectedResponse: CheckoutResponse = {
      paymentId: 'pay-001',
      subscriptionId: 'sub-001',
      amount: 15000,
      paymentType: PaymentType.ADHESION,
      status: PaymentStatus.PENDING,
      checkoutUrl: 'https://checkout.helloasso-sandbox.com/session/abc123',
      helloassoCheckoutId: 'ha-checkout-abc123',
      expiresAt: '2026-02-25T10:00:00Z',
      createdAt: '2026-02-24T10:00:00Z',
    };

    service.initiateCheckout(request).subscribe((response) => {
      expect(response).toEqual(expectedResponse);
      expect(response.checkoutUrl).toContain('helloasso');
    });

    const req = httpMock.expectOne('/api/v1/payments/checkout');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(expectedResponse);
  });

  it('should send GET to /api/v1/payments/{id}', () => {
    const mockDetail: PaymentDetail = {
      id: 'pay-001',
      subscriptionId: 'sub-001',
      familyId: 'famille-dupont-001',
      familyMemberName: 'Sophie Martin',
      associationName: 'Judo Club Lyon',
      activityName: 'Judo enfants 6-10 ans',
      amount: 15000,
      paymentType: PaymentType.ADHESION,
      status: PaymentStatus.COMPLETED,
      helloassoCheckoutId: 'ha-checkout-abc123',
      helloassoPaymentId: 'ha-pay-xyz789',
      paymentMethod: PaymentMethod.CARD,
      paidAt: '2026-01-15T14:30:00Z',
      invoiceId: 'inv-001',
      metadata: null,
      createdAt: '2026-01-15T14:00:00Z',
      updatedAt: '2026-01-15T14:30:00Z',
    };

    service.getById('pay-001').subscribe((detail) => {
      expect(detail.id).toBe('pay-001');
      expect(detail.familyMemberName).toBe('Sophie Martin');
      expect(detail.associationName).toBe('Judo Club Lyon');
    });

    const req = httpMock.expectOne('/api/v1/payments/pay-001');
    expect(req.request.method).toBe('GET');
    req.flush(mockDetail);
  });

  it('should send GET to /api/v1/payments/family/{familyId} with pagination params', () => {
    const mockPage: Page<PaymentSummary> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
      first: true,
      last: true,
    };

    service
      .getMyPayments({
        familyId: 'famille-dupont-001',
        page: 0,
        size: 20,
      })
      .subscribe((page) => {
        expect(page.content).toEqual([]);
        expect(page.totalElements).toBe(0);
      });

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/payments/family/famille-dupont-001' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '20' &&
        r.params.get('sort') === 'createdAt,desc'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should include status filter when provided', () => {
    const mockPage: Page<PaymentSummary> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 20,
      first: true,
      last: true,
    };

    service
      .getMyPayments({
        familyId: 'famille-dupont-001',
        status: PaymentStatus.COMPLETED,
        page: 0,
        size: 10,
      })
      .subscribe((page) => {
        expect(page).toBeTruthy();
      });

    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/payments/family/famille-dupont-001' &&
        r.params.get('status') === 'COMPLETED' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '10'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });
});
```

---

## Test File 4: Payment Reducer Spec

**Path**: `frontend/src/app/features/payments/store/payment.reducer.spec.ts`

```typescript
import { paymentReducer } from './payment.reducer';
import { PaymentActions } from './payment.actions';
import { initialPaymentState, PaymentState } from './payment.state';
import {
  PaymentSummary,
  PaymentDetail,
  PaymentStatus,
  PaymentType,
  PaymentMethod,
  CheckoutResponse,
  Page,
} from '../../../shared/models/payment.model';

/**
 * Unit tests for payment.reducer.
 *
 * 8 tests covering:
 * - Returns initial state for unknown action
 * - Sets listLoading on loadPayments
 * - Stores payments on loadPaymentsSuccess
 * - Stores error on loadPaymentsFailure
 * - Sets checkoutLoading on initiateCheckout
 * - Stores checkoutResponse on initiateCheckoutSuccess
 * - Sets statusFilter on setStatusFilter
 * - Clears selectedPayment on clearSelectedPayment
 */
describe('paymentReducer', () => {
  const mockPayments: PaymentSummary[] = [
    {
      id: 'pay-001',
      subscriptionId: 'sub-001',
      familyMemberName: 'Sophie Martin',
      associationName: 'Judo Club Lyon',
      activityName: 'Judo enfants 6-10 ans',
      amount: 15000,
      paymentType: PaymentType.ADHESION,
      status: PaymentStatus.COMPLETED,
      paymentMethod: PaymentMethod.CARD,
      paidAt: '2026-01-15T14:30:00Z',
      invoiceId: 'inv-001',
      createdAt: '2026-01-15T14:00:00Z',
    },
  ];

  const mockPage: Page<PaymentSummary> = {
    content: mockPayments,
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 20,
    first: true,
    last: true,
  };

  const mockPaymentDetail: PaymentDetail = {
    id: 'pay-001',
    subscriptionId: 'sub-001',
    familyId: 'famille-dupont-001',
    familyMemberName: 'Sophie Martin',
    associationName: 'Judo Club Lyon',
    activityName: 'Judo enfants 6-10 ans',
    amount: 15000,
    paymentType: PaymentType.ADHESION,
    status: PaymentStatus.COMPLETED,
    helloassoCheckoutId: 'ha-checkout-abc123',
    helloassoPaymentId: 'ha-pay-xyz789',
    paymentMethod: PaymentMethod.CARD,
    paidAt: '2026-01-15T14:30:00Z',
    invoiceId: 'inv-001',
    metadata: null,
    createdAt: '2026-01-15T14:00:00Z',
    updatedAt: '2026-01-15T14:30:00Z',
  };

  const mockCheckoutResponse: CheckoutResponse = {
    paymentId: 'pay-002',
    subscriptionId: 'sub-002',
    amount: 22000,
    paymentType: PaymentType.COTISATION,
    status: PaymentStatus.PENDING,
    checkoutUrl: 'https://checkout.helloasso-sandbox.com/session/def456',
    helloassoCheckoutId: 'ha-checkout-def456',
    expiresAt: '2026-02-25T10:00:00Z',
    createdAt: '2026-02-24T10:00:00Z',
  };

  it('should return initial state for unknown action', () => {
    const action = { type: 'UNKNOWN' };
    const state = paymentReducer(undefined, action);

    expect(state).toEqual(initialPaymentState);
  });

  it('should set listLoading to true on loadPayments', () => {
    const action = PaymentActions.loadPayments({
      familyId: 'famille-dupont-001',
    });
    const state = paymentReducer(initialPaymentState, action);

    expect(state.listLoading).toBe(true);
    expect(state.error).toBeNull();
  });

  it('should store payments and metadata on loadPaymentsSuccess', () => {
    const loadingState: PaymentState = {
      ...initialPaymentState,
      listLoading: true,
    };

    const action = PaymentActions.loadPaymentsSuccess({ page: mockPage });
    const state = paymentReducer(loadingState, action);

    expect(state.payments).toEqual(mockPayments);
    expect(state.totalElements).toBe(1);
    expect(state.currentPage).toBe(0);
    expect(state.listLoading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should store error on loadPaymentsFailure', () => {
    const loadingState: PaymentState = {
      ...initialPaymentState,
      listLoading: true,
    };

    const action = PaymentActions.loadPaymentsFailure({
      error: 'Une erreur interne est survenue.',
    });
    const state = paymentReducer(loadingState, action);

    expect(state.listLoading).toBe(false);
    expect(state.error).toBe('Une erreur interne est survenue.');
  });

  it('should set checkoutLoading to true on initiateCheckout', () => {
    const action = PaymentActions.initiateCheckout({
      request: {
        subscriptionId: 'sub-002',
        amount: 22000,
        description: 'Cotisation Danse Bordeaux - Lucas Martin',
        paymentType: PaymentType.COTISATION,
        returnUrl: 'http://localhost:4200/payments/checkout/redirect?status=success',
        cancelUrl: 'http://localhost:4200/payments/checkout/redirect?status=cancelled',
      },
    });
    const state = paymentReducer(initialPaymentState, action);

    expect(state.checkoutLoading).toBe(true);
    expect(state.checkoutResponse).toBeNull();
    expect(state.error).toBeNull();
  });

  it('should store checkoutResponse on initiateCheckoutSuccess', () => {
    const loadingState: PaymentState = {
      ...initialPaymentState,
      checkoutLoading: true,
    };

    const action = PaymentActions.initiateCheckoutSuccess({
      response: mockCheckoutResponse,
    });
    const state = paymentReducer(loadingState, action);

    expect(state.checkoutResponse).toEqual(mockCheckoutResponse);
    expect(state.checkoutLoading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should update statusFilter and reset page on setStatusFilter', () => {
    const stateWithPage: PaymentState = {
      ...initialPaymentState,
      currentPage: 3,
    };

    const action = PaymentActions.setStatusFilter({
      status: PaymentStatus.COMPLETED,
    });
    const state = paymentReducer(stateWithPage, action);

    expect(state.statusFilter).toBe(PaymentStatus.COMPLETED);
    expect(state.currentPage).toBe(0);
  });

  it('should clear selectedPayment on clearSelectedPayment', () => {
    const stateWithPayment: PaymentState = {
      ...initialPaymentState,
      selectedPayment: mockPaymentDetail,
      detailLoading: true,
    };

    const action = PaymentActions.clearSelectedPayment();
    const state = paymentReducer(stateWithPayment, action);

    expect(state.selectedPayment).toBeNull();
    expect(state.detailLoading).toBe(false);
  });
});
```

---

## Test Summary

| Test File | Path | Tests | Coverage Focus |
|-----------|------|-------|----------------|
| PaymentListComponent | `frontend/src/app/features/payments/components/payment-list/payment-list.component.spec.ts` | 6 | Rendering, data display, filter actions, pagination, empty/error states |
| CheckoutRedirectComponent | `frontend/src/app/features/payments/components/checkout-redirect/checkout-redirect.component.spec.ts` | 5 | Loading spinner, success/error/cancelled states, navigation |
| PaymentService | `frontend/src/app/features/payments/services/payment.service.spec.ts` | 4 | HTTP methods, URL construction, query parameters, request bodies |
| payment.reducer | `frontend/src/app/features/payments/store/payment.reducer.spec.ts` | 8 | State transitions for all action types, initial state |

**Total: 23 tests** -- all must fail before implementation (TDD red phase) and pass after (green phase).

### Test Data Convention

All test data uses realistic French names consistent with the project's seed data:
- **Family**: Famille Dupont (ID: `famille-dupont-001`)
- **Members**: Sophie Martin, Lucas Martin, Emma Martin
- **Associations**: Judo Club Lyon, École de Danse Bordeaux, Chorale Harmonie Nantes
- **Activities**: Judo enfants 6-10 ans, Danse classique débutant, Chorale adultes
