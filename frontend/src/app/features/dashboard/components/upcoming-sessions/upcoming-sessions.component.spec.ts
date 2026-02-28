// frontend/src/app/features/dashboard/components/upcoming-sessions/upcoming-sessions.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { UpcomingSessionsComponent } from './upcoming-sessions.component';
import { UpcomingSession } from '@shared/models/dashboard.model';

describe('UpcomingSessionsComponent', () => {
  let component: UpcomingSessionsComponent;
  let fixture: ComponentFixture<UpcomingSessionsComponent>;

  const mockSession: UpcomingSession = {
    sessionId: 1,
    activityName: 'Natation enfants',
    associationName: 'Lyon Natation',
    dayOfWeek: 'Lundi',
    startTime: '14:00',
    endTime: '15:00',
    location: 'Piscine municipale',
    memberNames: ['Marie Dupont'],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UpcomingSessionsComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(UpcomingSessionsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display empty state when no sessions', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Aucune s\u00e9ance pr\u00e9vue');
  });

  it('should display session data when sessions provided', () => {
    fixture.componentRef.setInput('sessions', [mockSession]);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Natation enfants');
    expect(compiled.textContent).toContain('Lyon Natation');
  });
});
