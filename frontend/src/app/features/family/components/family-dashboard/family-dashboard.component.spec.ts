// frontend/src/app/features/family/components/family-dashboard/family-dashboard.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { FamilyDashboardComponent } from './family-dashboard.component';
import { FamilyState } from '../../store/family.reducer';
import { FamilyActions } from '../../store/family.actions';
import { Family } from '../../models/family.model';

/**
 * Unit tests for FamilyDashboardComponent.
 *
 * Story: S2-002 — Angular Family Feature
 * Tests: 3 test methods
 *
 * These tests verify:
 * 1. Component creates successfully
 * 2. Dispatches loadFamily on init
 * 3. Dispatches createFamily when form is submitted
 */
describe('FamilyDashboardComponent', () => {
  let component: FamilyDashboardComponent;
  let fixture: ComponentFixture<FamilyDashboardComponent>;
  let store: MockStore;

  const initialState: { family: FamilyState } = {
    family: {
      family: null,
      loading: false,
      error: null,
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FamilyDashboardComponent],
      providers: [
        provideMockStore({ initialState }),
        provideNoopAnimations(),
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(FamilyDashboardComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch loadFamily on init', () => {
    fixture.detectChanges();

    expect(store.dispatch).toHaveBeenCalledWith(FamilyActions.loadFamily());
  });

  it('should dispatch createFamily when form is valid and submitted', () => {
    fixture.detectChanges();

    component.familyNameForm.setValue({ name: 'Famille Dupont' });
    component.onCreateFamily();

    expect(store.dispatch).toHaveBeenCalledWith(
      FamilyActions.createFamily({ request: { name: 'Famille Dupont' } })
    );
  });
});
