// frontend/src/app/features/activities/store/activity.effects.spec.ts
import { TestBed } from '@angular/core/testing';
import { Actions } from '@ngrx/effects';
import { provideMockActions } from '@ngrx/effects/testing';
import { Observable, of, throwError } from 'rxjs';

import * as fromEffects from './activity.effects';
import { ActivityActions } from './activity.actions';
import { ActivityService } from '../services/activity.service';
import { Activity, ActivityDetail, ActivitySearchRequest, Session } from '@shared/models/activity.model';
import { PageResponse } from '../../association/models/association.model';

describe('Activity Effects', () => {
  let actions$: Observable<any>;
  let activityService: jest.Mocked<ActivityService>;

  const mockActivity: Activity = {
    id: 1,
    name: 'Natation enfants',
    category: 'Sport',
    level: 'BEGINNER',
    minAge: 6,
    maxAge: 10,
    priceCents: 15000,
    status: 'ACTIVE',
    sessionCount: 2,
  };

  const mockPage: PageResponse<Activity> = {
    content: [mockActivity],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 20,
  };

  const mockSession: Session = {
    id: 1,
    activityId: 1,
    dayOfWeek: 'TUESDAY',
    startTime: '17:00',
    endTime: '18:00',
    location: 'Piscine municipale',
    instructorName: 'Jean Dupont',
    maxCapacity: 20,
    active: true,
  };

  const mockDetail: ActivityDetail = {
    id: 1,
    associationId: 1,
    associationName: 'Lyon Natation Metropole',
    name: 'Natation enfants 6-10 ans',
    description: 'Cours de natation pour enfants',
    category: 'Sport',
    level: 'BEGINNER',
    minAge: 6,
    maxAge: 10,
    maxCapacity: 20,
    priceCents: 15000,
    seasonStart: '2024-09-01',
    seasonEnd: '2025-06-30',
    status: 'ACTIVE',
    sessions: [mockSession],
    createdAt: '2024-01-15T10:00:00',
    updatedAt: '2024-06-01T14:30:00',
  };

  beforeEach(() => {
    activityService = {
      getActivities: jest.fn(),
      getActivityDetail: jest.fn(),
    } as any;

    TestBed.configureTestingModule({
      providers: [
        provideMockActions(() => actions$),
        { provide: ActivityService, useValue: activityService },
      ],
    });
  });

  describe('loadActivities$', () => {
    it('should dispatch loadActivitiesSuccess on service success', (done) => {
      const request: ActivitySearchRequest = { associationId: 1 };
      actions$ = of(ActivityActions.loadActivities({ request }));
      activityService.getActivities.mockReturnValue(of(mockPage));

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.loadActivities$(
          TestBed.inject(Actions),
          activityService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(ActivityActions.loadActivitiesSuccess({ page: mockPage }));
          expect(activityService.getActivities).toHaveBeenCalledWith(request);
          done();
        });
      });
    });

    it('should dispatch loadActivitiesFailure on service error', (done) => {
      const request: ActivitySearchRequest = { associationId: 1 };
      actions$ = of(ActivityActions.loadActivities({ request }));
      activityService.getActivities.mockReturnValue(
        throwError(() => ({ message: 'Network error' }))
      );

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.loadActivities$(
          TestBed.inject(Actions),
          activityService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            ActivityActions.loadActivitiesFailure({ error: 'Network error' })
          );
          done();
        });
      });
    });
  });

  describe('loadActivityDetail$', () => {
    it('should dispatch loadActivityDetailSuccess on service success', (done) => {
      actions$ = of(ActivityActions.loadActivityDetail({ associationId: 1, activityId: 1 }));
      activityService.getActivityDetail.mockReturnValue(of(mockDetail));

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.loadActivityDetail$(
          TestBed.inject(Actions),
          activityService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            ActivityActions.loadActivityDetailSuccess({ activity: mockDetail })
          );
          expect(activityService.getActivityDetail).toHaveBeenCalledWith(1, 1);
          done();
        });
      });
    });

    it('should dispatch loadActivityDetailFailure on service error', (done) => {
      actions$ = of(ActivityActions.loadActivityDetail({ associationId: 1, activityId: 999 }));
      activityService.getActivityDetail.mockReturnValue(
        throwError(() => ({ message: 'Activity not found' }))
      );

      TestBed.runInInjectionContext(() => {
        const effect = fromEffects.loadActivityDetail$(
          TestBed.inject(Actions),
          activityService
        );
        effect.subscribe((action) => {
          expect(action).toEqual(
            ActivityActions.loadActivityDetailFailure({ error: 'Activity not found' })
          );
          done();
        });
      });
    });
  });
});
