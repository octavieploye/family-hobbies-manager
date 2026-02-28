// frontend/src/app/features/subscriptions/subscriptions.routes.ts
import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { subscriptionReducer } from './store/subscription.reducer';
import * as subscriptionEffects from './store/subscription.effects';

/**
 * Lazy-loaded routes for the subscriptions feature.
 *
 * All subscription routes require authentication (authGuard applied at app.routes.ts level).
 * The feature NgRx store and effects are registered at the route level
 * so they are only loaded when the user navigates to /subscriptions.
 */
export const SUBSCRIPTION_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState('subscriptions', subscriptionReducer),
      provideEffects(subscriptionEffects),
    ],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./components/subscription-list/subscription-list.component').then(
            (m) => m.SubscriptionListComponent
          ),
        title: 'Mes inscriptions',
      },
    ],
  },
];
