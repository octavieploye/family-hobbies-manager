// frontend/src/app/core/auth/store/auth.effects.spec.ts
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { provideMockActions } from '@ngrx/effects/testing';
import { Action } from '@ngrx/store';
import { Observable, of, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { AuthResponse } from '../models/auth.models';
import * as AuthActions from './auth.actions';
import * as AuthEffects from './auth.effects';

/**
 * Unit tests for auth NgRx effects (functional style).
 *
 * Story: H-013 — Missing auth.effects.ts
 * Tests: 8 test methods
 *
 * These tests verify:
 * 1. login$ calls authService.login and dispatches loginSuccess on success
 * 2. login$ dispatches loginFailure on error
 * 3. loginSuccess$ stores tokens and navigates to /dashboard
 * 4. register$ calls authService.register and dispatches registerSuccess on success
 * 5. register$ dispatches registerFailure on error
 * 6. registerSuccess$ stores tokens and navigates to /dashboard
 * 7. refresh$ calls authService.refreshToken and dispatches refreshSuccess on success
 * 8. logout$ calls authService.logout
 */
describe('Auth Effects', () => {
  let actions$: Observable<Action>;
  let authServiceSpy: jest.Mocked<AuthService>;
  let routerSpy: jest.Mocked<Router>;

  const mockAuthResponse: AuthResponse = {
    accessToken: 'test-access-token',
    refreshToken: 'test-refresh-token',
    tokenType: 'Bearer',
    expiresIn: 3600,
  };

  beforeEach(() => {
    authServiceSpy = {
      login: jest.fn(),
      register: jest.fn(),
      refreshToken: jest.fn(),
      logout: jest.fn(),
      storeTokens: jest.fn(),
      getAccessToken: jest.fn(),
      getRefreshToken: jest.fn(),
      isAuthenticated: jest.fn(),
    } as unknown as jest.Mocked<AuthService>;

    routerSpy = {
      navigate: jest.fn(),
    } as unknown as jest.Mocked<Router>;

    TestBed.configureTestingModule({
      providers: [
        provideMockActions(() => actions$),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  describe('login$', () => {
    it('shouldDispatchLoginSuccessOnSuccessfulLogin', (done) => {
      authServiceSpy.login.mockReturnValue(of(mockAuthResponse));

      actions$ = of(AuthActions.login({ email: 'test@example.com', password: 'password123' }));

      TestBed.runInInjectionContext(() => {
        const effect$ = AuthEffects.login$(actions$, authServiceSpy);
        effect$.subscribe((action) => {
          expect(action).toEqual(AuthActions.loginSuccess({ response: mockAuthResponse }));
          expect(authServiceSpy.login).toHaveBeenCalledWith({
            email: 'test@example.com',
            password: 'password123',
          });
          done();
        });
      });
    });

    it('shouldDispatchLoginFailureOnError', (done) => {
      const errorResponse = { error: { message: 'Invalid credentials' } };
      authServiceSpy.login.mockReturnValue(throwError(() => errorResponse));

      actions$ = of(AuthActions.login({ email: 'test@example.com', password: 'wrong' }));

      TestBed.runInInjectionContext(() => {
        const effect$ = AuthEffects.login$(actions$, authServiceSpy);
        effect$.subscribe((action) => {
          expect(action).toEqual(AuthActions.loginFailure({ error: 'Invalid credentials' }));
          done();
        });
      });
    });
  });

  describe('loginSuccess$', () => {
    it('shouldStoreTokensAndNavigateToDashboard', (done) => {
      actions$ = of(AuthActions.loginSuccess({ response: mockAuthResponse }));

      TestBed.runInInjectionContext(() => {
        const effect$ = AuthEffects.loginSuccess$(actions$, authServiceSpy, routerSpy);
        effect$.subscribe(() => {
          expect(authServiceSpy.storeTokens).toHaveBeenCalledWith(
            'test-access-token',
            'test-refresh-token'
          );
          expect(routerSpy.navigate).toHaveBeenCalledWith(['/dashboard']);
          done();
        });
      });
    });
  });

  describe('register$', () => {
    it('shouldDispatchRegisterSuccessOnSuccessfulRegistration', (done) => {
      authServiceSpy.register.mockReturnValue(of(mockAuthResponse));

      actions$ = of(
        AuthActions.register({
          email: 'new@example.com',
          password: 'password123',
          firstName: 'Jean',
          lastName: 'Dupont',
        })
      );

      TestBed.runInInjectionContext(() => {
        const effect$ = AuthEffects.register$(actions$, authServiceSpy);
        effect$.subscribe((action) => {
          expect(action).toEqual(AuthActions.registerSuccess({ response: mockAuthResponse }));
          expect(authServiceSpy.register).toHaveBeenCalledWith({
            email: 'new@example.com',
            password: 'password123',
            firstName: 'Jean',
            lastName: 'Dupont',
            phone: undefined,
          });
          done();
        });
      });
    });

    it('shouldDispatchRegisterFailureOnError', (done) => {
      const errorResponse = { error: { message: 'Email already exists' } };
      authServiceSpy.register.mockReturnValue(throwError(() => errorResponse));

      actions$ = of(
        AuthActions.register({
          email: 'existing@example.com',
          password: 'password123',
          firstName: 'Jean',
          lastName: 'Dupont',
        })
      );

      TestBed.runInInjectionContext(() => {
        const effect$ = AuthEffects.register$(actions$, authServiceSpy);
        effect$.subscribe((action) => {
          expect(action).toEqual(AuthActions.registerFailure({ error: 'Email already exists' }));
          done();
        });
      });
    });
  });

  describe('registerSuccess$', () => {
    it('shouldStoreTokensAndNavigateToDashboard', (done) => {
      actions$ = of(AuthActions.registerSuccess({ response: mockAuthResponse }));

      TestBed.runInInjectionContext(() => {
        const effect$ = AuthEffects.registerSuccess$(actions$, authServiceSpy, routerSpy);
        effect$.subscribe(() => {
          expect(authServiceSpy.storeTokens).toHaveBeenCalledWith(
            'test-access-token',
            'test-refresh-token'
          );
          expect(routerSpy.navigate).toHaveBeenCalledWith(['/dashboard']);
          done();
        });
      });
    });
  });

  describe('refresh$', () => {
    it('shouldDispatchRefreshSuccessOnSuccessfulRefresh', (done) => {
      authServiceSpy.refreshToken.mockReturnValue(of(mockAuthResponse));

      actions$ = of(AuthActions.refresh());

      TestBed.runInInjectionContext(() => {
        const effect$ = AuthEffects.refresh$(actions$, authServiceSpy);
        effect$.subscribe((action) => {
          expect(action).toEqual(AuthActions.refreshSuccess({ response: mockAuthResponse }));
          expect(authServiceSpy.refreshToken).toHaveBeenCalled();
          done();
        });
      });
    });
  });

  describe('logout$', () => {
    it('shouldCallAuthServiceLogout', (done) => {
      actions$ = of(AuthActions.logout());

      TestBed.runInInjectionContext(() => {
        const effect$ = AuthEffects.logout$(actions$, authServiceSpy);
        effect$.subscribe(() => {
          expect(authServiceSpy.logout).toHaveBeenCalled();
          done();
        });
      });
    });
  });
});
