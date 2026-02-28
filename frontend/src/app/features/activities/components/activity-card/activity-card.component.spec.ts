// frontend/src/app/features/activities/components/activity-card/activity-card.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivityCardComponent } from './activity-card.component';
import { Activity } from '@shared/models/activity.model';
import { provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
describe('ActivityCardComponent', () => {
  let component: ActivityCardComponent;
  let fixture: ComponentFixture<ActivityCardComponent>;

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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ActivityCardComponent, NoopAnimationsModule],
      providers: [provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(ActivityCardComponent);
    component = fixture.componentInstance;
    component.activity = mockActivity;
    component.associationId = 1;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display activity name', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Natation enfants');
  });

  it('should display category', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Sport');
  });

  it('should display session count', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('2 seance(s)');
  });

  it('should compute price in euros correctly', () => {
    expect(component.priceInEuros).toBe(150);
  });

  it('should compute age range correctly', () => {
    expect(component.ageRange).toBe('6 - 10 ans');
  });

  it('should return null for age range when no ages set', () => {
    component.activity = {
      ...mockActivity,
      minAge: null,
      maxAge: null,
    };
    expect(component.ageRange).toBeNull();
  });
});
