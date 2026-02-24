import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { catchError, throwError } from 'rxjs';
import { parseHttpError } from '../handlers/http-error.handler';
import { ErrorCode } from '../models';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const snackBar = inject(MatSnackBar);
  const liveAnnouncer = inject(LiveAnnouncer);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const parsed = parseHttpError(error);

      // Handle 401 — redirect to login
      if (parsed.errorCode === ErrorCode.UNAUTHORIZED) {
        router.navigate(['/auth/login'], {
          queryParams: { returnUrl: router.url },
        });
      }

      // Handle 403 — redirect to forbidden page
      if (parsed.errorCode === ErrorCode.FORBIDDEN) {
        router.navigate(['/forbidden']);
      }

      // Show toast notification for user-facing errors
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
        details: parsed.details,
        retryable: parsed.isRetryable,
      });

      return throwError(() => parsed);
    })
  );
};
