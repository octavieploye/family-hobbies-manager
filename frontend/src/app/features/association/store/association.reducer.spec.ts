// frontend/src/app/features/association/store/association.reducer.spec.ts
import {
  associationReducer,
  initialState,
  AssociationState,
} from './association.reducer';
import { AssociationActions } from './association.actions';
import {
  Association,
  AssociationDetail,
  PageResponse,
} from '../models/association.model';

describe('AssociationReducer', () => {
  const mockAssociation: Association = {
    id: 1,
    name: 'Club Sportif Paris',
    slug: 'club-sportif-paris',
    city: 'Paris',
    postalCode: '75001',
    category: 'Sport',
    status: 'ACTIVE',
  };

  const mockPage: PageResponse<Association> = {
    content: [mockAssociation],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 20,
  };

  const mockDetail: AssociationDetail = {
    id: 1,
    name: 'Club Sportif Paris',
    slug: 'club-sportif-paris',
    description: 'Un club sportif au coeur de Paris.',
    city: 'Paris',
    postalCode: '75001',
    category: 'Sport',
    status: 'ACTIVE',
    createdAt: '2024-01-15T10:00:00',
    updatedAt: '2024-06-01T14:30:00',
  };

  it('should return the initial state on unknown action', () => {
    const action = { type: 'UNKNOWN' } as any;
    const state = associationReducer(undefined, action);
    expect(state).toEqual(initialState);
  });

  describe('Search Associations', () => {
    it('should set loading to true on searchAssociations', () => {
      const action = AssociationActions.searchAssociations({
        request: { city: 'Paris' },
      });
      const state = associationReducer(initialState, action);

      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('should populate associations on searchAssociationsSuccess', () => {
      const action = AssociationActions.searchAssociationsSuccess({
        page: mockPage,
      });
      const state = associationReducer(
        { ...initialState, loading: true },
        action
      );

      expect(state.associations).toEqual([mockAssociation]);
      expect(state.totalElements).toBe(1);
      expect(state.totalPages).toBe(1);
      expect(state.currentPage).toBe(0);
      expect(state.pageSize).toBe(20);
      expect(state.loading).toBe(false);
      expect(state.error).toBeNull();
    });

    it('should set error on searchAssociationsFailure', () => {
      const action = AssociationActions.searchAssociationsFailure({
        error: 'Network error',
      });
      const state = associationReducer(
        { ...initialState, loading: true },
        action
      );

      expect(state.loading).toBe(false);
      expect(state.error).toBe('Network error');
    });
  });

  describe('Load Association Detail', () => {
    it('should set loading on loadAssociationDetail', () => {
      const action = AssociationActions.loadAssociationDetail({ id: 1 });
      const state = associationReducer(initialState, action);

      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('should set selectedAssociation on loadAssociationDetailSuccess', () => {
      const action = AssociationActions.loadAssociationDetailSuccess({
        association: mockDetail,
      });
      const state = associationReducer(
        { ...initialState, loading: true },
        action
      );

      expect(state.selectedAssociation).toEqual(mockDetail);
      expect(state.loading).toBe(false);
      expect(state.error).toBeNull();
    });
  });

  describe('Clear', () => {
    it('should reset to initial state on clearSearch', () => {
      const populatedState: AssociationState = {
        associations: [mockAssociation],
        selectedAssociation: mockDetail,
        totalElements: 1,
        totalPages: 1,
        currentPage: 0,
        pageSize: 20,
        loading: false,
        error: null,
      };
      const action = AssociationActions.clearSearch();
      const state = associationReducer(populatedState, action);

      expect(state).toEqual(initialState);
    });

    it('should handle pagination update via search success', () => {
      const largePage: PageResponse<Association> = {
        content: [mockAssociation],
        totalElements: 100,
        totalPages: 5,
        number: 2,
        size: 20,
      };
      const action = AssociationActions.searchAssociationsSuccess({
        page: largePage,
      });
      const state = associationReducer(initialState, action);

      expect(state.totalElements).toBe(100);
      expect(state.totalPages).toBe(5);
      expect(state.currentPage).toBe(2);
      expect(state.pageSize).toBe(20);
    });
  });
});
