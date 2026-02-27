// frontend/src/app/features/family/store/family.effects.ts
import { inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { FamilyService } from '../services/family.service';
import { FamilyActions } from './family.actions';

/**
 * Functional NgRx effects for the Family feature.
 *
 * Each effect listens for a trigger action, calls the FamilyService,
 * and dispatches a success or failure action.
 *
 * Uses the functional effect pattern (createEffect with { functional: true })
 * consistent with the existing auth effects in this project.
 */

/**
 * Load family effect.
 * Calls FamilyService.getMyFamily() and dispatches loadFamilySuccess or loadFamilyFailure.
 */
export const loadFamily$ = createEffect(
  (actions$ = inject(Actions), familyService = inject(FamilyService)) =>
    actions$.pipe(
      ofType(FamilyActions.loadFamily),
      switchMap(() =>
        familyService.getMyFamily().pipe(
          map((family) => FamilyActions.loadFamilySuccess({ family })),
          catchError((error) =>
            of(FamilyActions.loadFamilyFailure({
              error: error?.error?.message || error?.message || 'Failed to load family',
            }))
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Create family effect.
 * Calls FamilyService.createFamily() and dispatches createFamilySuccess or createFamilyFailure.
 */
export const createFamily$ = createEffect(
  (actions$ = inject(Actions), familyService = inject(FamilyService)) =>
    actions$.pipe(
      ofType(FamilyActions.createFamily),
      switchMap(({ request }) =>
        familyService.createFamily(request).pipe(
          map((family) => FamilyActions.createFamilySuccess({ family })),
          catchError((error) =>
            of(FamilyActions.createFamilyFailure({
              error: error?.error?.message || error?.message || 'Failed to create family',
            }))
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Update family effect.
 * Calls FamilyService.updateFamily() and dispatches updateFamilySuccess or updateFamilyFailure.
 */
export const updateFamily$ = createEffect(
  (actions$ = inject(Actions), familyService = inject(FamilyService)) =>
    actions$.pipe(
      ofType(FamilyActions.updateFamily),
      switchMap(({ id, request }) =>
        familyService.updateFamily(id, request).pipe(
          map((family) => FamilyActions.updateFamilySuccess({ family })),
          catchError((error) =>
            of(FamilyActions.updateFamilyFailure({
              error: error?.error?.message || error?.message || 'Failed to update family',
            }))
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Add member effect.
 * Calls FamilyService.addMember() and dispatches addMemberSuccess or addMemberFailure.
 */
export const addMember$ = createEffect(
  (actions$ = inject(Actions), familyService = inject(FamilyService)) =>
    actions$.pipe(
      ofType(FamilyActions.addMember),
      switchMap(({ familyId, request }) =>
        familyService.addMember(familyId, request).pipe(
          map((member) => FamilyActions.addMemberSuccess({ member })),
          catchError((error) =>
            of(FamilyActions.addMemberFailure({
              error: error?.error?.message || error?.message || 'Failed to add member',
            }))
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Update member effect.
 * Calls FamilyService.updateMember() and dispatches updateMemberSuccess or updateMemberFailure.
 */
export const updateMember$ = createEffect(
  (actions$ = inject(Actions), familyService = inject(FamilyService)) =>
    actions$.pipe(
      ofType(FamilyActions.updateMember),
      switchMap(({ memberId, request }) =>
        familyService.updateMember(memberId, request).pipe(
          map((member) => FamilyActions.updateMemberSuccess({ member })),
          catchError((error) =>
            of(FamilyActions.updateMemberFailure({
              error: error?.error?.message || error?.message || 'Failed to update member',
            }))
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Remove member effect.
 * Calls FamilyService.removeMember() and dispatches removeMemberSuccess or removeMemberFailure.
 */
export const removeMember$ = createEffect(
  (actions$ = inject(Actions), familyService = inject(FamilyService)) =>
    actions$.pipe(
      ofType(FamilyActions.removeMember),
      switchMap(({ memberId }) =>
        familyService.removeMember(memberId).pipe(
          map(() => FamilyActions.removeMemberSuccess({ memberId })),
          catchError((error) =>
            of(FamilyActions.removeMemberFailure({
              error: error?.error?.message || error?.message || 'Failed to remove member',
            }))
          )
        )
      )
    ),
  { functional: true }
);
