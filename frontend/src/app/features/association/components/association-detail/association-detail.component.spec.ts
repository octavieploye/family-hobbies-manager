// frontend/src/app/features/association/components/association-detail/association-detail.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, ActivatedRoute } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { AssociationDetailComponent } from './association-detail.component';
import { AssociationState } from '../../store/association.reducer';
import { AssociationDetail } from '../../models/association.model';

describe('AssociationDetailComponent', () => {
  let component: AssociationDetailComponent;
  let fixture: ComponentFixture<AssociationDetailComponent>;
  let store: MockStore;

  const mockDetail: AssociationDetail = {
    id: 1,
    name: 'Club Sportif Paris',
    slug: 'club-sportif-paris',
    description: 'Un club sportif au coeur de Paris.',
    address: '12 Rue de Rivoli',
    city: 'Paris',
    postalCode: '75001',
    department: 'Paris',
    region: 'Ile-de-France',
    phone: '+33 1 23 45 67 89',
    email: 'contact@clubsportifparis.fr',
    website: 'https://clubsportifparis.fr',
    category: 'Sport',
    status: 'ACTIVE',
    createdAt: '2024-01-15T10:00:00',
    updatedAt: '2024-06-01T14:30:00',
  };

  const initialState: { associations: AssociationState } = {
    associations: {
      associations: [],
      selectedAssociation: mockDetail,
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
      imports: [AssociationDetailComponent, NoopAnimationsModule],
      providers: [
        provideRouter([]),
        provideMockStore({ initialState }),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? '1' : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(AssociationDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch loadAssociationDetail on init', () => {
    expect(store.dispatch).toHaveBeenCalled();
  });

  it('should display association name', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Club Sportif Paris');
  });

  it('should display back button', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Retour aux associations');
  });

  it('should display description', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Un club sportif au coeur de Paris.');
  });

  it('should display contact info', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('+33 1 23 45 67 89');
    expect(compiled.textContent).toContain('contact@clubsportifparis.fr');
  });
});
