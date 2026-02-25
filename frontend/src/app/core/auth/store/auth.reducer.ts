// frontend/src/app/core/auth/store/auth.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { AuthState } from '../models/auth.models';
import * as AuthActions from './auth.actions';

export const initialState: AuthState = {
  accessToken: null,
  refreshToken: null,
  isAuthenticated: false,
  loading: false,
  error: null,
};

export const authReducer = createReducer(
  initialState,

  // --- Login ---
  on(AuthActions.login, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AuthActions.loginSuccess, (state, { response }) => ({
    ...state,
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    isAuthenticated: true,
    loading: false,
    error: null,
  })),

  on(AuthActions.loginFailure, (state, { error }) => ({
    ...state,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    loading: false,
    error,
  })),

  // --- Register ---
  on(AuthActions.register, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AuthActions.registerSuccess, (state, { response }) => ({
    ...state,
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    isAuthenticated: true,
    loading: false,
    error: null,
  })),

  on(AuthActions.registerFailure, (state, { error }) => ({
    ...state,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    loading: false,
    error,
  })),

  // --- Refresh ---
  on(AuthActions.refreshSuccess, (state, { response }) => ({
    ...state,
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    isAuthenticated: true,
    error: null,
  })),

  on(AuthActions.refreshFailure, (state, { error }) => ({
    ...state,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    error,
  })),

  // --- Logout ---
  on(AuthActions.logout, () => ({
    ...initialState,
  })),

  // --- Init (restore from localStorage) ---
  on(AuthActions.initAuth, (state, { accessToken, refreshToken }) => ({
    ...state,
    accessToken,
    refreshToken,
    isAuthenticated: accessToken !== null,
  }))
);
