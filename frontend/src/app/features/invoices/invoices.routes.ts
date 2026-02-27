// frontend/src/app/features/invoices/invoices.routes.ts
import { Routes } from '@angular/router';

/**
 * Lazy-loaded routes for the invoices feature.
 *
 * All invoice routes require authentication (authGuard applied at app.routes.ts level).
 */
export const INVOICE_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./components/invoice-list/invoice-list.component').then(
        (m) => m.InvoiceListComponent
      ),
    title: 'Mes factures',
  },
];
