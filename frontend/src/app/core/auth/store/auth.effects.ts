// frontend/src/app/core/auth/store/auth.effects.ts
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, exhaustMap, map, tap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import * as AuthActions from './auth.actions';

/**
 * Login effect.
 *
 * Listens for [Auth] Login action, calls AuthService.login(),
 * then dispatches loginSuccess or loginFailure.
 *
 * Uses exhaustMap to ignore duplicate login requests while one is in-flight.
 */
export const login$ = createEffect(
  (actions$ = inject(Actions), authService = inject(AuthService)) =>
    actions$.pipe(
      ofType(AuthActions.login),
      exhaustMap(({ email, password }) =>
        authService.login({ email, password }).pipe(
          map((response) => AuthActions.loginSuccess({ response })),
          catchError((error) =>
            of(AuthActions.loginFailure({
              error: error?.error?.message || error?.message || 'Login failed',
            }))
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Login success side-effect.
 *
 * After a successful login, persist tokens in storage and navigate to dashboard.
 * This is a non-dispatching effect (dispatch: false).
 */
export const loginSuccess$ = createEffect(
  (actions$ = inject(Actions), authService = inject(AuthService), router = inject(Router)) =>
    actions$.pipe(
      ofType(AuthActions.loginSuccess),
      tap(({ response }) => {
        authService.storeTokens(response.accessToken, response.refreshToken);
        router.navigate(['/dashboard']);
      })
    ),
  { functional: true, dispatch: false }
);

/**
 * Register effect.
 *
 * Listens for [Auth] Register action, calls AuthService.register(),
 * then dispatches registerSuccess or registerFailure.
 */
export const register$ = createEffect(
  (actions$ = inject(Actions), authService = inject(AuthService)) =>
    actions$.pipe(
      ofType(AuthActions.register),
      exhaustMap(({ email, password, firstName, lastName, phone }) =>
        authService.register({ email, password, firstName, lastName, phone }).pipe(
          map((response) => AuthActions.registerSuccess({ response })),
          catchError((error) =>
            of(AuthActions.registerFailure({
              error: error?.error?.message || error?.message || 'Registration failed',
            }))
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Register success side-effect.
 *
 * After a successful registration, persist tokens and navigate to dashboard.
 * This is a non-dispatching effect (dispatch: false).
 */
export const registerSuccess$ = createEffect(
  (actions$ = inject(Actions), authService = inject(AuthService), router = inject(Router)) =>
    actions$.pipe(
      ofType(AuthActions.registerSuccess),
      tap(({ response }) => {
        authService.storeTokens(response.accessToken, response.refreshToken);
        router.navigate(['/dashboard']);
      })
    ),
  { functional: true, dispatch: false }
);

/**
 * Refresh token effect.
 *
 * Listens for [Auth] Refresh action, calls AuthService.refreshToken(),
 * then dispatches refreshSuccess or refreshFailure.
 *
 * Token storage is handled inside AuthService.refreshToken() via tap().
 */
export const refresh$ = createEffect(
  (actions$ = inject(Actions), authService = inject(AuthService)) =>
    actions$.pipe(
      ofType(AuthActions.refresh),
      exhaustMap(() =>
        authService.refreshToken().pipe(
          map((response) => AuthActions.refreshSuccess({ response })),
          catchError((error) =>
            of(AuthActions.refreshFailure({
              error: error?.error?.message || error?.message || 'Token refresh failed',
            }))
          )
        )
      )
    ),
  { functional: true }
);

/**
 * Logout side-effect.
 *
 * Listens for [Auth] Logout action, calls AuthService.logout()
 * which clears tokens and redirects to /auth/login.
 * This is a non-dispatching effect (dispatch: false).
 */
export const logout$ = createEffect(
  (actions$ = inject(Actions), authService = inject(AuthService)) =>
    actions$.pipe(
      ofType(AuthActions.logout),
      tap(() => {
        authService.logout();
      })
    ),
  { functional: true, dispatch: false }
);
