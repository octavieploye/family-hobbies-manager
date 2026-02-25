// frontend/src/app/core/auth/store/auth.actions.ts
import { createAction, props } from '@ngrx/store';
import { AuthResponse } from '../models/auth.models';

// --- Login ---
export const login = createAction(
  '[Auth] Login',
  props<{ email: string; password: string }>()
);

export const loginSuccess = createAction(
  '[Auth] Login Success',
  props<{ response: AuthResponse }>()
);

export const loginFailure = createAction(
  '[Auth] Login Failure',
  props<{ error: string }>()
);

// --- Register ---
export const register = createAction(
  '[Auth] Register',
  props<{
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    phone?: string;
  }>()
);

export const registerSuccess = createAction(
  '[Auth] Register Success',
  props<{ response: AuthResponse }>()
);

export const registerFailure = createAction(
  '[Auth] Register Failure',
  props<{ error: string }>()
);

// --- Refresh ---
export const refresh = createAction('[Auth] Refresh');

export const refreshSuccess = createAction(
  '[Auth] Refresh Success',
  props<{ response: AuthResponse }>()
);

export const refreshFailure = createAction(
  '[Auth] Refresh Failure',
  props<{ error: string }>()
);

// --- Logout ---
export const logout = createAction('[Auth] Logout');

// --- Init (restore tokens from localStorage on app start) ---
export const initAuth = createAction(
  '[Auth] Init',
  props<{ accessToken: string | null; refreshToken: string | null }>()
);
