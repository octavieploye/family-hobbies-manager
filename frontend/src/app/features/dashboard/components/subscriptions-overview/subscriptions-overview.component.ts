// frontend/src/app/features/dashboard/components/subscriptions-overview/subscriptions-overview.component.ts
import { Component, ChangeDetectionStrategy, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatChipsModule } from '@angular/material/chips';
import { MatDividerModule } from '@angular/material/divider';

import { SubscriptionSummary } from '@shared/models/dashboard.model';
import { SubscriptionStatus, SUBSCRIPTION_STATUS_CONFIG } from '@shared/models/subscription.model';

/**
 * Widget: Active subscriptions overview.
 *
 * Displays activity name + association for each active subscription.
 * French labels throughout.
 */
@Component({
  selector: 'app-subscriptions-overview',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatIconModule,
    MatListModule,
    MatChipsModule,
    MatDividerModule,
  ],
  templateUrl: './subscriptions-overview.component.html',
  styleUrl: './subscriptions-overview.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubscriptionsOverviewComponent {
  /** List of active subscriptions to display. */
  @Input() subscriptions: SubscriptionSummary[] = [];

  /**
   * Get status label in French using shared subscription status config.
   */
  getStatusLabel(status: string): string {
    return SUBSCRIPTION_STATUS_CONFIG[status as SubscriptionStatus]?.label || status;
  }
}
