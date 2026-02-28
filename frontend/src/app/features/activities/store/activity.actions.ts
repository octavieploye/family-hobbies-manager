// frontend/src/app/features/activities/store/activity.actions.ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import {
  Activity,
  ActivityDetail,
  ActivitySearchRequest,
} from '@shared/models/activity.model';
import { PageResponse } from '../../association/models/association.model';

/**
 * NgRx action group for the Activities feature.
 *
 * Follows the [Source] Event naming convention.
 * Each async operation has a triplet: trigger / success / failure.
 */
export const ActivityActions = createActionGroup({
  source: 'Activities',
  events: {
    // --- List ---
    'Load Activities': props<{ request: ActivitySearchRequest }>(),
    'Load Activities Success': props<{ page: PageResponse<Activity> }>(),
    'Load Activities Failure': props<{ error: string }>(),

    // --- Detail ---
    'Load Activity Detail': props<{ associationId: number; activityId: number }>(),
    'Load Activity Detail Success': props<{ activity: ActivityDetail }>(),
    'Load Activity Detail Failure': props<{ error: string }>(),

    // --- Clear ---
    'Clear Activities': emptyProps(),
    'Clear Error': emptyProps(),
  },
});
