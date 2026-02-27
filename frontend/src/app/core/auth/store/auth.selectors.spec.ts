import { AuthState } from '../models/auth.models';
import {
  selectAuthState,
  selectIsAuthenticated,
  selectAccessToken,
  selectAuthLoading,
  selectAuthError,
} from './auth.selectors';

/**
 * Unit tests for auth NgRx selectors.
 *
 * Tests: 5 test methods
 *
 * These tests verify each selector returns the correct slice of state.
 * Selectors are pure functions -- no TestBed or DI needed.
 */
describe('Auth Selectors', () => {
  const authenticatedState: AuthState = {
    accessToken: 'jwt-token',
    refreshToken: 'refresh-token',
    isAuthenticated: true,
    loading: false,
    error: null,
  };

  const loadingState: AuthState = {
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    loading: true,
    error: null,
  };

  const errorState: AuthState = {
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    loading: false,
    error: 'Invalid credentials',
  };

  it('should select the entire auth state when selectAuthState used', () => {
    // given
    const appState = { auth: authenticatedState };

    // when
    const result = selectAuthState(appState);

    // then
    expect(result).toEqual(authenticatedState);
  });

  it('should return true when selectIsAuthenticated called on authenticated state', () => {
    // given
    const appState = { auth: authenticatedState };

    // when
    const result = selectIsAuthenticated(appState);

    // then
    expect(result).toBe(true);
  });

  it('should return access token when selectAccessToken called on authenticated state', () => {
    // given
    const appState = { auth: authenticatedState };

    // when
    const result = selectAccessToken(appState);

    // then
    expect(result).toBe('jwt-token');
  });

  it('should return true when selectAuthLoading called on loading state', () => {
    // given
    const appState = { auth: loadingState };

    // when
    const result = selectAuthLoading(appState);

    // then
    expect(result).toBe(true);
  });

  it('should return error message when selectAuthError called on error state', () => {
    // given
    const appState = { auth: errorState };

    // when
    const result = selectAuthError(appState);

    // then
    expect(result).toBe('Invalid credentials');
  });
});
