// frontend/src/app/features/payments/store/payment.effects.ts
import { inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { PaymentService } from '../services/payment.service';
import { PaymentActions } from './payment.actions';

/**
 * Load payments list effect.
 *
 * Listens for [Payments] Load Payments action,
 * calls PaymentService.getMyPayments(), then dispatches success or failure.
 */
export const loadPayments$ = createEffect(
  (actions$ = inject(Actions), service = inject(PaymentService)) =>
    actions$.pipe(
      ofType(PaymentActions.loadPayments),
      switchMap(({ params }) =>
        service.getMyPayments(params).pipe(
          map((page) => PaymentActions.loadPaymentsSuccess({ page })),
          catchError((error) =>
            of(
              PaymentActions.loadPaymentsFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement des paiements',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Load payment detail effect.
 *
 * Listens for [Payments] Load Payment Detail action,
 * calls PaymentService.getById(), then dispatches success or failure.
 */
export const loadPaymentDetail$ = createEffect(
  (actions$ = inject(Actions), service = inject(PaymentService)) =>
    actions$.pipe(
      ofType(PaymentActions.loadPaymentDetail),
      switchMap(({ paymentId }) =>
        service.getById(paymentId).pipe(
          map((payment) => PaymentActions.loadPaymentDetailSuccess({ payment })),
          catchError((error) =>
            of(
              PaymentActions.loadPaymentDetailFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement du paiement',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Initiate checkout effect.
 *
 * Listens for [Payments] Initiate Checkout action,
 * calls PaymentService.initiateCheckout(), then dispatches success or failure.
 */
export const initiateCheckout$ = createEffect(
  (actions$ = inject(Actions), service = inject(PaymentService)) =>
    actions$.pipe(
      ofType(PaymentActions.initiateCheckout),
      switchMap(({ request }) =>
        service.initiateCheckout(request).pipe(
          map((response) => PaymentActions.initiateCheckoutSuccess({ response })),
          catchError((error) =>
            of(
              PaymentActions.initiateCheckoutFailure({
                error: error?.error?.message || error?.message || 'Erreur lors de la creation du paiement',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Redirect to HelloAsso checkout effect (non-dispatching).
 *
 * On checkout success, redirects the browser to the HelloAsso checkout URL.
 */
export const redirectToCheckout$ = createEffect(
  (actions$ = inject(Actions)) =>
    actions$.pipe(
      ofType(PaymentActions.initiateCheckoutSuccess),
      tap(({ response }) => {
        window.location.href = response.checkoutUrl;
      })
    ),
  { functional: true, dispatch: false }
);
