// frontend/src/app/features/activities/store/activity.reducer.spec.ts
import {
  activityReducer,
  initialActivityState,
  ActivityState,
} from './activity.reducer';
import { ActivityActions } from './activity.actions';
import { Activity, ActivityDetail, Session } from '@shared/models/activity.model';
import { PageResponse } from '../../association/models/association.model';

describe('ActivityReducer', () => {
  const mockActivity: Activity = {
    id: 1,
    name: 'Natation enfants',
    category: 'Sport',
    level: 'BEGINNER',
    minAge: 6,
    maxAge: 10,
    priceCents: 15000,
    status: 'ACTIVE',
    sessionCount: 2,
  };

  const mockPage: PageResponse<Activity> = {
    content: [mockActivity],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 20,
  };

  const mockSession: Session = {
    id: 1,
    activityId: 1,
    dayOfWeek: 'TUESDAY',
    startTime: '17:00',
    endTime: '18:00',
    location: 'Piscine municipale',
    instructorName: 'Jean Dupont',
    maxCapacity: 20,
    active: true,
  };

  const mockDetail: ActivityDetail = {
    id: 1,
    associationId: 1,
    associationName: 'Lyon Natation Metropole',
    name: 'Natation enfants 6-10 ans',
    description: 'Cours de natation pour enfants',
    category: 'Sport',
    level: 'BEGINNER',
    minAge: 6,
    maxAge: 10,
    maxCapacity: 20,
    priceCents: 15000,
    seasonStart: '2024-09-01',
    seasonEnd: '2025-06-30',
    status: 'ACTIVE',
    sessions: [mockSession],
    createdAt: '2024-01-15T10:00:00',
    updatedAt: '2024-06-01T14:30:00',
  };

  it('should return the initial state on unknown action', () => {
    const action = { type: 'UNKNOWN' } as any;
    const state = activityReducer(undefined, action);
    expect(state).toEqual(initialActivityState);
  });

  describe('Load Activities', () => {
    it('should set loading to true on loadActivities', () => {
      const action = ActivityActions.loadActivities({
        request: { associationId: 1 },
      });
      const state = activityReducer(initialActivityState, action);

      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('should populate activities on loadActivitiesSuccess', () => {
      const action = ActivityActions.loadActivitiesSuccess({
        page: mockPage,
      });
      const state = activityReducer(
        { ...initialActivityState, loading: true },
        action
      );

      expect(state.activities).toEqual([mockActivity]);
      expect(state.totalElements).toBe(1);
      expect(state.totalPages).toBe(1);
      expect(state.currentPage).toBe(0);
      expect(state.pageSize).toBe(20);
      expect(state.loading).toBe(false);
      expect(state.error).toBeNull();
    });

    it('should set error on loadActivitiesFailure', () => {
      const action = ActivityActions.loadActivitiesFailure({
        error: 'Network error',
      });
      const state = activityReducer(
        { ...initialActivityState, loading: true },
        action
      );

      expect(state.loading).toBe(false);
      expect(state.error).toBe('Network error');
    });
  });

  describe('Load Activity Detail', () => {
    it('should set loading on loadActivityDetail', () => {
      const action = ActivityActions.loadActivityDetail({
        associationId: 1,
        activityId: 1,
      });
      const state = activityReducer(initialActivityState, action);

      expect(state.loading).toBe(true);
      expect(state.error).toBeNull();
    });

    it('should set selectedActivity on loadActivityDetailSuccess', () => {
      const action = ActivityActions.loadActivityDetailSuccess({
        activity: mockDetail,
      });
      const state = activityReducer(
        { ...initialActivityState, loading: true },
        action
      );

      expect(state.selectedActivity).toEqual(mockDetail);
      expect(state.loading).toBe(false);
      expect(state.error).toBeNull();
    });

    it('should set error on loadActivityDetailFailure', () => {
      const action = ActivityActions.loadActivityDetailFailure({
        error: 'Activity not found',
      });
      const state = activityReducer(
        { ...initialActivityState, loading: true },
        action
      );

      expect(state.loading).toBe(false);
      expect(state.error).toBe('Activity not found');
    });
  });

  describe('Clear', () => {
    it('should reset to initial state on clearActivities', () => {
      const populatedState: ActivityState = {
        activities: [mockActivity],
        selectedActivity: mockDetail,
        totalElements: 1,
        totalPages: 1,
        currentPage: 0,
        pageSize: 20,
        loading: false,
        error: null,
      };
      const action = ActivityActions.clearActivities();
      const state = activityReducer(populatedState, action);

      expect(state).toEqual(initialActivityState);
    });

    it('should clear error on clearError', () => {
      const stateWithError: ActivityState = {
        ...initialActivityState,
        error: 'Some error',
      };
      const action = ActivityActions.clearError();
      const state = activityReducer(stateWithError, action);

      expect(state.error).toBeNull();
    });
  });
});
