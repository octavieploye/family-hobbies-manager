// frontend/src/app/features/attendance/components/attendance-summary/attendance-summary.component.ts
import { Component, ChangeDetectionStrategy, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AttendanceActions } from '../../store/attendance.actions';
import {
  selectAttendanceSummary,
  selectAttendanceLoading,
  selectAttendanceError,
} from '../../store/attendance.selectors';
import { ATTENDANCE_STATUS_CONFIG } from '@shared/models/attendance.model';
import { getProgressColor } from '@shared/utils/attendance-display.utils';

/**
 * Attendance summary component showing stats cards.
 *
 * Features:
 * - Present/Absent/Excused/Late counts as stat cards
 * - Attendance rate as a progress bar
 * - French labels throughout
 *
 * Data loaded via memberId route parameter.
 */
@Component({
  selector: 'app-attendance-summary',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './attendance-summary.component.html',
  styleUrl: './attendance-summary.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AttendanceSummaryComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  /** Observable of attendance summary from the store. */
  summary$ = this.store.select(selectAttendanceSummary);

  /** Observable of the loading flag. */
  loading$ = this.store.select(selectAttendanceLoading);

  /** Observable of the current error message. */
  error$ = this.store.select(selectAttendanceError);

  /** Status configuration for icons and colors. */
  statusConfig = ATTENDANCE_STATUS_CONFIG;

  ngOnInit(): void {
    const memberId = Number(this.route.snapshot.paramMap.get('memberId'));
    if (memberId) {
      this.store.dispatch(AttendanceActions.loadMemberSummary({ memberId }));
    }
  }

  ngOnDestroy(): void {
    this.store.dispatch(AttendanceActions.clearAttendance());
  }

  /**
   * Get progress bar color based on attendance rate.
   */
  getProgressColor(rate: number): string {
    return getProgressColor(rate);
  }
}
