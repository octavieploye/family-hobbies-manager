// frontend/src/app/features/attendance/components/attendance-sheet/attendance-sheet.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MockStore, provideMockStore } from '@ngrx/store/testing';
import { AttendanceSheetComponent } from './attendance-sheet.component';
import { AttendanceState, initialAttendanceState } from '../../store/attendance.reducer';
import { Attendance } from '@shared/models/attendance.model';

describe('AttendanceSheetComponent', () => {
  let component: AttendanceSheetComponent;
  let fixture: ComponentFixture<AttendanceSheetComponent>;
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
    attendance: { ...initialAttendanceState },
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AttendanceSheetComponent, NoopAnimationsModule],
      providers: [
        provideMockStore({ initialState }),
      ],
    }).compileComponents();

    store = TestBed.inject(MockStore);
    jest.spyOn(store, 'dispatch');

    fixture = TestBed.createComponent(AttendanceSheetComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display page title', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Feuille de pr\u00e9sence');
  });

  it('should return correct status labels in French', () => {
    expect(component.getStatusLabel('PRESENT')).toBe('Pr\u00e9sent');
    expect(component.getStatusLabel('ABSENT')).toBe('Absent');
    expect(component.getStatusLabel('EXCUSED')).toBe('Excus\u00e9');
    expect(component.getStatusLabel('LATE')).toBe('En retard');
  });

  it('should track status changes locally', () => {
    component.onStatusChange(mockAttendance, 'ABSENT');
    expect(component.getEffectiveStatus(mockAttendance)).toBe('ABSENT');
    expect(component.hasChanges()).toBe(true);
  });
});
