// frontend/src/app/core/auth/interceptors/jwt.interceptor.ts
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, switchMap, throwError } from 'rxjs';

/**
 * Functional HTTP interceptor (Angular 17+ style).
 *
 * Responsibilities:
 * 1. Attach Bearer token to every non-public request.
 * 2. On 401: attempt token refresh, then retry the original request.
 * 3. On refresh failure: force logout and redirect to /auth/login.
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  // Paths that must NOT carry an Authorization header.
  // These are public endpoints that the backend allows without JWT.
  const publicPaths = ['/auth/login', '/auth/register', '/auth/refresh'];
  const isPublicRequest = publicPaths.some((path) => req.url.includes(path));

  if (isPublicRequest) {
    return next(req);
  }

  // Clone the request and attach the Bearer token if available
  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Only attempt refresh if the error is 401 and this is NOT already a refresh request
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
            // Refresh failed -- force logout and let the user re-authenticate
            authService.logout();
            return throwError(() => refreshError);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
