// frontend/src/app/features/payments/payments.routes.ts
import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { paymentReducer } from './store/payment.reducer';
import * as paymentEffects from './store/payment.effects';

/**
 * Lazy-loaded routes for the payments feature.
 *
 * All payment routes require authentication (authGuard applied at app.routes.ts level).
 * The feature NgRx store and effects are registered at the route level
 * so they are only loaded when the user navigates to /payments.
 */
export const PAYMENT_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState('payments', paymentReducer),
      provideEffects(paymentEffects),
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
        title: 'Resultat du paiement',
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./components/payment-detail/payment-detail.component').then(
            (m) => m.PaymentDetailComponent
          ),
        title: 'Detail du paiement',
      },
    ],
  },
];
