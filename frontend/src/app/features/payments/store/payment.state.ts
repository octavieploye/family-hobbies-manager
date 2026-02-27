// frontend/src/app/features/payments/store/payment.state.ts
import {
  CheckoutResponse,
  Page,
  PaymentDetail,
  PaymentStatus,
  PaymentSummary,
} from '@shared/models/payment.model';

/**
 * State shape for the Payments feature store.
 */
export interface PaymentState {
  payments: PaymentSummary[];
  selectedPayment: PaymentDetail | null;
  checkoutResponse: CheckoutResponse | null;
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  statusFilter: PaymentStatus | null;
  listLoading: boolean;
  detailLoading: boolean;
  checkoutLoading: boolean;
  error: string | null;
}

/**
 * Initial state with sensible defaults.
 */
export const initialPaymentState: PaymentState = {
  payments: [],
  selectedPayment: null,
  checkoutResponse: null,
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
  pageSize: 10,
  statusFilter: null,
  listLoading: false,
  detailLoading: false,
  checkoutLoading: false,
  error: null,
};
