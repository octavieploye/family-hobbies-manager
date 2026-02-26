# Story S5-006: Implement Angular Payment Feature

> 5 points | Priority: P1 | Service: frontend
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

The Angular payment feature is the user-facing layer for the entire HelloAsso payment integration built in S5-004 and S5-005. Families browsing the platform need to view their payment history, initiate checkout sessions that redirect to HelloAsso, and handle the return flow after payment completion or failure. This story delivers three standalone Angular 17+ components (`PaymentListComponent`, `PaymentDetailComponent`, `CheckoutRedirectComponent`), a `PaymentService` for HTTP communication with the payment-service backend, and a full NgRx store (`actions`, `reducer`, `effects`, `selectors`, `state`) for centralized payment state management. The checkout flow works as follows: the frontend calls `POST /api/v1/payments/checkout` to get a checkout URL, redirects the user to HelloAsso, and when HelloAsso redirects back to `/payments/checkout/redirect?status=success|error`, the `CheckoutRedirectComponent` displays the appropriate result. Payment statuses are rendered as color-coded Angular Material chips (PENDING=yellow, AUTHORIZED=blue, COMPLETED=green, FAILED=red, REFUNDED=grey). All HTTP errors flow through the project's `error.interceptor.ts` which parses structured `ApiError` responses and displays French toast notifications via `MatSnackBar`. Jest tests validate component rendering, filter logic, return parameter handling, HTTP calls, and reducer state transitions.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Payment model + enums | `frontend/src/app/shared/models/payment.model.ts` | TypeScript interfaces and enums matching backend DTOs | `ng build` compiles |
| 2 | PaymentService | `frontend/src/app/features/payments/services/payment.service.ts` | HttpClient service: initiateCheckout, getMyPayments, getById | `ng test --include=**/payment.service.spec.ts` |
| 3 | NgRx state interface | `frontend/src/app/features/payments/store/payment.state.ts` | PaymentState interface and initialState | `ng build` compiles |
| 4 | NgRx actions | `frontend/src/app/features/payments/store/payment.actions.ts` | Action creators for load, checkout, success, failure | `ng build` compiles |
| 5 | NgRx reducer | `frontend/src/app/features/payments/store/payment.reducer.ts` | Reducer handling all payment actions | `ng test --include=**/payment.reducer.spec.ts` |
| 6 | NgRx effects | `frontend/src/app/features/payments/store/payment.effects.ts` | Side effects for API calls | `ng build` compiles |
| 7 | NgRx selectors | `frontend/src/app/features/payments/store/payment.selectors.ts` | Memoized selectors for components | `ng build` compiles |
| 8 | PaymentListComponent | `frontend/src/app/features/payments/components/payment-list/` | Material table with filters, status chips | `ng test --include=**/payment-list.component.spec.ts` |
| 9 | PaymentDetailComponent | `frontend/src/app/features/payments/components/payment-detail/` | Full payment info view with status timeline | `ng test --include=**/payment-detail.component.spec.ts` |
| 10 | CheckoutRedirectComponent | `frontend/src/app/features/payments/components/checkout-redirect/` | HelloAsso return handler with spinner and result | `ng test --include=**/checkout-redirect.component.spec.ts` |
| 11 | Payments routing | `frontend/src/app/features/payments/payments.routes.ts` | Lazy-loaded routes for /payments/* | `ng build` compiles |
| 12 | SCSS styles | `frontend/src/app/features/payments/components/*/` | BEM styles for all 3 components | Visual check |
| 13 | Failing tests (TDD) | See [companion file](./S5-006-angular-payment-feature-tests.md) | Jest spec files for components, service, reducer | Tests compile, fail (TDD) |

---

## Task 1 Detail: Payment Model + Enums

- **What**: TypeScript interfaces and enums that mirror the backend Payment DTOs from S5-004
- **Where**: `frontend/src/app/shared/models/payment.model.ts`
- **Why**: Typed contracts for the HTTP layer and NgRx store; every component and service depends on these
- **Content**:

```typescript
// frontend/src/app/shared/models/payment.model.ts

/**
 * Payment status lifecycle — mirrors backend PaymentStatus enum.
 * Used by status chips and filters across the payments feature.
 */
export enum PaymentStatus {
  PENDING = 'PENDING',
  AUTHORIZED = 'AUTHORIZED',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  REFUNDED = 'REFUNDED',
  CANCELLED = 'CANCELLED',
}

/**
 * Payment method — mirrors backend PaymentMethod enum.
 */
