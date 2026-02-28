// frontend/src/app/features/attendance/components/attendance-history/attendance-history.component.ts
import { Component, ChangeDetectionStrategy, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';

import { AttendanceActions } from '../../store/attendance.actions';
import {
  selectAttendanceRecords,
  selectAttendanceLoading,
  selectAttendanceError,
} from '../../store/attendance.selectors';
import {
  AttendanceStatus,
  ATTENDANCE_STATUS_CONFIG,
} from '@shared/models/attendance.model';
import {
  getAttendanceStatusLabel,
  getAttendanceStatusIcon,
  getAttendanceStatusColor,
} from '@shared/utils/attendance-display.utils';

/**
 * Timeline view for a single member's attendance history.
 *
 * Features:
 * - MatList with status icons and colors
 * - Session date and note display
 * - Link to attendance summary
 * - French labels throughout
 *
 * Data loaded via memberId route parameter.
 */
@Component({
  selector: 'app-attendance-history',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatDividerModule,
  ],
  templateUrl: './attendance-history.component.html',
  styleUrl: './attendance-history.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AttendanceHistoryComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  /** Observable of attendance records from the store. */
  records$ = this.store.select(selectAttendanceRecords);

  /** Observable of the loading flag. */
  loading$ = this.store.select(selectAttendanceLoading);

  /** Observable of the current error message. */
  error$ = this.store.select(selectAttendanceError);

  /** Status configuration for display. */
  statusConfig = ATTENDANCE_STATUS_CONFIG;

  /** Current member ID from route. */
  memberId: number = 0;

  ngOnInit(): void {
    this.memberId = Number(this.route.snapshot.paramMap.get('memberId'));
    if (this.memberId) {
      this.store.dispatch(AttendanceActions.loadMemberHistory({ memberId: this.memberId }));
    }
  }

  ngOnDestroy(): void {
    this.store.dispatch(AttendanceActions.clearAttendance());
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
}
