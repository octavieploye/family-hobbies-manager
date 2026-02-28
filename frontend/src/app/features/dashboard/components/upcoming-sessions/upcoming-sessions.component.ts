// frontend/src/app/features/dashboard/components/upcoming-sessions/upcoming-sessions.component.ts
import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatDividerModule } from '@angular/material/divider';

import { UpcomingSession } from '@shared/models/dashboard.model';

/**
 * Widget: Upcoming sessions for the next 7 days.
 *
 * Displays day, time, activity, and member names.
 * French labels throughout.
 */
@Component({
  selector: 'app-upcoming-sessions',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatIconModule,
    MatListModule,
    MatDividerModule,
  ],
  templateUrl: './upcoming-sessions.component.html',
  styleUrl: './upcoming-sessions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UpcomingSessionsComponent {
  /** List of upcoming sessions to display. */
  @Input() sessions: UpcomingSession[] = [];
}
