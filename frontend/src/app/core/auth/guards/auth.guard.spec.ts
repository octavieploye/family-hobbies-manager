// frontend/src/app/core/auth/guards/auth.guard.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

/**
 * Unit tests for authGuard (Angular functional guard).
 *
 * Story: S1-006 — Implement Angular Auth Scaffolding
 * Tests: 2 test methods
 *
 * These tests verify:
 * 1. Authenticated user is allowed through (returns true)
 * 2. Unauthenticated user is redirected to /auth/login with returnUrl
 *
 * Uses TestBed.runInInjectionContext() for Angular 17+ functional guard testing.
 *
 * Review findings incorporated:
 * - F-07 (WARNING): Only 2 tests cover the auth guard. Role-based route guarding
 *   (e.g., shouldDenyUserWithInsufficientRole, shouldAllowUserWithCorrectRole) is
 *   DEFERRED to a later sprint when role-based guards are implemented. Sprint 1
 *   guard only checks isAuthenticated(), not specific roles. Role-based guards
 *   (FAMILY, ASSOCIATION, ADMIN) will be added alongside the corresponding
 *   protected feature modules.
 */
describe('authGuard', () => {
  let authServiceSpy: jest.Mocked<AuthService>;
  let routerSpy: jest.Mocked<Router>;

  const mockRoute = {} as ActivatedRouteSnapshot;

  beforeEach(() => {
    authServiceSpy = {
      isAuthenticated: jest.fn(),
      getAccessToken: jest.fn(),
      getRefreshToken: jest.fn(),
      login: jest.fn(),
      register: jest.fn(),
      refreshToken: jest.fn(),
      logout: jest.fn(),
      storeTokens: jest.fn(),
    } as unknown as jest.Mocked<AuthService>;

    routerSpy = {
      navigate: jest.fn(),
      createUrlTree: jest.fn(),
    } as unknown as jest.Mocked<Router>;

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('shouldAllowAuthenticatedUser', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);

    const mockState = { url: '/dashboard' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRoute, mockState)
    );

    expect(result).toBe(true);
  });

  it('shouldRedirectUnauthenticatedUser', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(false);

    const expectedUrlTree = {} as UrlTree;
    routerSpy.createUrlTree.mockReturnValue(expectedUrlTree);

    const mockState = { url: '/dashboard' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRoute, mockState)
    );

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/dashboard' },
    });
    expect(result).toBe(expectedUrlTree);
  });
});
