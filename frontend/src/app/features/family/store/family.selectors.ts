// frontend/src/app/features/family/store/family.selectors.ts
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { FamilyState } from './family.reducer';

/**
 * Feature selector for the 'family' state slice.
 * The feature key 'family' must match the key used when registering
 * the reducer via provideState('family', familyReducer) in family routes.
 */
export const selectFamilyState = createFeatureSelector<FamilyState>('family');

/**
 * Select the family object (or null if not loaded / not created).
 */
export const selectFamily = createSelector(
  selectFamilyState,
  (state) => state.family
);

/**
 * Select the family members array.
 * Returns an empty array if no family is loaded.
 */
export const selectFamilyMembers = createSelector(
  selectFamilyState,
  (state) => state.family?.members ?? []
);

/**
 * Select the loading flag.
 * Used by components to show a spinner during async operations.
 */
export const selectFamilyLoading = createSelector(
  selectFamilyState,
  (state) => state.loading
);

/**
 * Select the error message.
 * Used by components to display error feedback to the user.
 */
export const selectFamilyError = createSelector(
  selectFamilyState,
  (state) => state.error
);
