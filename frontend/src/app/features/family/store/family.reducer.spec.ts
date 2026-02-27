// frontend/src/app/features/family/store/family.reducer.spec.ts
import { familyReducer, initialFamilyState, FamilyState } from './family.reducer';
import { FamilyActions } from './family.actions';
import { Family, FamilyMember } from '../models/family.model';

/**
 * Unit tests for familyReducer (NgRx reducer — pure function).
 *
 * Story: S2-002 — Angular Family Feature
 * Tests: 6 test methods
 *
 * These tests verify:
 * 1. Unknown action returns initial state (default case)
 * 2. loadFamilySuccess sets family, loading=false, error=null
 * 3. createFamilySuccess sets family, loading=false, error=null
 * 4. addMemberSuccess appends new member to family.members array
 * 5. removeMemberSuccess filters out removed member from family.members array
 * 6. loadFamilyFailure sets error, loading=false
 *
 * NgRx reducers are pure functions — given a state and an action, they return
 * a new state. No TestBed or DI needed.
 */
describe('familyReducer', () => {
  const mockFamily: Family = {
    id: 1,
    name: 'Famille Dupont',
    createdBy: 100,
    members: [
      {
        id: 10,
        familyId: 1,
        firstName: 'Jean',
        lastName: 'Dupont',
        dateOfBirth: '1985-06-15',
        age: 40,
        relationship: 'PARENT',
        createdAt: '2025-01-01T00:00:00',
      },
    ],
    createdAt: '2025-01-01T00:00:00',
    updatedAt: '2025-01-01T00:00:00',
  };

  const mockNewMember: FamilyMember = {
    id: 20,
    familyId: 1,
    firstName: 'Marie',
    lastName: 'Dupont',
    dateOfBirth: '2010-03-22',
    age: 15,
    relationship: 'CHILD',
    createdAt: '2025-06-01T00:00:00',
  };

  it('should return initial state when unknown action dispatched', () => {
    const state = familyReducer(undefined, { type: 'UNKNOWN' });

    expect(state).toEqual(initialFamilyState);
    expect(state.family).toBeNull();
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should set family when loadFamilySuccess dispatched', () => {
    const state = familyReducer(
      initialFamilyState,
      FamilyActions.loadFamilySuccess({ family: mockFamily })
    );

    expect(state.family).toEqual(mockFamily);
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should set family when createFamilySuccess dispatched', () => {
    const state = familyReducer(
      initialFamilyState,
      FamilyActions.createFamilySuccess({ family: mockFamily })
    );

    expect(state.family).toEqual(mockFamily);
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should append member to family when addMemberSuccess dispatched', () => {
    const stateWithFamily: FamilyState = {
      family: { ...mockFamily },
      loading: false,
      error: null,
    };

    const state = familyReducer(
      stateWithFamily,
      FamilyActions.addMemberSuccess({ member: mockNewMember })
    );

    expect(state.family?.members.length).toBe(2);
    expect(state.family?.members[1]).toEqual(mockNewMember);
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should remove member from family when removeMemberSuccess dispatched', () => {
    const stateWithFamily: FamilyState = {
      family: { ...mockFamily, members: [...mockFamily.members, mockNewMember] },
      loading: false,
      error: null,
    };

    const state = familyReducer(
      stateWithFamily,
      FamilyActions.removeMemberSuccess({ memberId: 20 })
    );

    expect(state.family?.members.length).toBe(1);
    expect(state.family?.members.find((m) => m.id === 20)).toBeUndefined();
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('should set error when loadFamilyFailure dispatched', () => {
    // First set loading to true via loadFamily
    const loadingState = familyReducer(initialFamilyState, FamilyActions.loadFamily());
    expect(loadingState.loading).toBe(true);

    // Then simulate failure
    const state = familyReducer(
      loadingState,
      FamilyActions.loadFamilyFailure({ error: 'Failed to load family' })
    );

    expect(state.family).toBeNull();
    expect(state.loading).toBe(false);
    expect(state.error).toBe('Failed to load family');
  });
});
