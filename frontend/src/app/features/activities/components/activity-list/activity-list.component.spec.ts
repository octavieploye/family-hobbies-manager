// frontend/src/app/features/activities/components/activity-list/activity-list.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { ActivityListComponent } from './activity-list.component';
import { ActivityState, initialActivityState } from '../../store/activity.reducer';

describe('ActivityListComponent', () => {
  let component: ActivityListComponent;
  let fixture: ComponentFixture<ActivityListComponent>;
  let store: MockStore;

  const initialState: { activities: ActivityState } = {
    activities: { ...initialActivityState },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActivityListComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideMockStore({ initialState }),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'associationId' ? '1' : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(ActivityListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should set associationId from route params', () => {
    expect(component.associationId).toBe(1);
  });

  it('should dispatch loadActivities on init', () => {
    expect(store.dispatch).toHaveBeenCalled();
  });

  it('should display page title', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Activit');
  });

  it('should display empty state when no activities', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Aucune activit');
  });

  it('should display back button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Retour');
  });
});
