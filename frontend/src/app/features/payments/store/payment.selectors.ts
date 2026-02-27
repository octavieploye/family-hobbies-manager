// frontend/src/app/features/payments/store/payment.selectors.ts
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { PaymentState } from './payment.state';

/**
 * Feature selector for the 'payments' state slice.
 * The key 'payments' must match the key used in provideState().
 */
export const selectPaymentState = createFeatureSelector<PaymentState>('payments');

/** Select the list of payment summaries. */
export const selectPayments = createSelector(
  selectPaymentState,
  (state) => state.payments
);

/** Select the currently loaded payment detail. */
export const selectSelectedPayment = createSelector(
  selectPaymentState,
  (state) => state.selectedPayment
);

/** Select the checkout response. */
export const selectCheckoutResponse = createSelector(
  selectPaymentState,
  (state) => state.checkoutResponse
);

/** Select total number of payment records (across all pages). */
export const selectTotalElements = createSelector(
  selectPaymentState,
  (state) => state.totalElements
);

/** Select total number of pages. */
export const selectTotalPages = createSelector(
  selectPaymentState,
  (state) => state.totalPages
);

/** Select the current page index (0-based). */
export const selectCurrentPage = createSelector(
  selectPaymentState,
  (state) => state.currentPage
);

/** Select the page size. */
export const selectPageSize = createSelector(
  selectPaymentState,
  (state) => state.pageSize
);

/** Select the current status filter. */
export const selectStatusFilter = createSelector(
  selectPaymentState,
  (state) => state.statusFilter
);

/** Select whether the list is loading. */
export const selectListLoading = createSelector(
  selectPaymentState,
  (state) => state.listLoading
);

/** Select whether a detail is loading. */
export const selectDetailLoading = createSelector(
  selectPaymentState,
  (state) => state.detailLoading
);

/** Select whether checkout is in progress. */
export const selectCheckoutLoading = createSelector(
  selectPaymentState,
  (state) => state.checkoutLoading
);

/** Select the current error message, if any. */
export const selectPaymentError = createSelector(
  selectPaymentState,
  (state) => state.error
);

/**
 * Composite selector for pagination state.
 * Useful for binding directly to MatPaginator.
 */
export const selectPagination = createSelector(
  selectTotalElements,
  selectCurrentPage,
  selectPageSize,
  (totalElements, currentPage, pageSize) => ({
    totalElements,
    currentPage,
    pageSize,
  })
);
