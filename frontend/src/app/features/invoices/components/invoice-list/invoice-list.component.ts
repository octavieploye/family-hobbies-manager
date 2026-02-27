// frontend/src/app/features/invoices/components/invoice-list/invoice-list.component.ts
import { Component, ChangeDetectionStrategy, OnInit, inject, signal } from '@angular/core';
import { CommonModule, CurrencyPipe, DatePipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';

import { InvoiceService } from '../../services/invoice.service';
import {
  InvoiceSummary,
  InvoiceStatus,
  INVOICE_STATUS_CONFIG,
} from '@shared/models/invoice.model';

/**
 * Standalone list component for viewing user invoices.
 *
 * Features:
 * - Material table displaying invoice summaries
 * - Paginator for navigation
 * - PDF download via blob URL
 * - French labels and EUR currency formatting
 * - Loading spinner and error/empty states
 */
@Component({
  selector: 'app-invoice-list',
  standalone: true,
  imports: [
    CommonModule,
    CurrencyPipe,
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    MatChipsModule,
    MatIconModule,
  ],
  templateUrl: './invoice-list.component.html',
  styleUrl: './invoice-list.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvoiceListComponent implements OnInit {
  private readonly invoiceService = inject(InvoiceService);

  /** Columns displayed in the table. */
  displayedColumns: string[] = [
    'invoiceNumber',
    'status',
    'buyerName',
    'amount',
    'issuedAt',
    'actions',
  ];

  /** Status display configuration (color + French label). */
  statusConfig = INVOICE_STATUS_CONFIG;

  /** Demo user ID (hardcoded for portfolio demo). */
  private readonly userId = 1;

  /** Reactive signals for component state. */
  invoices = signal<InvoiceSummary[]>([]);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);
  totalElements = signal<number>(0);
  currentPage = signal<number>(0);
  pageSize = signal<number>(10);

  ngOnInit(): void {
    this.loadInvoices();
  }

  /**
   * Handle page change from MatPaginator.
   */
  onPageChange(event: PageEvent): void {
    this.currentPage.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.loadInvoices();
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
  getStatusLabel(status: InvoiceStatus): string {
    return this.statusConfig[status]?.label ?? status;
  }

  /**
   * Get the status chip color.
   */
  getStatusColor(status: InvoiceStatus): string {
    return this.statusConfig[status]?.color ?? 'basic';
  }

  /**
   * Download the invoice PDF.
   * Creates a temporary anchor element, triggers click, then revokes the blob URL.
   */
  downloadInvoice(invoice: InvoiceSummary): void {
    this.invoiceService.downloadPdf(invoice.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${invoice.invoiceNumber}.pdf`;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.error.set('Erreur lors du telechargement de la facture');
      },
    });
  }

  /**
   * Load invoices from the backend with current pagination.
   */
  private loadInvoices(): void {
    this.loading.set(true);
    this.error.set(null);

    this.invoiceService
      .getInvoicesByUser(this.userId, this.currentPage(), this.pageSize())
      .subscribe({
        next: (page) => {
          this.invoices.set(page.content);
          this.totalElements.set(page.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.error.set('Erreur lors du chargement des factures');
          this.loading.set(false);
        },
      });
  }
}
