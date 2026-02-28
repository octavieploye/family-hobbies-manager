// frontend/src/app/features/dashboard/dashboard.routes.ts
import { Routes } from '@angular/router';

/**
 * Lazy-loaded routes for the dashboard feature.
 *
 * All dashboard routes require authentication (authGuard applied at app.routes.ts level).
 * The dashboard aggregates data from multiple services; no feature-level NgRx store needed
 * since the DashboardComponent uses DashboardService directly with signals.
 */
export const DASHBOARD_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./components/dashboard/dashboard.component').then(
        (m) => m.DashboardComponent
      ),
    title: 'Tableau de bord',
  },
];
