// frontend/src/app/core/auth/store/auth.reducer.spec.ts
import { authReducer, initialState } from './auth.reducer';
import * as AuthActions from './auth.actions';
import { AuthState } from '../models/auth.models';

/**
 * Unit tests for authReducer (NgRx reducer — pure function).
 *
 * Story: S1-006 — Implement Angular Auth Scaffolding
 * Tests: 5 test methods
 *
 * These tests verify:
 * 1. Unknown action returns initial state (default case)
 * 2. loginSuccess sets tokens, isAuthenticated=true, clears error
 * 3. loginFailure sets error, clears tokens, loading=false
 * 4. logout clears all auth state
 * 5. initAuth restores tokens from localStorage payload
 *
 * NgRx reducers are pure functions — given a state and an action, they return
 * a new state. No TestBed or DI needed.
 */
describe('authReducer', () => {
  it('should return initial state when unknown action dispatched', () => {
    const state = authReducer(undefined, { type: 'UNKNOWN' });

    expect(state).toEqual(initialState);
    expect(state.isAuthenticated).toBe(false);
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should set token and authenticated when loginSuccess dispatched', () => {
    const response = {
      accessToken: 'jwt-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    };

    const state = authReducer(
      initialState,
      AuthActions.loginSuccess({ response })
    );

    expect(state.accessToken).toBe('jwt-token');
    expect(state.refreshToken).toBe('refresh-token');
    expect(state.isAuthenticated).toBe(true);
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should set error and clear tokens when loginFailure dispatched', () => {
    // First set loading to true via login action
    const loadingState = authReducer(initialState, AuthActions.login({ email: 'a@b.com', password: 'p' }));
    expect(loadingState.loading).toBe(true);

    // Then simulate failure
    const state = authReducer(
      loadingState,
      AuthActions.loginFailure({ error: 'Invalid credentials' })
    );

    expect(state.isAuthenticated).toBe(false);
    expect(state.accessToken).toBeNull();
    expect(state.loading).toBe(false);
    expect(state.error).toBe('Invalid credentials');
  });

  it('should clear all auth state when logout dispatched', () => {
    // Start from an authenticated state
    const authenticatedState: AuthState = {
      accessToken: 'jwt-token',
      refreshToken: 'refresh-token',
      isAuthenticated: true,
      loading: false,
      error: null,
    };

    const state = authReducer(authenticatedState, AuthActions.logout());

    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should restore tokens from payload when initAuth dispatched', () => {
    const state = authReducer(
      initialState,
      AuthActions.initAuth({
        accessToken: 'stored-token',
        refreshToken: 'stored-refresh',
      })
    );

    expect(state.accessToken).toBe('stored-token');
    expect(state.refreshToken).toBe('stored-refresh');
    expect(state.isAuthenticated).toBe(true);
  });
});
