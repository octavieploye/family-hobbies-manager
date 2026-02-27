// frontend/src/app/features/association/store/association.selectors.ts
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AssociationState } from './association.reducer';

/**
 * Feature selector for the 'associations' state slice.
 * The key 'associations' must match the key used in provideState().
 */
export const selectAssociationState =
  createFeatureSelector<AssociationState>('associations');

/** Select the list of associations from the latest search. */
export const selectAssociations = createSelector(
  selectAssociationState,
  (state) => state.associations
);

/** Select the currently loaded association detail. */
export const selectSelectedAssociation = createSelector(
  selectAssociationState,
  (state) => state.selectedAssociation
);

/** Select total number of search results (across all pages). */
export const selectTotalElements = createSelector(
  selectAssociationState,
  (state) => state.totalElements
);

/** Select total number of pages from the latest search. */
export const selectTotalPages = createSelector(
  selectAssociationState,
  (state) => state.totalPages
);

/** Select the current page index (0-based). */
export const selectCurrentPage = createSelector(
  selectAssociationState,
  (state) => state.currentPage
);

/** Select the page size. */
export const selectPageSize = createSelector(
  selectAssociationState,
  (state) => state.pageSize
);

/** Select whether a request is in progress. */
export const selectAssociationLoading = createSelector(
  selectAssociationState,
  (state) => state.loading
);

/** Select the current error message, if any. */
export const selectAssociationError = createSelector(
  selectAssociationState,
  (state) => state.error
);

/**
 * Composite selector for pagination state.
 * Useful for binding directly to MatPaginator.
 */
export const selectPagination = createSelector(
  selectTotalElements,
  selectCurrentPage,
  selectPageSize,
  (totalElements, currentPage, pageSize) => ({
    totalElements,
    currentPage,
    pageSize,
  })
);
