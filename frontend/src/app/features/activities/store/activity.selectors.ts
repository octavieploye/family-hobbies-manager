// frontend/src/app/features/activities/store/activity.selectors.ts
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { ActivityState } from './activity.reducer';

/**
 * Feature selector for the 'activities' state slice.
 * The key 'activities' must match the key used in provideState().
 */
export const selectActivityState =
  createFeatureSelector<ActivityState>('activities');

/** Select the list of activities from the latest load. */
export const selectActivities = createSelector(
  selectActivityState,
  (state) => state.activities
);

/** Select the currently loaded activity detail. */
export const selectSelectedActivity = createSelector(
  selectActivityState,
  (state) => state.selectedActivity
);

/** Select total number of results (across all pages). */
export const selectActivityTotalElements = createSelector(
  selectActivityState,
  (state) => state.totalElements
);

/** Select total number of pages. */
export const selectActivityTotalPages = createSelector(
  selectActivityState,
  (state) => state.totalPages
);

/** Select the current page index (0-based). */
export const selectActivityCurrentPage = createSelector(
  selectActivityState,
  (state) => state.currentPage
);

/** Select the page size. */
export const selectActivityPageSize = createSelector(
  selectActivityState,
  (state) => state.pageSize
);

/** Select whether a request is in progress. */
export const selectActivityLoading = createSelector(
  selectActivityState,
  (state) => state.loading
);

/** Select the current error message, if any. */
export const selectActivityError = createSelector(
  selectActivityState,
  (state) => state.error
);

/**
 * Composite selector for pagination state.
 * Useful for binding directly to MatPaginator.
 */
export const selectActivityPagination = createSelector(
  selectActivityTotalElements,
  selectActivityCurrentPage,
  selectActivityPageSize,
  (totalElements, currentPage, pageSize) => ({
    totalElements,
    currentPage,
    pageSize,
  })
);
