// frontend/src/app/features/family/family.routes.ts
import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { familyReducer } from './store/family.reducer';
import * as familyEffects from './store/family.effects';

/**
 * Family feature routes.
 *
 * Lazy-loaded from app.routes.ts.
 * Registers the NgRx family store and effects at the feature level.
 */
export const FAMILY_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./components/family-dashboard/family-dashboard.component').then(
        (m) => m.FamilyDashboardComponent
      ),
    providers: [
      provideState('family', familyReducer),
      provideEffects(familyEffects),
    ],
  },
];
