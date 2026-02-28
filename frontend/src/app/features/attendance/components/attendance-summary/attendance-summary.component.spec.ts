// frontend/src/app/features/attendance/components/attendance-summary/attendance-summary.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { ActivatedRoute } from '@angular/router';
import { AttendanceSummaryComponent } from './attendance-summary.component';
import { AttendanceState, initialAttendanceState } from '../../store/attendance.reducer';
import { AttendanceSummary } from '@shared/models/attendance.model';

describe('AttendanceSummaryComponent', () => {
  let component: AttendanceSummaryComponent;
  let fixture: ComponentFixture<AttendanceSummaryComponent>;
  let store: MockStore;

  const mockSummary: AttendanceSummary = {
    familyMemberId: 5,
    memberFirstName: 'Marie',
    memberLastName: 'Dupont',
    totalSessions: 10,
    presentCount: 8,
    absentCount: 1,
    excusedCount: 1,
    lateCount: 0,
    attendanceRate: 80,
  };

  const initialState: { attendance: AttendanceState } = {
    attendance: { ...initialAttendanceState, summary: mockSummary },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttendanceSummaryComponent, NoopAnimationsModule],
      providers: [
        provideMockStore({ initialState }),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => key === 'memberId' ? '5' : null,
              },
            },
          },
        },
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(AttendanceSummaryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch loadMemberSummary on init', () => {
    expect(store.dispatch).toHaveBeenCalled();
  });

  it('should display page title', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('sum\u00e9 de pr\u00e9sence');
  });

  it('should return correct progress color based on rate', () => {
    expect(component.getProgressColor(85)).toBe('primary');
    expect(component.getProgressColor(60)).toBe('accent');
    expect(component.getProgressColor(30)).toBe('warn');
  });
});
