// frontend/src/app/core/auth/guards/auth.guard.ts
import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Functional route guard (Angular 17+ style).
 *
 * Usage in route config:
 *   { path: 'dashboard', canActivate: [authGuard], component: DashboardComponent }
 *
 * Behavior:
 * - Returns true if the user has a token in localStorage.
 * - Redirects to /auth/login with returnUrl query param if not authenticated.
 */
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Preserve the attempted URL so the login page can redirect back after success
  return router.createUrlTree(['/auth/login'], {
    queryParams: { returnUrl: state.url },
  });
};
