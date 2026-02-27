// frontend/src/app/app.routes.ts
import { Routes } from '@angular/router';
import { authGuard } from './core/auth/guards/auth.guard';

/**
 * Root application routes.
 *
 * Each feature is lazy-loaded using loadChildren for route-level code splitting.
 * Guards are applied per feature; see each feature's routes file for details.
 */
export const routes: Routes = [
  {
    path: 'associations',
    loadChildren: () =>
      import('./features/association/association.routes').then(
        (m) => m.ASSOCIATION_ROUTES
      ),
  },
  {
    path: 'family',
    loadChildren: () =>
      import('./features/family/family.routes').then(
        (m) => m.FAMILY_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: '',
    redirectTo: 'associations',
    pathMatch: 'full',
  },
];
