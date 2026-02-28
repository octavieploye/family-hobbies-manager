// frontend/src/app/features/dashboard/components/dashboard/dashboard.component.ts
import { Component, ChangeDetectionStrategy, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, takeUntil, switchMap, of } from 'rxjs';

import { DashboardService } from '../../services/dashboard.service';
import { UpcomingSessionsComponent } from '../upcoming-sessions/upcoming-sessions.component';
import { SubscriptionsOverviewComponent } from '../subscriptions-overview/subscriptions-overview.component';
import { AttendanceOverviewComponent } from '../attendance-overview/attendance-overview.component';
import { DashboardData, SubscriptionSummary, MemberAttendance } from '@shared/models/dashboard.model';

/**
 * Main dashboard component with responsive Material card grid.
 *
 * Features:
 * - Family name and member count header
 * - Active subscriptions overview widget
 * - Upcoming sessions widget (next 7 days)
 * - Attendance rate per member widget
 * - French labels throughout
 *
 * Data aggregated from multiple API endpoints via DashboardService.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatIconModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    UpcomingSessionsComponent,
    SubscriptionsOverviewComponent,
    AttendanceOverviewComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardComponent implements OnInit, OnDestroy {
  private readonly dashboardService = inject(DashboardService);
  private readonly destroy$ = new Subject<void>();

  /** Signals for template binding (Angular 17 signals instead of async pipe). */
  familyName = signal<string>('');
  memberCount = signal<number>(0);
  activeSubscriptions = signal<SubscriptionSummary[]>([]);
  attendanceOverview = signal<MemberAttendance[]>([]);
  loading = signal<boolean>(true);
  error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadDashboardData();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Load all dashboard data by aggregating multiple API calls.
   */
  private loadDashboardData(): void {
    this.loading.set(true);
    this.error.set(null);

    this.dashboardService.getFamily().pipe(
      takeUntil(this.destroy$),
      switchMap((family) => {
        this.familyName.set(family.name);
        this.memberCount.set(family.members?.length ?? 0);

        // Load subscriptions for the family
        return this.dashboardService.getFamilySubscriptions(family.id).pipe(
          switchMap((subscriptions) => {
            this.activeSubscriptions.set(
              this.dashboardService.mapToSubscriptionSummaries(subscriptions)
            );

            // Load attendance summaries for all members
            const memberIds = (family.members || []).map((m) => m.id);
            return this.dashboardService.getMemberAttendanceSummaries(memberIds);
          })
        );
      })
    ).subscribe({
      next: (summaries) => {
        this.attendanceOverview.set(
          this.dashboardService.mapToMemberAttendance(summaries)
        );
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || err?.message || 'Erreur lors du chargement du tableau de bord');
        this.loading.set(false);
      },
    });
  }
}
