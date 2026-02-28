// frontend/src/app/features/settings/settings.routes.ts
import { Routes } from '@angular/router';

/**
 * Lazy-loaded routes for the settings feature.
 *
 * All settings routes require authentication (authGuard applied at app.routes.ts level).
 */
export const SETTINGS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./components/settings-page/settings-page.component').then(
        (m) => m.SettingsPageComponent
      ),
    title: 'Param\u00e8tres',
  },
];
