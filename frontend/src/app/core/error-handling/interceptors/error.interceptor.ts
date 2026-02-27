import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../../auth/services/auth.service';
import { parseHttpError } from '../handlers/http-error.handler';
import { ErrorCode } from '../models';

/**
 * Centralized HTTP error interceptor.
 *
 * This is the SINGLE place for all HTTP error handling, including 401
 * (token refresh + logout). The jwt interceptor only attaches tokens.
 *
 * 401 flow:
 * 1. Receive 401 on a non-refresh request.
 * 2. Attempt token refresh via AuthService.
 * 3. On success: retry the original request with the new token.
 * 4. On failure: force logout (clears tokens + redirects to /auth/login).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const snackBar = inject(MatSnackBar);
  const liveAnnouncer = inject(LiveAnnouncer);
  const authService = inject(AuthService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Handle 401 — attempt token refresh, then retry or logout
      if (error.status === 401 && !req.url.includes('/auth/refresh')) {
        return authService.refreshToken().pipe(
          switchMap((response) => {
            // Retry the original request with the new access token
            const retryReq = req.clone({
              setHeaders: {
                Authorization: `Bearer ${response.accessToken}`,
              },
            });
            return next(retryReq);
          }),
          catchError((refreshError) => {
            // Refresh failed — force logout and redirect to login
            authService.logout();
            return throwError(() => parseHttpError(
              refreshError instanceof HttpErrorResponse
                ? refreshError
                : error
            ));
          })
        );
      }

      const parsed = parseHttpError(error);

      // Handle 403 — redirect to forbidden page
      if (parsed.errorCode === ErrorCode.FORBIDDEN) {
        router.navigate(['/forbidden']);
      }

      // Show toast notification for user-facing errors (skip 401 since it triggers refresh above)
      if (parsed.status !== 401) {
        snackBar.open(parsed.message, 'Fermer', {
          duration: 5000,
          panelClass: parsed.status >= 500 ? 'snackbar-error' : 'snackbar-warning',
          horizontalPosition: 'end',
          verticalPosition: 'top',
        });

        // Announce for screen readers (RGAA compliance)
        liveAnnouncer.announce(`Erreur: ${parsed.message}`, 'assertive');
      }

      // Log for debugging
      console.error(`[HTTP ${parsed.status}] ${parsed.errorCode}: ${parsed.message}`, {
        url: req.url,
        method: req.method,
        correlationId: parsed.correlationId,
        details: parsed.details,
        retryable: parsed.isRetryable,
      });

      return throwError(() => parsed);
    })
  );
};
