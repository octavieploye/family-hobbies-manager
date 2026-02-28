// frontend/src/app/features/attendance/components/attendance-sheet/attendance-sheet.component.ts
import { Component, ChangeDetectionStrategy, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormControl } from '@angular/forms';
import { Store } from '@ngrx/store';
import { MatTableModule } from '@angular/material/table';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

import { AttendanceActions } from '../../store/attendance.actions';
import {
  selectAttendanceRecords,
  selectAttendanceLoading,
  selectAttendanceError,
} from '../../store/attendance.selectors';
import {
  Attendance,
  AttendanceStatus,
  AttendanceMark,
  BulkAttendanceRequest,
  ATTENDANCE_STATUS_CONFIG,
} from '@shared/models/attendance.model';
import {
  getAttendanceStatusLabel,
  getAttendanceStatusIcon,
  getAttendanceStatusColor,
} from '@shared/utils/attendance-display.utils';

/**
 * Spreadsheet-style attendance sheet component.
 *
 * Features:
 * - Material table with rows=members, columns=dates
 * - Status selection via dropdown per cell
 * - Bulk save button to persist all changes at once
 * - French labels throughout
 *
 * All data flows through the NgRx store.
 */
@Component({
  selector: 'app-attendance-sheet',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatSnackBarModule,
  ],
  templateUrl: './attendance-sheet.component.html',
  styleUrl: './attendance-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AttendanceSheetComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly snackBar = inject(MatSnackBar);

  /** Observable of attendance records from the store. */
  records$ = this.store.select(selectAttendanceRecords);

  /** Observable of the loading flag. */
  loading$ = this.store.select(selectAttendanceLoading);

  /** Observable of the current error message. */
  error$ = this.store.select(selectAttendanceError);

  /** Status configuration for display. */
  statusConfig = ATTENDANCE_STATUS_CONFIG;

  /** Available status options. */
  statusOptions: AttendanceStatus[] = ['PRESENT', 'ABSENT', 'EXCUSED', 'LATE'];

  /** Columns for the attendance table. */
  displayedColumns: string[] = ['memberName', 'status', 'note'];

  /** Session ID for loading data. */
  sessionIdControl = new FormControl<number | null>(null);

  /** Date control for selecting session date. */
  dateControl = new FormControl<string>(new Date().toISOString().split('T')[0]);

  /** Tracks local edits before bulk save. Key: attendance record id. */
  editedStatuses: Map<number, AttendanceStatus> = new Map();

  /** Tracks local note edits. Key: attendance record id. */
  editedNotes: Map<number, string> = new Map();

  ngOnInit(): void {
    // Initial load if session ID is provided
  }

  ngOnDestroy(): void {
    this.store.dispatch(AttendanceActions.clearAttendance());
  }

  /**
   * Load attendance for the selected session and date.
   */
  onLoadSession(): void {
    const sessionId = this.sessionIdControl.value;
    const date = this.dateControl.value;
    if (sessionId && date) {
      this.editedStatuses.clear();
      this.editedNotes.clear();
      this.store.dispatch(AttendanceActions.loadSessionAttendance({ sessionId, date }));
    }
  }

  /**
   * Get the French label for a status.
   */
  getStatusLabel(status: AttendanceStatus): string {
    return getAttendanceStatusLabel(status);
  }

  /**
   * Get the icon for a status.
   */
  getStatusIcon(status: AttendanceStatus): string {
    return getAttendanceStatusIcon(status);
  }

  /**
   * Get the color for a status.
   */
  getStatusColor(status: AttendanceStatus): string {
    return getAttendanceStatusColor(status);
  }

  /**
   * Track a status change for a record (local edit, not yet saved).
   */
  onStatusChange(record: Attendance, status: AttendanceStatus): void {
    this.editedStatuses.set(record.id, status);
  }

  /**
   * Track a note change for a record (local edit, not yet saved).
   */
  onNoteChange(record: Attendance, note: string): void {
    this.editedNotes.set(record.id, note);
  }

  /**
   * Get the effective status for a record (edited or original).
   */
  getEffectiveStatus(record: Attendance): AttendanceStatus {
    return this.editedStatuses.get(record.id) ?? record.status;
  }

  /**
   * Get the effective note for a record (edited or original).
   */
  getEffectiveNote(record: Attendance): string {
    return this.editedNotes.get(record.id) ?? record.note ?? '';
  }

  /**
   * Check if there are unsaved changes.
   */
  hasChanges(): boolean {
    return this.editedStatuses.size > 0 || this.editedNotes.size > 0;
  }

  /**
   * Save all changes as a bulk operation.
   */
  onBulkSave(records: Attendance[]): void {
    const sessionId = this.sessionIdControl.value;
    const date = this.dateControl.value;
    if (!sessionId || !date) {
      return;
    }

    const marks: AttendanceMark[] = records.map((r) => ({
      familyMemberId: r.familyMemberId,
      subscriptionId: r.subscriptionId,
      status: this.getEffectiveStatus(r),
      note: this.getEffectiveNote(r) || undefined,
    }));

    const request: BulkAttendanceRequest = {
      sessionId,
      sessionDate: date,
      marks,
    };

    this.store.dispatch(AttendanceActions.bulkMarkAttendance({ request }));
    this.editedStatuses.clear();
    this.editedNotes.clear();
    this.snackBar.open('Pr\u00e9sences enregistr\u00e9es', 'Fermer', { duration: 3000 });
  }
}