export enum PaymentMethod {
  CARD = 'CARD',
  SEPA = 'SEPA',
  INSTALLMENT_3X = 'INSTALLMENT_3X',
  INSTALLMENT_10X = 'INSTALLMENT_10X',
}

/**
 * Payment type — matches CheckoutRequest.paymentType from S5-004.
 */
export enum PaymentType {
  ADHESION = 'ADHESION',
  COTISATION = 'COTISATION',
  DONATION = 'DONATION',
  EVENT = 'EVENT',
}

/**
 * Request body for POST /api/v1/payments/checkout.
 * Sent by the frontend to initiate a HelloAsso checkout session.
 */
export interface CheckoutRequest {
  subscriptionId: number;
  amount: number;
  description: string;
  paymentType: PaymentType;
  returnUrl: string;
  cancelUrl: string;
}

/**
 * Response from POST /api/v1/payments/checkout.
 * Contains the checkoutUrl to redirect the user to HelloAsso.
 */
export interface CheckoutResponse {
  paymentId: number;
  subscriptionId: number;
  amount: number;
  paymentType: PaymentType;
  status: PaymentStatus;
  checkoutUrl: string;
  helloassoCheckoutId: string;
  expiresAt: string;
  createdAt: string;
}

/**
 * Summary DTO returned by GET /api/v1/payments/family/{familyId}.
 * Used in the payment list table.
 */
export interface PaymentSummary {
  id: number;
  subscriptionId: number;
  familyMemberName: string;
  associationName: string;
  activityName: string;
  amount: number;
  paymentType: PaymentType;
  status: PaymentStatus;
  paymentMethod: PaymentMethod | null;
  paidAt: string | null;
  invoiceId: number | null;
  createdAt: string;
}

/**
 * Full payment detail returned by GET /api/v1/payments/{id}.
 * Used in the payment detail view.
 */
export interface PaymentDetail {
  id: number;
  subscriptionId: number;
  familyId: number;
  familyMemberName: string;
  associationName: string;
  activityName: string;
  amount: number;
  paymentType: PaymentType;
  status: PaymentStatus;
  helloassoCheckoutId: string | null;
  helloassoPaymentId: string | null;
  paymentMethod: PaymentMethod | null;
  paidAt: string | null;
  invoiceId: number | null;
  metadata: Record<string, string> | null;
  createdAt: string;
  updatedAt: string;
}

/**
 * Spring Page wrapper for paginated responses.
 */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

/**
 * Query parameters for the payment list endpoint.
 */
export interface PaymentListParams {
  familyId: number;
  status?: PaymentStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/**
 * Maps PaymentStatus to display properties (color + French label).
 */
export const PAYMENT_STATUS_CONFIG: Record<PaymentStatus, { color: string; label: string }> = {
  [PaymentStatus.PENDING]: { color: 'warn', label: 'En attente' },
  [PaymentStatus.AUTHORIZED]: { color: 'primary', label: 'Autorisé' },
  [PaymentStatus.COMPLETED]: { color: 'accent', label: 'Terminé' },
  [PaymentStatus.FAILED]: { color: 'warn', label: 'Échoué' },
  [PaymentStatus.REFUNDED]: { color: '', label: 'Remboursé' },
  [PaymentStatus.CANCELLED]: { color: '', label: 'Annulé' },
};
```

- **Verify**: `cd frontend && npx ng build` -> compiles with no errors

---

## Task 2 Detail: PaymentService

- **What**: Angular HTTP service encapsulating all payment-service REST API calls
- **Where**: `frontend/src/app/features/payments/services/payment.service.ts`
- **Why**: Single source of truth for HTTP communication with the payment-service backend; consumed by NgRx effects
- **Content**:

```typescript
// frontend/src/app/features/payments/services/payment.service.ts

import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CheckoutRequest,
  CheckoutResponse,
  PaymentDetail,
  PaymentSummary,
  PaymentListParams,
  Page,
} from '../../../shared/models/payment.model';

@Injectable({
  providedIn: 'root',
})
export class PaymentService {
  private readonly baseUrl = '/api/v1/payments';

  constructor(private readonly http: HttpClient) {}

