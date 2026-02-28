// frontend/src/app/features/activities/store/activity.effects.ts
import { inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { ActivityService } from '../services/activity.service';
import { ActivityActions } from './activity.actions';

/**
 * Load activities list effect.
 *
 * Listens for [Activities] Load Activities action,
 * calls ActivityService.getActivities(), then dispatches success or failure.
 */
export const loadActivities$ = createEffect(
  (actions$ = inject(Actions), service = inject(ActivityService)) =>
    actions$.pipe(
      ofType(ActivityActions.loadActivities),
      switchMap(({ request }) =>
        service.getActivities(request).pipe(
          map((page) => ActivityActions.loadActivitiesSuccess({ page })),
          catchError((error) =>
            of(
              ActivityActions.loadActivitiesFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement des activit\u00e9s',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Load activity detail effect.
 *
 * Listens for [Activities] Load Activity Detail action,
 * calls ActivityService.getActivityDetail(), then dispatches success or failure.
 */
export const loadActivityDetail$ = createEffect(
  (actions$ = inject(Actions), service = inject(ActivityService)) =>
    actions$.pipe(
      ofType(ActivityActions.loadActivityDetail),
      switchMap(({ associationId, activityId }) =>
        service.getActivityDetail(associationId, activityId).pipe(
          map((activity) =>
            ActivityActions.loadActivityDetailSuccess({ activity })
          ),
          catchError((error) =>
            of(
              ActivityActions.loadActivityDetailFailure({
                error: error?.error?.message || error?.message || 'Erreur lors du chargement de l\'activit\u00e9',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);
