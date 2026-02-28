// frontend/src/app/features/subscriptions/store/subscription.effects.spec.ts
import { TestBed } from '@angular/core/testing';
import { Actions } from '@ngrx/effects';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of, throwError } from 'rxjs';

import * as fromEffects from './subscription.effects';
import { SubscriptionActions } from './subscription.actions';
import { SubscriptionService } from '../services/subscription.service';
import { Subscription, SubscriptionRequest } from '@shared/models/subscription.model';

describe('Subscription Effects', () => {
  let actions$: Observable<any>;
  let subscriptionService: jest.Mocked<SubscriptionService>;

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
    cancellationReason: 'Demenagement',
    cancelledAt: '2024-10-01T10:00:00',
  };

  beforeEach(() => {
    subscriptionService = {
      getByFamily: jest.fn(),
      getByMember: jest.fn(),
      getById: jest.fn(),
      create: jest.fn(),
      cancel: jest.fn(),
    } as any;

    TestBed.configureTestingModule({
      providers: [
        provideMockActions(() => actions$),
        { provide: SubscriptionService, useValue: subscriptionService },
      ],
    });
  });

  describe('loadSubscriptions$', () => {
    it('should dispatch loadSubscriptionsSuccess on service success', (done) => {
      actions$ = of(SubscriptionActions.loadSubscriptions({ familyId: 3 }));
      subscriptionService.getByFamily.mockReturnValue(of([mockSubscription]));

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.loadSubscriptions$(
          TestBed.inject(Actions),
          subscriptionService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            SubscriptionActions.loadSubscriptionsSuccess({ subscriptions: [mockSubscription] })
          );
          expect(subscriptionService.getByFamily).toHaveBeenCalledWith(3);
          done();
        });
      });
    });

    it('should dispatch loadSubscriptionsFailure on service error', (done) => {
      actions$ = of(SubscriptionActions.loadSubscriptions({ familyId: 3 }));
      subscriptionService.getByFamily.mockReturnValue(
        throwError(() => ({ message: 'Erreur serveur' }))
      );

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.loadSubscriptions$(
          TestBed.inject(Actions),
          subscriptionService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            SubscriptionActions.loadSubscriptionsFailure({ error: 'Erreur serveur' })
          );
          done();
        });
      });
    });
  });

  describe('createSubscription$', () => {
    it('should dispatch createSubscriptionSuccess on service success', (done) => {
      const request: SubscriptionRequest = {
        activityId: 5,
        familyMemberId: 10,
        familyId: 3,
        subscriptionType: 'ADHESION',
        startDate: '2024-09-01',
      };
      actions$ = of(SubscriptionActions.createSubscription({ request }));
      subscriptionService.create.mockReturnValue(of(mockSubscription));

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.createSubscription$(
          TestBed.inject(Actions),
          subscriptionService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            SubscriptionActions.createSubscriptionSuccess({ subscription: mockSubscription })
          );
          expect(subscriptionService.create).toHaveBeenCalledWith(request);
          done();
        });
      });
    });

    it('should dispatch createSubscriptionFailure on service error', (done) => {
      const request: SubscriptionRequest = {
        activityId: 5,
        familyMemberId: 10,
        familyId: 3,
        subscriptionType: 'ADHESION',
        startDate: '2024-09-01',
      };
      actions$ = of(SubscriptionActions.createSubscription({ request }));
      subscriptionService.create.mockReturnValue(
        throwError(() => ({ message: 'Conflict' }))
      );

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.createSubscription$(
          TestBed.inject(Actions),
          subscriptionService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            SubscriptionActions.createSubscriptionFailure({ error: 'Conflict' })
          );
          done();
        });
      });
    });
  });

  describe('cancelSubscription$', () => {
    it('should dispatch cancelSubscriptionSuccess on service success', (done) => {
      actions$ = of(SubscriptionActions.cancelSubscription({ subscriptionId: 1, reason: 'Demenagement' }));
      subscriptionService.cancel.mockReturnValue(of(cancelledSubscription));

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.cancelSubscription$(
          TestBed.inject(Actions),
          subscriptionService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            SubscriptionActions.cancelSubscriptionSuccess({ subscription: cancelledSubscription })
          );
          expect(subscriptionService.cancel).toHaveBeenCalledWith(1, 'Demenagement');
          done();
        });
      });
    });

    it('should dispatch cancelSubscriptionFailure on service error', (done) => {
      actions$ = of(SubscriptionActions.cancelSubscription({ subscriptionId: 1 }));
      subscriptionService.cancel.mockReturnValue(
        throwError(() => ({ message: 'Subscription not found' }))
      );

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.cancelSubscription$(
          TestBed.inject(Actions),
          subscriptionService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            SubscriptionActions.cancelSubscriptionFailure({ error: 'Subscription not found' })
          );
          done();
        });
      });
    });
  });
});
