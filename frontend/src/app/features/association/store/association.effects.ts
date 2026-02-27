// frontend/src/app/features/association/store/association.effects.ts
import { inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { AssociationService } from '../services/association.service';
import { AssociationActions } from './association.actions';

/**
 * Search associations effect.
 *
 * Listens for [Association] Search Associations action,
 * calls AssociationService.search(), then dispatches success or failure.
 */
export const searchAssociations$ = createEffect(
  (actions$ = inject(Actions), service = inject(AssociationService)) =>
    actions$.pipe(
      ofType(AssociationActions.searchAssociations),
      switchMap(({ request }) =>
        service.search(request).pipe(
          map((page) => AssociationActions.searchAssociationsSuccess({ page })),
          catchError((error) =>
            of(
              AssociationActions.searchAssociationsFailure({
                error: error?.error?.message || error?.message || 'Search failed',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Load association detail effect.
 *
 * Listens for [Association] Load Association Detail action,
 * calls AssociationService.getById(), then dispatches success or failure.
 */
export const loadAssociationDetail$ = createEffect(
  (actions$ = inject(Actions), service = inject(AssociationService)) =>
    actions$.pipe(
      ofType(AssociationActions.loadAssociationDetail),
      switchMap(({ id }) =>
        service.getById(id).pipe(
          map((association) =>
            AssociationActions.loadAssociationDetailSuccess({ association })
          ),
          catchError((error) =>
            of(
              AssociationActions.loadAssociationDetailFailure({
                error: error?.error?.message || error?.message || 'Failed to load association',
              })
            )
          )
        )
      )
    ),
  { functional: true }
);
