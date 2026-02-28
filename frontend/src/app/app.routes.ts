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
    path: 'dashboard',
    loadChildren: () =>
      import('./features/dashboard/dashboard.routes').then(
        (m) => m.DASHBOARD_ROUTES
      ),
    canActivate: [authGuard],
  },
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
    path: 'subscriptions',
    loadChildren: () =>
      import('./features/subscriptions/subscriptions.routes').then(
        (m) => m.SUBSCRIPTION_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: 'attendance',
    loadChildren: () =>
      import('./features/attendance/attendance.routes').then(
        (m) => m.ATTENDANCE_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: 'payments',
    loadChildren: () =>
      import('./features/payments/payments.routes').then(
        (m) => m.PAYMENT_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: 'invoices',
    loadChildren: () =>
      import('./features/invoices/invoices.routes').then(
        (m) => m.INVOICE_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: 'notifications',
    loadChildren: () =>
      import('./features/notifications/notifications.routes').then(
        (m) => m.NOTIFICATION_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: 'settings',
    loadChildren: () =>
      import('./features/settings/settings.routes').then(
        (m) => m.SETTINGS_ROUTES
      ),
    canActivate: [authGuard],
  },
  {
    path: '',
    redirectTo: 'associations',
    pathMatch: 'full',
  },
];
