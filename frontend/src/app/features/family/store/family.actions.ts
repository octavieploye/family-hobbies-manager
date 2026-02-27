// frontend/src/app/features/family/store/family.actions.ts
import { createActionGroup, emptyProps, props } from '@ngrx/store';
import {
  Family,
  FamilyMember,
  FamilyMemberRequest,
  FamilyRequest,
} from '../models/family.model';

/**
 * NgRx action group for the Family feature.
 *
 * Follows the [Source] Event naming convention.
 * Each operation has a trigger, success, and failure variant.
 */
export const FamilyActions = createActionGroup({
  source: 'Family',
  events: {
    // --- Load Family ---
    'Load Family': emptyProps(),
    'Load Family Success': props<{ family: Family }>(),
    'Load Family Failure': props<{ error: string }>(),

    // --- Create Family ---
    'Create Family': props<{ request: FamilyRequest }>(),
    'Create Family Success': props<{ family: Family }>(),
    'Create Family Failure': props<{ error: string }>(),

    // --- Update Family ---
    'Update Family': props<{ id: number; request: FamilyRequest }>(),
    'Update Family Success': props<{ family: Family }>(),
    'Update Family Failure': props<{ error: string }>(),

    // --- Add Member ---
    'Add Member': props<{ familyId: number; request: FamilyMemberRequest }>(),
    'Add Member Success': props<{ member: FamilyMember }>(),
    'Add Member Failure': props<{ error: string }>(),

    // --- Update Member ---
    'Update Member': props<{ memberId: number; request: FamilyMemberRequest }>(),
    'Update Member Success': props<{ member: FamilyMember }>(),
    'Update Member Failure': props<{ error: string }>(),

    // --- Remove Member ---
    'Remove Member': props<{ memberId: number }>(),
    'Remove Member Success': props<{ memberId: number }>(),
    'Remove Member Failure': props<{ error: string }>(),

    // --- Clear Error ---
    'Clear Error': emptyProps(),
  },
});
