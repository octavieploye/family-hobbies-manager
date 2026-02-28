// frontend/src/app/features/subscriptions/store/subscription.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { Subscription, SubscriptionStatus } from '@shared/models/subscription.model';
import { SubscriptionActions } from './subscription.actions';

/**
 * State shape for the Subscriptions feature store.
 */
export interface SubscriptionState {
  subscriptions: Subscription[];
  loading: boolean;
  error: string | null;
  statusFilter: SubscriptionStatus | null;
}

/**
 * Initial state with sensible defaults.
 */
export const initialSubscriptionState: SubscriptionState = {
  subscriptions: [],
  loading: false,
  error: null,
  statusFilter: null,
};

/**
 * Subscriptions feature reducer.
 * Handles list loading, creating, cancelling, filters, and state clearing.
 */
export const subscriptionReducer = createReducer(
  initialSubscriptionState,

  // --- Load Subscriptions by Family ---
  on(SubscriptionActions.loadSubscriptions, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(SubscriptionActions.loadSubscriptionsSuccess, (state, { subscriptions }) => ({
    ...state,
    subscriptions,
    loading: false,
    error: null,
  })),

  on(SubscriptionActions.loadSubscriptionsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Load Subscriptions by Member ---
  on(SubscriptionActions.loadMemberSubscriptions, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(SubscriptionActions.loadMemberSubscriptionsSuccess, (state, { subscriptions }) => ({
    ...state,
    subscriptions,
    loading: false,
    error: null,
  })),

  on(SubscriptionActions.loadMemberSubscriptionsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Create Subscription ---
  on(SubscriptionActions.createSubscription, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(SubscriptionActions.createSubscriptionSuccess, (state, { subscription }) => ({
    ...state,
    subscriptions: [...state.subscriptions, subscription],
    loading: false,
    error: null,
  })),

  on(SubscriptionActions.createSubscriptionFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Cancel Subscription ---
  on(SubscriptionActions.cancelSubscription, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(SubscriptionActions.cancelSubscriptionSuccess, (state, { subscription }) => ({
    ...state,
    subscriptions: state.subscriptions.map((s) =>
      s.id === subscription.id ? subscription : s
    ),
    loading: false,
    error: null,
  })),

  on(SubscriptionActions.cancelSubscriptionFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Filters ---
  on(SubscriptionActions.setStatusFilter, (state, { status }) => ({
    ...state,
    statusFilter: status,
  })),

  // --- Clear ---
  on(SubscriptionActions.clearSubscriptions, () => ({
    ...initialSubscriptionState,
  })),

  on(SubscriptionActions.clearError, (state) => ({
    ...state,
    error: null,
  }))
);
