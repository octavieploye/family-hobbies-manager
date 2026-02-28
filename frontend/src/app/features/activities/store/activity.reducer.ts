// frontend/src/app/features/activities/store/activity.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { Activity, ActivityDetail } from '@shared/models/activity.model';
import { ActivityActions } from './activity.actions';

/**
 * State shape for the Activities feature store.
 */
export interface ActivityState {
  activities: Activity[];
  selectedActivity: ActivityDetail | null;
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  loading: boolean;
  error: string | null;
}

/**
 * Initial state with sensible defaults.
 */
export const initialActivityState: ActivityState = {
  activities: [],
  selectedActivity: null,
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
  pageSize: 20,
  loading: false,
  error: null,
};

/**
 * Activities feature reducer.
 * Handles list loading, detail loading, and state clearing.
 */
export const activityReducer = createReducer(
  initialActivityState,

  // --- Load Activities ---
  on(ActivityActions.loadActivities, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(ActivityActions.loadActivitiesSuccess, (state, { page }) => ({
    ...state,
    activities: page.content,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
    currentPage: page.number,
    pageSize: page.size,
    loading: false,
    error: null,
  })),

  on(ActivityActions.loadActivitiesFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Load Activity Detail ---
  on(ActivityActions.loadActivityDetail, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(ActivityActions.loadActivityDetailSuccess, (state, { activity }) => ({
    ...state,
    selectedActivity: activity,
    loading: false,
    error: null,
  })),

  on(ActivityActions.loadActivityDetailFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Clear ---
  on(ActivityActions.clearActivities, () => ({
    ...initialActivityState,
  })),

  on(ActivityActions.clearError, (state) => ({
    ...state,
    error: null,
  }))
);
