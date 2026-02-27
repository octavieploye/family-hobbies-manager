// frontend/src/app/features/payments/store/payment.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { PaymentActions } from './payment.actions';
import { PaymentState, initialPaymentState } from './payment.state';

/**
 * Payments feature reducer.
 * Handles list loading, detail loading, checkout, filters, and state clearing.
 */
export const paymentReducer = createReducer(
  initialPaymentState,

  // --- Load Payments ---
  on(PaymentActions.loadPayments, (state) => ({
    ...state,
    listLoading: true,
    error: null,
  })),

  on(PaymentActions.loadPaymentsSuccess, (state, { page }) => ({
    ...state,
    payments: page.content,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
    currentPage: page.number,
    pageSize: page.size,
    listLoading: false,
    error: null,
  })),

  on(PaymentActions.loadPaymentsFailure, (state, { error }) => ({
    ...state,
    listLoading: false,
    error,
  })),

  // --- Load Payment Detail ---
  on(PaymentActions.loadPaymentDetail, (state) => ({
    ...state,
    detailLoading: true,
    error: null,
  })),

  on(PaymentActions.loadPaymentDetailSuccess, (state, { payment }) => ({
    ...state,
    selectedPayment: payment,
    detailLoading: false,
    error: null,
  })),

  on(PaymentActions.loadPaymentDetailFailure, (state, { error }) => ({
    ...state,
    detailLoading: false,
    error,
  })),

  // --- Checkout ---
  on(PaymentActions.initiateCheckout, (state) => ({
    ...state,
    checkoutLoading: true,
    error: null,
  })),

  on(PaymentActions.initiateCheckoutSuccess, (state, { response }) => ({
    ...state,
    checkoutResponse: response,
    checkoutLoading: false,
    error: null,
  })),

  on(PaymentActions.initiateCheckoutFailure, (state, { error }) => ({
    ...state,
    checkoutLoading: false,
    error,
  })),

  // --- Filters ---
  on(PaymentActions.setStatusFilter, (state, { status }) => ({
    ...state,
    statusFilter: status,
  })),

  // --- Clear ---
  on(PaymentActions.clearSelectedPayment, (state) => ({
    ...state,
    selectedPayment: null,
  })),

  on(PaymentActions.clearCheckoutResponse, (state) => ({
    ...state,
    checkoutResponse: null,
  })),

  on(PaymentActions.clearError, (state) => ({
    ...state,
    error: null,
  }))
);
