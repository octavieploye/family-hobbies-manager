// frontend/src/app/features/subscriptions/store/subscription.effects.ts
import { inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { SubscriptionService } from '../services/subscription.service';
import { SubscriptionActions } from './subscription.actions';

/**
 * Load subscriptions by family effect.
 */
export const loadSubscriptions$ = createEffect(
  (actions$ = inject(Actions), service = inject(SubscriptionService)) =>
    actions$.pipe(
      ofType(SubscriptionActions.loadSubscriptions),
      switchMap(({ familyId }) =>
        service.getByFamily(familyId).pipe(
          map((subscriptions) =>
            SubscriptionActions.loadSubscriptionsSuccess({ subscriptions })
          ),
          catchError((error) =>
            of(
              SubscriptionActions.loadSubscriptionsFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement des inscriptions',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Load subscriptions by member effect.
 */
export const loadMemberSubscriptions$ = createEffect(
  (actions$ = inject(Actions), service = inject(SubscriptionService)) =>
    actions$.pipe(
      ofType(SubscriptionActions.loadMemberSubscriptions),
      switchMap(({ memberId }) =>
        service.getByMember(memberId).pipe(
          map((subscriptions) =>
            SubscriptionActions.loadMemberSubscriptionsSuccess({ subscriptions })
          ),
          catchError((error) =>
            of(
              SubscriptionActions.loadMemberSubscriptionsFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement des inscriptions du membre',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Create subscription effect.
 */
export const createSubscription$ = createEffect(
  (actions$ = inject(Actions), service = inject(SubscriptionService)) =>
    actions$.pipe(
      ofType(SubscriptionActions.createSubscription),
      switchMap(({ request }) =>
        service.create(request).pipe(
          map((subscription) =>
            SubscriptionActions.createSubscriptionSuccess({ subscription })
          ),
          catchError((error) =>
            of(
              SubscriptionActions.createSubscriptionFailure({
                error: error?.error?.message || error?.message || 'Erreur lors de la cr\u00e9ation de l\'inscription',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Cancel subscription effect.
 */
export const cancelSubscription$ = createEffect(
  (actions$ = inject(Actions), service = inject(SubscriptionService)) =>
    actions$.pipe(
      ofType(SubscriptionActions.cancelSubscription),
      switchMap(({ subscriptionId }) =>
        service.cancel(subscriptionId).pipe(
          map((subscription) =>
            SubscriptionActions.cancelSubscriptionSuccess({ subscription })
          ),
          catchError((error) =>
            of(
              SubscriptionActions.cancelSubscriptionFailure({
                error: error?.error?.message || error?.message || 'Erreur lors de l\'annulation de l\'inscription',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);
