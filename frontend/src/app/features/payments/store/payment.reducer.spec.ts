// frontend/src/app/features/payments/store/payment.reducer.spec.ts
import { paymentReducer } from './payment.reducer';
import { PaymentState, initialPaymentState } from './payment.state';
import { PaymentActions } from './payment.actions';
import {
  CheckoutResponse,
  Page,
  PaymentDetail,
  PaymentStatus,
  PaymentSummary,
} from '@shared/models/payment.model';

describe('PaymentReducer', () => {
  const mockPayment: PaymentSummary = {
    id: 1,
    subscriptionId: 10,
    familyMemberName: 'Jean Dupont',
    associationName: 'Club Sportif Paris',
    activityName: 'Football',
    amount: 150,
    paymentType: 'ADHESION',
    status: PaymentStatus.COMPLETED,
    paymentMethod: null,
    paidAt: '2026-02-27T12:00:00',
    invoiceId: null,
    createdAt: '2026-02-27T10:00:00',
  };

  const mockPage: Page<PaymentSummary> = {
    content: [mockPayment],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 10,
    first: true,
    last: true,
  };

  const mockCheckoutResponse: CheckoutResponse = {
    paymentId: 42,
    subscriptionId: 1,
    amount: 150,
    paymentType: 'ADHESION',
    status: PaymentStatus.PENDING,
    checkoutUrl: 'https://checkout.helloasso-sandbox.com/abc123',
    helloassoCheckoutId: 'abc123',
    expiresAt: '2026-03-01T10:00:00',
    createdAt: '2026-02-27T10:00:00',
  };

  it('should return the initial state when action is undefined', () => {
    const action = { type: 'UNKNOWN' } as any;
    const state = paymentReducer(undefined, action);
    expect(state).toEqual(initialPaymentState);
  });

  it('should set listLoading to true when loadPayments is dispatched', () => {
    const action = PaymentActions.loadPayments({
      params: { familyId: 1, page: 0, size: 10 },
    });
    const state = paymentReducer(initialPaymentState, action);

    expect(state.listLoading).toBe(true);
    expect(state.error).toBeNull();
  });

  it('should store payments when loadPaymentsSuccess is dispatched', () => {
    const action = PaymentActions.loadPaymentsSuccess({ page: mockPage });
    const state = paymentReducer(
      { ...initialPaymentState, listLoading: true },
      action
    );

    expect(state.payments).toEqual([mockPayment]);
    expect(state.totalElements).toBe(1);
    expect(state.totalPages).toBe(1);
    expect(state.currentPage).toBe(0);
    expect(state.pageSize).toBe(10);
    expect(state.listLoading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should store error when loadPaymentsFailure is dispatched', () => {
    const action = PaymentActions.loadPaymentsFailure({
      error: 'Erreur reseau',
    });
    const state = paymentReducer(
      { ...initialPaymentState, listLoading: true },
      action
    );

    expect(state.listLoading).toBe(false);
    expect(state.error).toBe('Erreur reseau');
  });

  it('should set checkoutLoading to true when initiateCheckout is dispatched', () => {
    const action = PaymentActions.initiateCheckout({
      request: {
        subscriptionId: 1,
        amount: 150,
        description: 'Test',
        paymentType: 'ADHESION' as any,
        returnUrl: 'http://localhost',
        cancelUrl: 'http://localhost',
      },
    });
    const state = paymentReducer(initialPaymentState, action);

    expect(state.checkoutLoading).toBe(true);
    expect(state.error).toBeNull();
  });

  it('should store checkout response when initiateCheckoutSuccess is dispatched', () => {
    const action = PaymentActions.initiateCheckoutSuccess({
      response: mockCheckoutResponse,
    });
    const state = paymentReducer(
      { ...initialPaymentState, checkoutLoading: true },
      action
    );

    expect(state.checkoutResponse).toEqual(mockCheckoutResponse);
    expect(state.checkoutLoading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should update status filter when setStatusFilter is dispatched', () => {
    const action = PaymentActions.setStatusFilter({
      status: PaymentStatus.COMPLETED,
    });
    const state = paymentReducer(initialPaymentState, action);

    expect(state.statusFilter).toBe(PaymentStatus.COMPLETED);
  });

  it('should clear selected payment when clearSelectedPayment is dispatched', () => {
    const stateWithPayment: PaymentState = {
      ...initialPaymentState,
      selectedPayment: {
        id: 1,
        subscriptionId: 10,
        familyMemberName: 'Jean Dupont',
        associationName: 'Club Sportif Paris',
        activityName: 'Football',
        amount: 150,
        paymentType: 'ADHESION',
        status: PaymentStatus.COMPLETED,
        paymentMethod: null,
        paidAt: '2026-02-27T12:00:00',
        invoiceId: null,
        createdAt: '2026-02-27T10:00:00',
        familyId: 1,
        helloassoPaymentId: 'ha_123',
        currency: 'EUR',
        updatedAt: '2026-02-27T12:00:00',
      },
    };
    const action = PaymentActions.clearSelectedPayment();
    const state = paymentReducer(stateWithPayment, action);

    expect(state.selectedPayment).toBeNull();
  });
});
