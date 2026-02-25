// frontend/src/app/core/auth/store/auth.selectors.ts
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AuthState } from '../models/auth.models';

/**
 * Feature selector for the 'auth' state slice.
 * The feature key 'auth' must match the key used when registering
 * the reducer in the app config (provideStore({ auth: authReducer })).
 */
export const selectAuthState = createFeatureSelector<AuthState>('auth');

/**
 * Select whether the user is currently authenticated.
 * Used by guards, nav components, and conditional UI elements.
 */
export const selectIsAuthenticated = createSelector(
  selectAuthState,
  (state) => state.isAuthenticated
);

/**
 * Select the current access token.
 * Used by the JWT interceptor as a fallback (normally reads from localStorage).
 */
export const selectAccessToken = createSelector(
  selectAuthState,
  (state) => state.accessToken
);

/**
 * Select the loading flag.
 * Used by login/register components to show a spinner.
 */
export const selectAuthLoading = createSelector(
  selectAuthState,
  (state) => state.loading
);

/**
 * Select the error message.
 * Used by login/register components to display error feedback.
 */
export const selectAuthError = createSelector(
  selectAuthState,
  (state) => state.error
);
