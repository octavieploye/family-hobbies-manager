// frontend/src/app/features/attendance/components/attendance-history/attendance-history.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { ActivatedRoute } from '@angular/router';
import { AttendanceHistoryComponent } from './attendance-history.component';
import { AttendanceState, initialAttendanceState } from '../../store/attendance.reducer';
import { Attendance } from '@shared/models/attendance.model';

describe('AttendanceHistoryComponent', () => {
  let component: AttendanceHistoryComponent;
  let fixture: ComponentFixture<AttendanceHistoryComponent>;
  let store: MockStore;

  const mockAttendance: Attendance = {
    id: 1,
    sessionId: 10,
    familyMemberId: 5,
    memberFirstName: 'Marie',
    memberLastName: 'Dupont',
    subscriptionId: 3,
    sessionDate: '2024-09-15',
    status: 'PRESENT',
    note: null,
    markedBy: 1,
    createdAt: '2024-09-15T10:00:00',
    updatedAt: '2024-09-15T10:00:00',
  };

  const initialState: { attendance: AttendanceState } = {
    attendance: { ...initialAttendanceState, records: [mockAttendance] },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttendanceHistoryComponent, NoopAnimationsModule],
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

    fixture = TestBed.createComponent(AttendanceHistoryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should dispatch loadMemberHistory on init', () => {
    expect(store.dispatch).toHaveBeenCalled();
  });

  it('should display page title', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Historique de pr\u00e9sence');
  });

  it('should return correct status labels and icons', () => {
    expect(component.getStatusLabel('PRESENT')).toBe('Pr\u00e9sent');
    expect(component.getStatusIcon('ABSENT')).toBe('cancel');
    expect(component.getStatusColor('LATE')).toBe('#2196f3');
  });
});
