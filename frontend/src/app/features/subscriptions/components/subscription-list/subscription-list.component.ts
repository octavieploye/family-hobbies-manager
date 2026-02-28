// frontend/src/app/features/subscriptions/components/subscription-list/subscription-list.component.ts
import { Component, ChangeDetectionStrategy, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { Store } from '@ngrx/store';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ReactiveFormsModule, FormControl } from '@angular/forms';

import { SubscriptionActions } from '../../store/subscription.actions';
import {
  selectFilteredSubscriptions,
  selectSubscriptionLoading,
  selectSubscriptionError,
  selectStatusFilter,
} from '../../store/subscription.selectors';
import {
  Subscription,
  SubscriptionStatus,
  SUBSCRIPTION_STATUS_CONFIG,
} from '@shared/models/subscription.model';
import { FamilyService } from '../../../family/services/family.service';

/**
 * Standalone list component displaying user's subscriptions.
 *
 * Features:
 * - Material table with subscription data
 * - Status badges with color coding per SUBSCRIPTION_STATUS_CONFIG
 * - Filter by status
 * - Cancel button for active/pending subscriptions
 *
 * All data flows through the NgRx store.
 */
@Component({
  selector: 'app-subscription-list',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatTableModule,
    MatChipsModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './subscription-list.component.html',
  styleUrl: './subscription-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubscriptionListComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly familyService = inject(FamilyService);
  private readonly destroy$ = new Subject<void>();

  /** Observable of filtered subscriptions from the store. */
  subscriptions$ = this.store.select(selectFilteredSubscriptions);

  /** Observable of the loading flag. */
  loading$ = this.store.select(selectSubscriptionLoading);

  /** Observable of the current error message. */
  error$ = this.store.select(selectSubscriptionError);

  /** Columns for the subscription table. */
  displayedColumns: string[] = [
    'activityName',
    'associationName',
    'memberName',
    'subscriptionType',
    'status',
    'startDate',
    'actions',
  ];

  /** Status filter options. */
  statusOptions: SubscriptionStatus[] = ['PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED'];

  /** Status configuration for display. */
  statusConfig = SUBSCRIPTION_STATUS_CONFIG;

  /** Status filter form control. */
  statusFilter = new FormControl<SubscriptionStatus | null>(null);

  ngOnInit(): void {
    // Fetch the authenticated user's family to get the real familyId
    this.familyService.getMyFamily()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (family) => {
          this.store.dispatch(SubscriptionActions.loadSubscriptions({ familyId: family.id }));
        },
        error: () => {
          // If family cannot be loaded, dispatch with error state handled by reducer
          this.store.dispatch(SubscriptionActions.loadSubscriptionsFailure({
            error: 'Impossible de charger votre famille. Veuillez vous reconnecter.',
          }));
        },
      });

    // Listen to filter changes
    this.statusFilter.valueChanges
      .pipe(takeUntil(this.destroy$))
      .subscribe((status) => {
        this.store.dispatch(SubscriptionActions.setStatusFilter({ status: status || null }));
      });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.store.dispatch(SubscriptionActions.clearSubscriptions());
  }

  /**
   * Get the status label in French.
   */
  getStatusLabel(status: SubscriptionStatus): string {
    return this.statusConfig[status]?.label || status;
  }

  /**
   * Get the Material color for a status.
   */
  getStatusColor(status: SubscriptionStatus): string {
    return this.statusConfig[status]?.color || '';
  }

  /**
   * Check if a subscription can be cancelled.
   */
  canCancel(subscription: Subscription): boolean {
    return subscription.status === 'PENDING' || subscription.status === 'ACTIVE';
  }

  /**
   * Cancel a subscription after prompting for a reason.
   */
  onCancel(subscription: Subscription): void {
    const reason = window.prompt(
      'Veuillez indiquer la raison de l\'annulation (optionnel) :'
    );
    // If the user clicked "Cancel" on the prompt, abort the operation
    if (reason === null) {
      return;
    }
    this.store.dispatch(
      SubscriptionActions.cancelSubscription({
        subscriptionId: subscription.id,
        reason: reason || undefined,
      })
    );
  }

  /**
   * Get subscription type label in French.
   */
  getTypeLabel(type: string): string {
    return type === 'ADHESION' ? 'Adh\u00e9sion' : 'Cotisation';
  }
}
