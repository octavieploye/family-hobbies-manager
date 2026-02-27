// frontend/src/app/features/association/association.routes.ts
import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { associationReducer } from './store/association.reducer';
import * as associationEffects from './store/association.effects';

/**
 * Lazy-loaded routes for the association feature.
 *
 * All association routes are PUBLIC (no authGuard required).
 * The feature NgRx store and effects are registered at the route level
 * so they are only loaded when the user navigates to /associations.
 */
export const ASSOCIATION_ROUTES: Routes = [
  {
    path: '',
    providers: [
      provideState('associations', associationReducer),
      provideEffects(associationEffects),
    ],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./components/association-list/association-list.component').then(
            (m) => m.AssociationListComponent
          ),
        title: 'Rechercher des associations',
      },
      {
        path: ':id',
        loadComponent: () =>
          import('./components/association-detail/association-detail.component').then(
            (m) => m.AssociationDetailComponent
          ),
        title: 'Detail association',
      },
    ],
  },
];
