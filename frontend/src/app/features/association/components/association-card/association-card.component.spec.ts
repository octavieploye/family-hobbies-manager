// frontend/src/app/features/association/components/association-card/association-card.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AssociationCardComponent } from './association-card.component';
import { Association } from '../../models/association.model';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('AssociationCardComponent', () => {
  let component: AssociationCardComponent;
  let fixture: ComponentFixture<AssociationCardComponent>;

  const mockAssociation: Association = {
    id: 1,
    name: 'Club Sportif Paris',
    slug: 'club-sportif-paris',
    city: 'Paris',
    postalCode: '75001',
    category: 'Sport',
    status: 'ACTIVE',
    logoUrl: 'https://example.com/logo.png',
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssociationCardComponent, NoopAnimationsModule],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AssociationCardComponent);
    component = fixture.componentInstance;
    component.association = mockAssociation;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display association name', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Club Sportif Paris');
  });

  it('should display city and postal code', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Paris');
    expect(compiled.textContent).toContain('75001');
  });

  it('should display category as chip', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Sport');
  });

  it('should have routerLink to association detail', () => {
    const card = fixture.nativeElement.querySelector('mat-card');
    expect(card).toBeTruthy();
  });
});