  /**
   * Initiates a HelloAsso checkout session.
   * The response contains a checkoutUrl to redirect the user.
   *
   * @param request checkout parameters (subscriptionId, amount, etc.)
   * @returns CheckoutResponse with the HelloAsso checkout URL
   */
  initiateCheckout(request: CheckoutRequest): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${this.baseUrl}/checkout`, request);
  }

  /**
   * Retrieves a single payment by its ID.
   *
   * @param paymentId the payment UUID
   * @returns full payment detail including HelloAsso references
   */
  getById(paymentId: string): Observable<PaymentDetail> {
    return this.http.get<PaymentDetail>(`${this.baseUrl}/${paymentId}`);
  }

  /**
   * Retrieves paginated payments for a family with optional filters.
   *
   * @param params query parameters (familyId, status, date range, pagination)
   * @returns paginated list of payment summaries
   */
  getMyPayments(params: PaymentListParams): Observable<Page<PaymentSummary>> {
    let httpParams = new HttpParams()
      .set('page', (params.page ?? 0).toString())
      .set('size', (params.size ?? 20).toString())
      .set('sort', params.sort ?? 'createdAt,desc');

    if (params.status) {
      httpParams = httpParams.set('status', params.status);
    }
    if (params.from) {
      httpParams = httpParams.set('from', params.from);
    }
    if (params.to) {
      httpParams = httpParams.set('to', params.to);
    }

    return this.http.get<Page<PaymentSummary>>(
      `${this.baseUrl}/family/${params.familyId}`,
      { params: httpParams }
    );
  }
}
```

- **Verify**: `cd frontend && npx ng test --include='**/payment.service.spec.ts'` -> tests run (fail TDD)

---

## Task 3 Detail: NgRx State Interface

- **What**: TypeScript interface defining the shape of the payment NgRx state slice
- **Where**: `frontend/src/app/features/payments/store/payment.state.ts`
- **Why**: Central contract for the reducer, selectors, and all components reading payment state
- **Content**:

```typescript
// frontend/src/app/features/payments/store/payment.state.ts

import {
  PaymentSummary,
  PaymentDetail,
  PaymentStatus,
  CheckoutResponse,
} from '../../../shared/models/payment.model';

export interface PaymentState {
  /** Paginated list of payment summaries */
  payments: PaymentSummary[];
  /** Total number of payments (for pagination) */
  totalElements: number;
  /** Currently selected payment detail */
  selectedPayment: PaymentDetail | null;
  /** Latest checkout response (contains redirect URL) */
  checkoutResponse: CheckoutResponse | null;
  /** Active status filter */
  statusFilter: PaymentStatus | null;
  /** Current page index (0-based) */
  currentPage: number;
  /** Page size */
  pageSize: number;
  /** Loading state for list operations */
  listLoading: boolean;
  /** Loading state for detail operations */
  detailLoading: boolean;
  /** Loading state for checkout operations */
  checkoutLoading: boolean;
  /** Error message (null when no error) */
  error: string | null;
}

export const initialPaymentState: PaymentState = {
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
};
```

- **Verify**: `cd frontend && npx ng build` -> compiles with no errors

---

## Task 4 Detail: NgRx Actions

- **What**: NgRx action creators for all payment operations (load list, load detail, checkout, filter)
- **Where**: `frontend/src/app/features/payments/store/payment.actions.ts`
- **Why**: Defines the event contracts that trigger reducer state transitions and effects side effects
- **Content**:

```typescript
// frontend/src/app/features/payments/store/payment.actions.ts

import { createActionGroup, emptyProps, props } from '@ngrx/store';
import {
  PaymentSummary,
  PaymentDetail,
  PaymentStatus,
  CheckoutRequest,
  CheckoutResponse,
  Page,
} from '../../../shared/models/payment.model';

export const PaymentActions = createActionGroup({
  source: 'Payments',
  events: {
    // ── Load payment list ──────────────────────────────────────────
    'Load Payments': props<{ familyId: number; page?: number; size?: number }>(),
    'Load Payments Success': props<{ page: Page<PaymentSummary> }>(),
    'Load Payments Failure': props<{ error: string }>(),

    // ── Load single payment detail ─────────────────────────────────
    'Load Payment Detail': props<{ paymentId: string }>(),
    'Load Payment Detail Success': props<{ payment: PaymentDetail }>(),
    'Load Payment Detail Failure': props<{ error: string }>(),

    // ── Initiate checkout ──────────────────────────────────────────
    'Initiate Checkout': props<{ request: CheckoutRequest }>(),
    'Initiate Checkout Success': props<{ response: CheckoutResponse }>(),
    'Initiate Checkout Failure': props<{ error: string }>(),

    // ── Filter by status ───────────────────────────────────────────
    'Set Status Filter': props<{ status: PaymentStatus | null }>(),

    // ── Clear state ────────────────────────────────────────────────
    'Clear Selected Payment': emptyProps(),
    'Clear Checkout Response': emptyProps(),
    'Clear Error': emptyProps(),
  },
});
```

- **Verify**: `cd frontend && npx ng build` -> compiles with no errors

---

## Task 5 Detail: NgRx Reducer

- **What**: Pure reducer function handling all payment action state transitions
- **Where**: `frontend/src/app/features/payments/store/payment.reducer.ts`
- **Why**: Single source of truth for payment state mutations; tested independently of side effects
- **Content**:

```typescript
// frontend/src/app/features/payments/store/payment.reducer.ts

import { createReducer, on } from '@ngrx/store';
import { PaymentActions } from './payment.actions';
import { initialPaymentState, PaymentState } from './payment.state';

export const paymentReducer = createReducer(
  initialPaymentState,

  // ── Load Payments ──────────────────────────────────────────────────
  on(PaymentActions.loadPayments, (state, { page, size }): PaymentState => ({
    ...state,
    listLoading: true,
    error: null,
    currentPage: page ?? state.currentPage,
    pageSize: size ?? state.pageSize,
  })),

  on(PaymentActions.loadPaymentsSuccess, (state, { page }): PaymentState => ({
    ...state,
    payments: page.content,
    totalElements: page.totalElements,
    currentPage: page.number,
    listLoading: false,
    error: null,
  })),

  on(PaymentActions.loadPaymentsFailure, (state, { error }): PaymentState => ({
    ...state,
    listLoading: false,
    error,
  })),

  // ── Load Payment Detail ────────────────────────────────────────────
  on(PaymentActions.loadPaymentDetail, (state): PaymentState => ({
    ...state,
    detailLoading: true,
    selectedPayment: null,
    error: null,
  })),

  on(PaymentActions.loadPaymentDetailSuccess, (state, { payment }): PaymentState => ({
    ...state,
    selectedPayment: payment,
    detailLoading: false,
    error: null,
  })),

  on(PaymentActions.loadPaymentDetailFailure, (state, { error }): PaymentState => ({
    ...state,
    detailLoading: false,
    error,
  })),

  // ── Initiate Checkout ──────────────────────────────────────────────
  on(PaymentActions.initiateCheckout, (state): PaymentState => ({
    ...state,
    checkoutLoading: true,
    checkoutResponse: null,
    error: null,
  })),

  on(PaymentActions.initiateCheckoutSuccess, (state, { response }): PaymentState => ({
    ...state,
    checkoutResponse: response,
    checkoutLoading: false,
    error: null,
  })),

  on(PaymentActions.initiateCheckoutFailure, (state, { error }): PaymentState => ({
    ...state,
    checkoutLoading: false,
    error,
  })),

  // ── Filter ─────────────────────────────────────────────────────────
  on(PaymentActions.setStatusFilter, (state, { status }): PaymentState => ({
    ...state,
    statusFilter: status,
    currentPage: 0,
  })),

  // ── Clear ──────────────────────────────────────────────────────────
  on(PaymentActions.clearSelectedPayment, (state): PaymentState => ({
    ...state,
    selectedPayment: null,
    detailLoading: false,
  })),

  on(PaymentActions.clearCheckoutResponse, (state): PaymentState => ({
    ...state,
    checkoutResponse: null,
    checkoutLoading: false,
  })),

  on(PaymentActions.clearError, (state): PaymentState => ({
    ...state,
    error: null,
  })),
);
```

- **Verify**: `cd frontend && npx ng test --include='**/payment.reducer.spec.ts'` -> tests run (fail TDD)

---

## Task 6 Detail: NgRx Effects

- **What**: Side effects that call `PaymentService` methods in response to dispatched actions
- **Where**: `frontend/src/app/features/payments/store/payment.effects.ts`
- **Why**: Bridges the NgRx action flow to the HTTP service layer; handles success/failure dispatching and checkout redirection
- **Content**:

```typescript
// frontend/src/app/features/payments/store/payment.effects.ts

import { Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Store } from '@ngrx/store';
import { of } from 'rxjs';
import { catchError, map, switchMap, tap, withLatestFrom } from 'rxjs/operators';
import { PaymentActions } from './payment.actions';
import { selectStatusFilter } from './payment.selectors';
import { PaymentService } from '../services/payment.service';
import { ParsedError } from '../../../core/error-handling';

@Injectable()
export class PaymentEffects {
  constructor(
    private readonly actions$: Actions,
    private readonly paymentService: PaymentService,
    private readonly store: Store
  ) {}

  /**
   * Loads paginated payments for the family.
   * Includes the current status filter from the store.
   */
  loadPayments$ = createEffect(() =>
    this.actions$.pipe(
      ofType(PaymentActions.loadPayments),
      withLatestFrom(this.store.select(selectStatusFilter)),
      switchMap(([action, statusFilter]) =>
        this.paymentService
          .getMyPayments({
            familyId: action.familyId,
            status: statusFilter ?? undefined,
            page: action.page,
            size: action.size,
          })
          .pipe(
            map((page) => PaymentActions.loadPaymentsSuccess({ page })),
            catchError((error: ParsedError) =>
              of(PaymentActions.loadPaymentsFailure({ error: error.message }))
            )
          )
      )
    )
  );

  /**
   * Loads a single payment detail by ID.
   */
  loadPaymentDetail$ = createEffect(() =>
    this.actions$.pipe(
      ofType(PaymentActions.loadPaymentDetail),
      switchMap((action) =>
        this.paymentService.getById(action.paymentId).pipe(
          map((payment) => PaymentActions.loadPaymentDetailSuccess({ payment })),
          catchError((error: ParsedError) =>
            of(PaymentActions.loadPaymentDetailFailure({ error: error.message }))
          )
        )
      )
    )
  );

  /**
   * Initiates a checkout session and redirects the user to HelloAsso.
   * After receiving the checkout URL, performs a window.location redirect.
   */
  initiateCheckout$ = createEffect(() =>
    this.actions$.pipe(
      ofType(PaymentActions.initiateCheckout),
      switchMap((action) =>
        this.paymentService.initiateCheckout(action.request).pipe(
          map((response) => PaymentActions.initiateCheckoutSuccess({ response })),
          catchError((error: ParsedError) =>
            of(PaymentActions.initiateCheckoutFailure({ error: error.message }))
          )
        )
      )
    )
  );

  /**
   * Redirects the browser to the HelloAsso checkout URL after successful initiation.
   * This is a non-dispatching effect (dispatch: false).
   */
  redirectToCheckout$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(PaymentActions.initiateCheckoutSuccess),
        tap((action) => {
          window.location.href = action.response.checkoutUrl;
        })
      ),
    { dispatch: false }
  );
}
```

- **Verify**: `cd frontend && npx ng build` -> compiles with no errors

---

## Task 7 Detail: NgRx Selectors

- **What**: Memoized selectors for reading payment state in components
- **Where**: `frontend/src/app/features/payments/store/payment.selectors.ts`
- **Why**: Decouples components from store shape; enables efficient change detection via memoization
- **Content**:

```typescript
// frontend/src/app/features/payments/store/payment.selectors.ts

import { createFeatureSelector, createSelector } from '@ngrx/store';
import { PaymentState } from './payment.state';

export const selectPaymentState = createFeatureSelector<PaymentState>('payments');

export const selectPayments = createSelector(
  selectPaymentState,
  (state) => state.payments
);

export const selectTotalElements = createSelector(
  selectPaymentState,
  (state) => state.totalElements
);

export const selectSelectedPayment = createSelector(
  selectPaymentState,
  (state) => state.selectedPayment
);

export const selectCheckoutResponse = createSelector(
  selectPaymentState,
  (state) => state.checkoutResponse
);

export const selectStatusFilter = createSelector(
  selectPaymentState,
  (state) => state.statusFilter
);

export const selectCurrentPage = createSelector(
  selectPaymentState,
  (state) => state.currentPage
);

export const selectPageSize = createSelector(
  selectPaymentState,
  (state) => state.pageSize
);

export const selectListLoading = createSelector(
  selectPaymentState,
  (state) => state.listLoading
);

export const selectDetailLoading = createSelector(
  selectPaymentState,
  (state) => state.detailLoading
);

export const selectCheckoutLoading = createSelector(
  selectPaymentState,
  (state) => state.checkoutLoading
);

export const selectError = createSelector(
  selectPaymentState,
  (state) => state.error
);

export const selectPaginationInfo = createSelector(
  selectPaymentState,
  (state) => ({
    page: state.currentPage,
    size: state.pageSize,
    totalElements: state.totalElements,
  })
);
```

- **Verify**: `cd frontend && npx ng build` -> compiles with no errors

---

## Tasks 8, 9, 10, 12: Components + SCSS

> Full source code for these tasks is in the companion file: **[S5-006-angular-payment-feature-components.md](./S5-006-angular-payment-feature-components.md)**
>
> The companion file contains complete, copy-paste-ready source code for:
>
> **Task 8 -- PaymentListComponent** (`frontend/src/app/features/payments/components/payment-list/`)
> - `payment-list.component.ts` -- Standalone component with Material table, status filter dropdown, pagination, NgRx store integration
> - `payment-list.component.html` -- Template with `@for`/`@if` control flow, `mat-table`, `mat-paginator`, `mat-chip` status display
> - Columns: Date, Membre, Association, Activite, Montant, Statut (chip), Actions (detail link)
> - Verify: `cd frontend && npx ng test --include='**/payment-list.component.spec.ts'`
>
> **Task 9 -- PaymentDetailComponent** (`frontend/src/app/features/payments/components/payment-detail/`)
> - `payment-detail.component.ts` -- Reads `:id` from route, dispatches `loadPaymentDetail`, status timeline logic, amount/date formatting
> - `payment-detail.component.html` -- Status timeline visualization, `mat-card` with payment grid, references section with HelloAsso IDs, subscription/invoice links
> - Verify: `cd frontend && npx ng test --include='**/payment-detail.component.spec.ts'`
>
> **Task 10 -- CheckoutRedirectComponent** (`frontend/src/app/features/payments/components/checkout-redirect/`)
> - `checkout-redirect.component.ts` -- Reads `status` and `paymentId` query params, shows loading spinner for 800ms, then success/error/cancelled card
> - `checkout-redirect.component.html` -- Loading spinner state, result card with icon/title/body, action buttons (view detail, back to list)
> - Verify: `cd frontend && npx ng test --include='**/checkout-redirect.component.spec.ts'`
>
> **Task 12 -- SCSS Styles** (BEM methodology)
> - `payment-list.component.scss` -- Table layout, status chip colors (pending=yellow, authorized=blue, completed=green, failed=red, refunded=grey, cancelled=grey), empty/error states
> - `payment-detail.component.scss` -- Timeline dots/connectors, card grid layout, monospace reference values, status chip colors
> - `checkout-redirect.component.scss` -- Centered card with colored top border, large status icon, action buttons
> - Verify: Visual inspection

---

## Task 11 Detail: Payments Routing

- **What**: Lazy-loaded route configuration for the payments feature module
- **Where**: `frontend/src/app/features/payments/payments.routes.ts`
- **Why**: Defines the URL structure for `/payments`, `/payments/:id`, and `/payments/checkout/redirect`
- **Content**:

```typescript
// frontend/src/app/features/payments/payments.routes.ts

import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { paymentReducer } from './store/payment.reducer';
import { PaymentEffects } from './store/payment.effects';

export const PAYMENT_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState('payments', paymentReducer),
      provideEffects(PaymentEffects),
    ],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./components/payment-list/payment-list.component').then(
            (m) => m.PaymentListComponent
          ),
        title: 'Mes paiements',
      },
      {
        path: 'checkout/redirect',
        loadComponent: () =>
          import('./components/checkout-redirect/checkout-redirect.component').then(
            (m) => m.CheckoutRedirectComponent
          ),
        title: 'Résultat du paiement',
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./components/payment-detail/payment-detail.component').then(
            (m) => m.PaymentDetailComponent
          ),
        title: 'Détail du paiement',
      },
    ],
  },
];
```

- **Verify**: `cd frontend && npx ng build` -> compiles and routes are registered

> **Note**: The `checkout/redirect` route MUST come before `:id` to avoid the dynamic segment matching "checkout" as a payment ID.

---

## Failing Tests (TDD Contract)

> Tests are located in the companion file: **[S5-006-angular-payment-feature-tests.md](./S5-006-angular-payment-feature-tests.md)**
>
> The companion file contains 4 complete Jest test files:
> 1. `payment-list.component.spec.ts` -- 6 tests: renders table, displays payments, filters by status, paginates, shows empty state, shows error
> 2. `checkout-redirect.component.spec.ts` -- 5 tests: shows spinner initially, shows success, shows error, shows cancelled, navigates to detail
> 3. `payment.service.spec.ts` -- 4 tests: initiateCheckout POST, getById GET, getMyPayments with params, error handling
> 4. `payment.reducer.spec.ts` -- 8 tests: initial state, load actions, checkout actions, filter, clear actions
>
> All 23 tests MUST fail before implementation (TDD) and pass after.
