// frontend/src/app/core/auth/interceptors/jwt.interceptor.ts
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Functional HTTP interceptor (Angular 17+ style).
 *
 * Single responsibility: attach the Bearer token to outgoing requests.
 *
 * All 401 response handling (token refresh, logout, redirect) is handled
 * exclusively by the errorInterceptor to avoid race conditions and
 * duplicate logout/redirect calls.
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

  return next(req);
};
