// frontend/src/app/features/payments/components/payment-detail/payment-detail.component.ts
import { Component, ChangeDetectionStrategy, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';

import { PaymentActions } from '../../store/payment.actions';
import {
  selectSelectedPayment,
  selectDetailLoading,
  selectPaymentError,
} from '../../store/payment.selectors';
import {
  PaymentStatus,
  PAYMENT_STATUS_CONFIG,
} from '@shared/models/payment.model';

/**
 * Timeline step definition for payment status progression.
 */
interface TimelineStep {
  status: PaymentStatus;
  label: string;
  icon: string;
}

/**
 * Standalone detail component for viewing a single payment.
 *
 * Features:
 * - Payment information in a card layout
 * - Status timeline showing progression
 * - Reference section for IDs
 * - Back navigation button
 */
@Component({
  selector: 'app-payment-detail',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    MatDividerModule,
  ],
  templateUrl: './payment-detail.component.html',
  styleUrl: './payment-detail.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentDetailComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Observable of the selected payment detail. */
  payment$ = this.store.select(selectSelectedPayment);

  /** Observable of the detail loading flag. */
  loading$ = this.store.select(selectDetailLoading);

  /** Observable of the current error message. */
  error$ = this.store.select(selectPaymentError);

  /** Status display configuration. */
  statusConfig = PAYMENT_STATUS_CONFIG;

  /** Timeline steps for payment status progression. */
  timelineSteps: TimelineStep[] = [
    { status: PaymentStatus.PENDING, label: 'En attente', icon: 'hourglass_empty' },
    { status: PaymentStatus.AUTHORIZED, label: 'Autorise', icon: 'check_circle_outline' },
    { status: PaymentStatus.COMPLETED, label: 'Complete', icon: 'done_all' },
  ];

  ngOnInit(): void {
    const paymentId = Number(this.route.snapshot.paramMap.get('id'));
    if (paymentId) {
      this.store.dispatch(PaymentActions.loadPaymentDetail({ paymentId }));
    }
  }

  ngOnDestroy(): void {
    this.store.dispatch(PaymentActions.clearSelectedPayment());
  }

  /**
   * Navigate back to the payment list.
   */
  goBack(): void {
    this.router.navigate(['/payments']);
  }

  /**
   * Format amount in EUR with French locale.
   */
  formatAmount(amount: number): string {
    return new Intl.NumberFormat('fr-FR', {
      style: 'currency',
      currency: 'EUR',
    }).format(amount);
  }

  /**
   * Get the status label in French.
   */
  getStatusLabel(status: PaymentStatus): string {
    return this.statusConfig[status]?.label ?? status;
  }

  /**
   * Get the status chip color.
   */
  getStatusColor(status: PaymentStatus): string {
    return this.statusConfig[status]?.color ?? 'basic';
  }

  /**
   * Check if a timeline step has been reached based on the current status.
   */
  isStepReached(stepStatus: PaymentStatus, currentStatus: PaymentStatus): boolean {
    const order = [PaymentStatus.PENDING, PaymentStatus.AUTHORIZED, PaymentStatus.COMPLETED];
    const stepIndex = order.indexOf(stepStatus);
    const currentIndex = order.indexOf(currentStatus);
    return currentIndex >= stepIndex && currentIndex >= 0 && stepIndex >= 0;
  }
}
