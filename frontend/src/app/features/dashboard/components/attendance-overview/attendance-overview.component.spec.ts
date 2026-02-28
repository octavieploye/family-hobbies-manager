// frontend/src/app/features/dashboard/components/attendance-overview/attendance-overview.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { AttendanceOverviewComponent } from './attendance-overview.component';
import { MemberAttendance } from '@shared/models/dashboard.model';

describe('AttendanceOverviewComponent', () => {
  let component: AttendanceOverviewComponent;
  let fixture: ComponentFixture<AttendanceOverviewComponent>;

  const mockMember: MemberAttendance = {
    memberId: 5,
    memberName: 'Marie Dupont',
    attendanceRate: 80,
    totalSessions: 10,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttendanceOverviewComponent, NoopAnimationsModule],
    }).compileComponents();

    fixture = TestBed.createComponent(AttendanceOverviewComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display empty state when no members', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Aucune donn\u00e9e de pr\u00e9sence');
  });

  it('should return correct progress color based on rate', () => {
    expect(component.getProgressColor(85)).toBe('primary');
    expect(component.getProgressColor(60)).toBe('accent');
    expect(component.getProgressColor(30)).toBe('warn');
  });
});
