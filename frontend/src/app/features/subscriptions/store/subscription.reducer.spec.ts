// frontend/src/app/features/subscriptions/store/subscription.reducer.spec.ts
import {
  subscriptionReducer,
  initialSubscriptionState,
  SubscriptionState,
} from './subscription.reducer';
import { SubscriptionActions } from './subscription.actions';
import { Subscription } from '@shared/models/subscription.model';

describe('SubscriptionReducer', () => {
  const mockSubscription: Subscription = {
    id: 1,
    activityId: 5,
    activityName: 'Natation enfants',
    associationName: 'Lyon Natation Metropole',
    familyMemberId: 10,
    memberFirstName: 'Marie',
    memberLastName: 'Dupont',
    familyId: 3,
    userId: 1,
    subscriptionType: 'ADHESION',
    status: 'ACTIVE',
    startDate: '2024-09-01',
    endDate: null,
    cancellationReason: null,
    cancelledAt: null,
    createdAt: '2024-08-15T10:00:00',
    updatedAt: '2024-08-15T10:00:00',
  };

  const cancelledSubscription: Subscription = {
    ...mockSubscription,
    status: 'CANCELLED',
    cancellationReason: 'User requested',
    cancelledAt: '2024-10-01T10:00:00',
  };

  it('should return the initial state on unknown action', () => {
    const action = { type: 'UNKNOWN' } as any;
    const state = subscriptionReducer(undefined, action);
    expect(state).toEqual(initialSubscriptionState);
  });

  describe('Load Subscriptions (by Family)', () => {
    it('should set loading to true on loadSubscriptions', () => {
      const action = SubscriptionActions.loadSubscriptions({ familyId: 3 });
      const state = subscriptionReducer(initialSubscriptionState, action);

      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('should populate subscriptions on loadSubscriptionsSuccess', () => {
      const action = SubscriptionActions.loadSubscriptionsSuccess({
        subscriptions: [mockSubscription],
      });
      const state = subscriptionReducer(
        { ...initialSubscriptionState, loading: true },
        action
      );

      expect(state.subscriptions).toEqual([mockSubscription]);
      expect(state.loading).toBe(false);
      expect(state.error).toBeNull();
    });

    it('should set error on loadSubscriptionsFailure', () => {
      const action = SubscriptionActions.loadSubscriptionsFailure({
        error: 'Network error',
      });
      const state = subscriptionReducer(
        { ...initialSubscriptionState, loading: true },
        action
      );

      expect(state.loading).toBe(false);
      expect(state.error).toBe('Network error');
    });
  });

  describe('Create Subscription', () => {
    it('should set loading on createSubscription', () => {
      const action = SubscriptionActions.createSubscription({
        request: {
          activityId: 5,
          familyMemberId: 10,
          familyId: 3,
          subscriptionType: 'ADHESION',
          startDate: '2024-09-01',
        },
      });
      const state = subscriptionReducer(initialSubscriptionState, action);

      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('should add subscription on createSubscriptionSuccess', () => {
      const action = SubscriptionActions.createSubscriptionSuccess({
        subscription: mockSubscription,
      });
      const state = subscriptionReducer(
        { ...initialSubscriptionState, loading: true },
        action
      );

      expect(state.subscriptions).toEqual([mockSubscription]);
      expect(state.loading).toBe(false);
    });

    it('should set error on createSubscriptionFailure', () => {
      const action = SubscriptionActions.createSubscriptionFailure({
        error: 'Duplicate subscription',
      });
      const state = subscriptionReducer(
        { ...initialSubscriptionState, loading: true },
        action
      );

      expect(state.loading).toBe(false);
      expect(state.error).toBe('Duplicate subscription');
    });
  });

  describe('Cancel Subscription', () => {
    it('should update subscription on cancelSubscriptionSuccess', () => {
      const stateWithSubscription: SubscriptionState = {
        ...initialSubscriptionState,
        subscriptions: [mockSubscription],
        loading: true,
      };

      const action = SubscriptionActions.cancelSubscriptionSuccess({
        subscription: cancelledSubscription,
      });
      const state = subscriptionReducer(stateWithSubscription, action);

      expect(state.subscriptions[0].status).toBe('CANCELLED');
      expect(state.subscriptions[0].cancelledAt).toBeTruthy();
      expect(state.loading).toBe(false);
    });
  });

  describe('Filters', () => {
    it('should set status filter', () => {
      const action = SubscriptionActions.setStatusFilter({ status: 'ACTIVE' });
      const state = subscriptionReducer(initialSubscriptionState, action);

      expect(state.statusFilter).toBe('ACTIVE');
    });

    it('should clear status filter with null', () => {
      const stateWithFilter: SubscriptionState = {
        ...initialSubscriptionState,
        statusFilter: 'ACTIVE',
      };
      const action = SubscriptionActions.setStatusFilter({ status: null });
      const state = subscriptionReducer(stateWithFilter, action);

      expect(state.statusFilter).toBeNull();
    });
  });

  describe('Clear', () => {
    it('should reset to initial state on clearSubscriptions', () => {
      const populatedState: SubscriptionState = {
        subscriptions: [mockSubscription],
        loading: false,
        error: null,
        statusFilter: 'ACTIVE',
      };
      const action = SubscriptionActions.clearSubscriptions();
      const state = subscriptionReducer(populatedState, action);

      expect(state).toEqual(initialSubscriptionState);
    });

    it('should clear error on clearError', () => {
      const stateWithError: SubscriptionState = {
        ...initialSubscriptionState,
        error: 'Some error',
      };
      const action = SubscriptionActions.clearError();
      const state = subscriptionReducer(stateWithError, action);

      expect(state.error).toBeNull();
    });
  });
});
