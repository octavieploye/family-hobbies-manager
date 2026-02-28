// frontend/src/app/features/dashboard/components/attendance-overview/attendance-overview.component.ts
import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatListModule } from '@angular/material/list';

import { MemberAttendance } from '@shared/models/dashboard.model';

/**
 * Widget: Attendance rate per member as progress bars.
 *
 * Displays each family member's attendance rate with color-coded progress.
 * French labels throughout.
 */
@Component({
  selector: 'app-attendance-overview',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatListModule,
  ],
  templateUrl: './attendance-overview.component.html',
  styleUrl: './attendance-overview.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AttendanceOverviewComponent {
  /** List of member attendance data to display. */
  @Input() members: MemberAttendance[] = [];

  /**
   * Get progress bar color based on attendance rate.
   */
  getProgressColor(rate: number): string {
    if (rate >= 80) {
      return 'primary';
    }
    if (rate >= 50) {
      return 'accent';
    }
    return 'warn';
  }
}
