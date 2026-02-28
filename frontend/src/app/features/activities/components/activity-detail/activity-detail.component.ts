// frontend/src/app/features/activities/components/activity-detail/activity-detail.component.ts
import { Component, ChangeDetectionStrategy, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule, CurrencyPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';

import { ActivityActions } from '../../store/activity.actions';
import {
  selectSelectedActivity,
  selectActivityLoading,
  selectActivityError,
} from '../../store/activity.selectors';
import { ActivityDetail } from '@shared/models/activity.model';
import { formatAgeRange, formatPriceInEuros } from '@shared/utils/activity-format.utils';

/**
 * Standalone detail component showing full information about an activity.
 *
 * Loads the activity detail by associationId and activityId from route params.
 * Dispatches loadActivityDetail action on init.
 * Displays all fields, sessions schedule table, and subscribe button.
 */
@Component({
  selector: 'app-activity-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatDividerModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    CurrencyPipe,
  ],
  templateUrl: './activity-detail.component.html',
  styleUrl: './activity-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActivityDetailComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  readonly dialog = inject(MatDialog);

  /** Route parameter values. */
  associationId = 0;
  activityId = 0;

  /** Observable of the selected activity detail. */
  activity$ = this.store.select(selectSelectedActivity);

  /** Observable of loading state. */
  loading$ = this.store.select(selectActivityLoading);

  /** Observable of error state. */
  error$ = this.store.select(selectActivityError);

  /** Columns for session schedule table. */
  sessionColumns: string[] = ['dayOfWeek', 'startTime', 'endTime', 'location', 'instructorName'];

  /** French labels for days of week. */
  dayLabels: Record<string, string> = {
    MONDAY: 'Lundi',
    TUESDAY: 'Mardi',
    WEDNESDAY: 'Mercredi',
    THURSDAY: 'Jeudi',
    FRIDAY: 'Vendredi',
    SATURDAY: 'Samedi',
    SUNDAY: 'Dimanche',
  };

  ngOnInit(): void {
    const associationIdParam = this.route.snapshot.paramMap.get('associationId');
    const activityIdParam = this.route.snapshot.paramMap.get('id');

    if (associationIdParam && activityIdParam) {
      this.associationId = Number(associationIdParam);
      this.activityId = Number(activityIdParam);

      if (!isNaN(this.associationId) && !isNaN(this.activityId)) {
        this.store.dispatch(
          ActivityActions.loadActivityDetail({
            associationId: this.associationId,
            activityId: this.activityId,
          })
        );
      }
    }
  }

  ngOnDestroy(): void {
    this.store.dispatch(ActivityActions.clearError());
  }

  /**
   * Format price from cents to euros.
   */
  priceInEuros(priceCents: number): number {
    return formatPriceInEuros(priceCents);
  }

  /**
   * Build age range display string.
   */
  ageRange(activity: ActivityDetail): string | null {
    return formatAgeRange(activity.minAge, activity.maxAge);
  }

  /**
   * Open the subscribe dialog.
   * This will be wired to the actual SubscribeDialogComponent in S3-005.
   */
  openSubscribeDialog(activity: ActivityDetail): void {
    // Will be wired in S3-005 subscription feature
    import('../../../subscriptions/components/subscribe-dialog/subscribe-dialog.component').then(
      (m) => {
        this.dialog.open(m.SubscribeDialogComponent, {
          width: '500px',
          data: {
            activityId: activity.id,
            activityName: activity.name,
            associationName: activity.associationName,
          },
        });
      }
    );
  }
}
