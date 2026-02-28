// frontend/src/app/features/subscriptions/store/subscription.selectors.ts
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { SubscriptionState } from './subscription.reducer';

/**
 * Feature selector for the 'subscriptions' state slice.
 * The key 'subscriptions' must match the key used in provideState().
 */
export const selectSubscriptionState =
  createFeatureSelector<SubscriptionState>('subscriptions');

/** Select the full list of subscriptions. */
export const selectAllSubscriptions = createSelector(
  selectSubscriptionState,
  (state) => state.subscriptions
);

/** Select the current status filter. */
export const selectStatusFilter = createSelector(
  selectSubscriptionState,
  (state) => state.statusFilter
);

/** Select subscriptions filtered by current status filter. */
export const selectFilteredSubscriptions = createSelector(
  selectAllSubscriptions,
  selectStatusFilter,
  (subscriptions, statusFilter) => {
    if (!statusFilter) {
      return subscriptions;
    }
    return subscriptions.filter((s) => s.status === statusFilter);
  }
);

/** Select whether a request is in progress. */
export const selectSubscriptionLoading = createSelector(
  selectSubscriptionState,
  (state) => state.loading
);

/** Select the current error message, if any. */
export const selectSubscriptionError = createSelector(
  selectSubscriptionState,
  (state) => state.error
);
