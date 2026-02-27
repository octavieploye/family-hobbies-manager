// frontend/src/app/features/family/store/family.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { Family } from '../models/family.model';
import { FamilyActions } from './family.actions';

/**
 * State shape for the family feature slice.
 */
export interface FamilyState {
  family: Family | null;
  loading: boolean;
  error: string | null;
}

/**
 * Initial state — no family loaded, not loading, no error.
 */
export const initialFamilyState: FamilyState = {
  family: null,
  loading: false,
  error: null,
};

/**
 * Family reducer.
 *
 * Handles all family-related state transitions:
 * - Load/Create/Update family
 * - Add/Update/Remove members (modifying the family.members array)
 * - Clear error
 */
export const familyReducer = createReducer(
  initialFamilyState,

  // --- Load Family ---
  on(FamilyActions.loadFamily, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(FamilyActions.loadFamilySuccess, (state, { family }) => ({
    ...state,
    family,
    loading: false,
    error: null,
  })),

  on(FamilyActions.loadFamilyFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Create Family ---
  on(FamilyActions.createFamily, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(FamilyActions.createFamilySuccess, (state, { family }) => ({
    ...state,
    family,
    loading: false,
    error: null,
  })),

  on(FamilyActions.createFamilyFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Update Family ---
  on(FamilyActions.updateFamily, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(FamilyActions.updateFamilySuccess, (state, { family }) => ({
    ...state,
    family,
    loading: false,
    error: null,
  })),

  on(FamilyActions.updateFamilyFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Add Member ---
  on(FamilyActions.addMember, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(FamilyActions.addMemberSuccess, (state, { member }) => ({
    ...state,
    family: state.family
      ? { ...state.family, members: [...state.family.members, member] }
      : null,
    loading: false,
    error: null,
  })),

  on(FamilyActions.addMemberFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Update Member ---
  on(FamilyActions.updateMember, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(FamilyActions.updateMemberSuccess, (state, { member }) => ({
    ...state,
    family: state.family
      ? {
          ...state.family,
          members: state.family.members.map((m) =>
            m.id === member.id ? member : m
          ),
        }
      : null,
    loading: false,
    error: null,
  })),

  on(FamilyActions.updateMemberFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Remove Member ---
  on(FamilyActions.removeMember, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(FamilyActions.removeMemberSuccess, (state, { memberId }) => ({
    ...state,
    family: state.family
      ? {
          ...state.family,
          members: state.family.members.filter((m) => m.id !== memberId),
        }
      : null,
    loading: false,
    error: null,
  })),

  on(FamilyActions.removeMemberFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Clear Error ---
  on(FamilyActions.clearError, (state) => ({
    ...state,
    error: null,
  }))
);
