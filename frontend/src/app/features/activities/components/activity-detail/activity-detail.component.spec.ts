// frontend/src/app/features/activities/components/activity-detail/activity-detail.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { ActivityDetailComponent } from './activity-detail.component';
import { ActivityState, initialActivityState } from '../../store/activity.reducer';
import { ActivityDetail, Session } from '@shared/models/activity.model';

describe('ActivityDetailComponent', () => {
  let component: ActivityDetailComponent;
  let fixture: ComponentFixture<ActivityDetailComponent>;
  let store: MockStore;

  const mockSession: Session = {
    id: 1,
    activityId: 5,
    dayOfWeek: 'TUESDAY',
    startTime: '17:00',
    endTime: '18:00',
    location: 'Piscine municipale',
    instructorName: 'Jean Dupont',
    maxCapacity: 20,
    active: true,
  };

  const mockDetail: ActivityDetail = {
    id: 5,
    associationId: 1,
    associationName: 'Lyon Natation Metropole',
    name: 'Natation enfants 6-10 ans',
    description: 'Cours de natation pour enfants de 6 a 10 ans',
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

  const initialState: { activities: ActivityState } = {
    activities: {
      ...initialActivityState,
      selectedActivity: mockDetail,
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActivityDetailComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideMockStore({ initialState }),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => {
                  if (key === 'associationId') return '1';
                  if (key === 'id') return '5';
                  return null;
                },
              },
            },
          },
        },
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(ActivityDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch loadActivityDetail on init', () => {
    expect(store.dispatch).toHaveBeenCalled();
  });

  it('should display activity name', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Natation enfants 6-10 ans');
  });

  it('should display association name', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Lyon Natation Metropole');
  });

  it('should display back button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Retour');
  });

  it('should display subscribe button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain("S'inscrire");
  });
});
