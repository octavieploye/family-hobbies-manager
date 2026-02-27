// frontend/src/app/features/association/store/association.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { Association, AssociationDetail } from '../models/association.model';
import { AssociationActions } from './association.actions';

/**
 * State shape for the Association feature store.
 */
export interface AssociationState {
  associations: Association[];
  selectedAssociation: AssociationDetail | null;
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  loading: boolean;
  error: string | null;
}

/**
 * Initial state with sensible defaults.
 */
export const initialState: AssociationState = {
  associations: [],
  selectedAssociation: null,
  totalElements: 0,
  totalPages: 0,
  currentPage: 0,
  pageSize: 20,
  loading: false,
  error: null,
};

/**
 * Association feature reducer.
 * Handles search, detail loading, and state clearing.
 */
export const associationReducer = createReducer(
  initialState,

  // --- Search ---
  on(AssociationActions.searchAssociations, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AssociationActions.searchAssociationsSuccess, (state, { page }) => ({
    ...state,
    associations: page.content,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
    currentPage: page.number,
    pageSize: page.size,
    loading: false,
    error: null,
  })),

  on(AssociationActions.searchAssociationsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Detail ---
  on(AssociationActions.loadAssociationDetail, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AssociationActions.loadAssociationDetailSuccess, (state, { association }) => ({
    ...state,
    selectedAssociation: association,
    loading: false,
    error: null,
  })),

  on(AssociationActions.loadAssociationDetailFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  // --- Clear ---
  on(AssociationActions.clearSearch, () => ({
    ...initialState,
  })),

  on(AssociationActions.clearError, (state) => ({
    ...state,
    error: null,
  }))
);
