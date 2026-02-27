// frontend/src/app/features/payments/store/payment.actions.ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import {
  CheckoutRequest,
  CheckoutResponse,
  Page,
  PaymentDetail,
  PaymentListParams,
  PaymentStatus,
  PaymentSummary,
} from '@shared/models/payment.model';

/**
 * NgRx action group for the Payments feature.
 *
 * Follows the [Source] Event naming convention.
 * Each async operation has a triplet: trigger / success / failure.
 */
export const PaymentActions = createActionGroup({
  source: 'Payments',
  events: {
    // --- List ---
    'Load Payments': props<{ params: PaymentListParams }>(),
    'Load Payments Success': props<{ page: Page<PaymentSummary> }>(),
    'Load Payments Failure': props<{ error: string }>(),

    // --- Detail ---
    'Load Payment Detail': props<{ paymentId: number }>(),
    'Load Payment Detail Success': props<{ payment: PaymentDetail }>(),
    'Load Payment Detail Failure': props<{ error: string }>(),

    // --- Checkout ---
    'Initiate Checkout': props<{ request: CheckoutRequest }>(),
    'Initiate Checkout Success': props<{ response: CheckoutResponse }>(),
    'Initiate Checkout Failure': props<{ error: string }>(),

    // --- Filters & Clear ---
    'Set Status Filter': props<{ status: PaymentStatus | null }>(),
    'Clear Selected Payment': emptyProps(),
    'Clear Checkout Response': emptyProps(),
    'Clear Error': emptyProps(),
  },
});
