// frontend/src/app/features/payments/components/payment-list/payment-list.component.ts
import { Component, ChangeDetectionStrategy, OnInit, inject } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';

import { PaymentActions } from '../../store/payment.actions';
import {
  selectPayments,
  selectListLoading,
  selectPaymentError,
  selectPagination,
  selectStatusFilter,
} from '../../store/payment.selectors';
import {
  PaymentStatus,
  PaymentSummary,
  PAYMENT_STATUS_CONFIG,
} from '@shared/models/payment.model';

/**
 * Standalone list component for viewing and filtering payments.
 *
 * Features:
 * - Material table displaying payment summaries
 * - Status filter via mat-select
 * - Paginator for navigation
 * - French labels and EUR currency formatting
 * - Loading spinner and error/empty states
 *
 * All data flows through the NgRx store.
 */
@Component({
  selector: 'app-payment-list',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatSelectModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatIconModule,
    MatFormFieldModule,
  ],
  templateUrl: './payment-list.component.html',
  styleUrl: './payment-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PaymentListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);

  /** Observable of payment summaries from the store. */
  payments$ = this.store.select(selectPayments);

  /** Observable of the list loading flag. */
  loading$ = this.store.select(selectListLoading);

  /** Observable of the current error message. */
  error$ = this.store.select(selectPaymentError);

  /** Observable of the pagination state for MatPaginator. */
  pagination$ = this.store.select(selectPagination);

  /** Observable of the current status filter. */
  statusFilter$ = this.store.select(selectStatusFilter);

  /** Columns displayed in the table. */
  displayedColumns: string[] = [
    'createdAt',
    'familyMemberName',
    'associationName',
    'amount',
    'status',
    'actions',
  ];

  /** Available payment statuses for the filter dropdown. */
  paymentStatuses = Object.values(PaymentStatus);

  /** Status display configuration (color + French label). */
  statusConfig = PAYMENT_STATUS_CONFIG;

  /** Demo family ID (hardcoded for portfolio demo). */
  private readonly familyId = 1;

  ngOnInit(): void {
    this.loadPayments();
  }

  /**
   * Dispatch a filter change and reload payments.
   */
  onStatusFilterChange(status: PaymentStatus | null): void {
    this.store.dispatch(PaymentActions.setStatusFilter({ status }));
    this.loadPayments(0, status);
  }

  /**
   * Handle page change from MatPaginator.
   */
  onPageChange(event: PageEvent): void {
    this.loadPayments(event.pageIndex, undefined, event.pageSize);
  }

  /**
   * Navigate to payment detail page.
   */
  viewPayment(payment: PaymentSummary): void {
    this.router.navigate(['/payments', payment.id]);
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
   * Dispatch loadPayments action with current filters.
   */
  private loadPayments(
    page: number = 0,
    status?: PaymentStatus | null,
    size: number = 10
  ): void {
    this.store.dispatch(
      PaymentActions.loadPayments({
        params: {
          familyId: this.familyId,
          status: status !== undefined ? (status ?? undefined) : undefined,
          page,
          size,
        },
      })
    );
  }
}
