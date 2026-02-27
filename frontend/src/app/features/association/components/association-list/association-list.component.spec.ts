// frontend/src/app/features/association/components/association-list/association-list.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { AssociationListComponent } from './association-list.component';
import { AssociationState } from '../../store/association.reducer';

describe('AssociationListComponent', () => {
  let component: AssociationListComponent;
  let fixture: ComponentFixture<AssociationListComponent>;
  let store: MockStore;

  const initialState: { associations: AssociationState } = {
    associations: {
      associations: [],
      selectedAssociation: null,
      totalElements: 0,
      totalPages: 0,
      currentPage: 0,
      pageSize: 20,
      loading: false,
      error: null,
    },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssociationListComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideMockStore({ initialState }),
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(AssociationListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch search on init', () => {
    expect(store.dispatch).toHaveBeenCalled();
  });

  it('should display page title', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Rechercher des associations');
  });

  it('should display empty state when no results', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Aucune association trouvee');
  });
});
