// frontend/src/app/features/association/index.ts
// Barrel export for the association feature module

// Models
export {
  Association,
  AssociationDetail,
  AssociationSearchRequest,
  PageResponse,
} from './models/association.model';

// Services
export { AssociationService } from './services/association.service';

// Store — Actions
export { AssociationActions } from './store/association.actions';

// Store — Reducer
export {
  associationReducer,
  initialState as associationInitialState,
} from './store/association.reducer';
export type { AssociationState } from './store/association.reducer';

// Store — Selectors
export {
  selectAssociationState,
  selectAssociations,
  selectSelectedAssociation,
  selectTotalElements,
  selectTotalPages,
  selectCurrentPage,
  selectPageSize,
  selectAssociationLoading,
  selectAssociationError,
  selectPagination,
} from './store/association.selectors';

// Store — Effects
export * as AssociationEffects from './store/association.effects';

// Routes
export { ASSOCIATION_ROUTES } from './association.routes';

// Components
export { AssociationListComponent } from './components/association-list/association-list.component';
export { AssociationCardComponent } from './components/association-card/association-card.component';
export { AssociationDetailComponent } from './components/association-detail/association-detail.component';
