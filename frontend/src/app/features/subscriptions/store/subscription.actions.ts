// frontend/src/app/features/subscriptions/store/subscription.actions.ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import {
  Subscription,
  SubscriptionRequest,
  SubscriptionStatus,
} from '@shared/models/subscription.model';

/**
 * NgRx action group for the Subscriptions feature.
 *
 * Follows the [Source] Event naming convention.
 * Each async operation has a triplet: trigger / success / failure.
 */
export const SubscriptionActions = createActionGroup({
  source: 'Subscriptions',
  events: {
    // --- Load by Family ---
    'Load Subscriptions': props<{ familyId: number }>(),
    'Load Subscriptions Success': props<{ subscriptions: Subscription[] }>(),
    'Load Subscriptions Failure': props<{ error: string }>(),

    // --- Load by Member ---
    'Load Member Subscriptions': props<{ memberId: number }>(),
    'Load Member Subscriptions Success': props<{ subscriptions: Subscription[] }>(),
    'Load Member Subscriptions Failure': props<{ error: string }>(),

    // --- Create ---
    'Create Subscription': props<{ request: SubscriptionRequest }>(),
    'Create Subscription Success': props<{ subscription: Subscription }>(),
    'Create Subscription Failure': props<{ error: string }>(),

    // --- Cancel ---
    'Cancel Subscription': props<{ subscriptionId: number }>(),
    'Cancel Subscription Success': props<{ subscription: Subscription }>(),
    'Cancel Subscription Failure': props<{ error: string }>(),

    // --- Filters ---
    'Set Status Filter': props<{ status: SubscriptionStatus | null }>(),

    // --- Clear ---
    'Clear Subscriptions': emptyProps(),
    'Clear Error': emptyProps(),
  },
});
