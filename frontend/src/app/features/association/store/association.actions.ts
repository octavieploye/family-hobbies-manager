// frontend/src/app/features/association/store/association.actions.ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import {
  Association,
  AssociationDetail,
  AssociationSearchRequest,
  PageResponse,
} from '../models/association.model';

/**
 * NgRx action group for the Association feature.
 *
 * Follows the [Source] Event naming convention.
 * Each async operation has a triplet: trigger / success / failure.
 */
export const AssociationActions = createActionGroup({
  source: 'Association',
  events: {
    // --- Search ---
    'Search Associations': props<{ request: AssociationSearchRequest }>(),
    'Search Associations Success': props<{ page: PageResponse<Association> }>(),
    'Search Associations Failure': props<{ error: string }>(),

    // --- Detail ---
    'Load Association Detail': props<{ id: number }>(),
    'Load Association Detail Success': props<{ association: AssociationDetail }>(),
    'Load Association Detail Failure': props<{ error: string }>(),

    // --- Clear ---
    'Clear Search': emptyProps(),
    'Clear Error': emptyProps(),
  },
});
