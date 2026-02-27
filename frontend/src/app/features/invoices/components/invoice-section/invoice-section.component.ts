// frontend/src/app/features/invoices/components/invoice-section/invoice-section.component.ts
import {
  Component,
  ChangeDetectionStrategy,
  Input,
  OnInit,
  OnChanges,
  SimpleChanges,
  inject,
  signal,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { InvoiceService } from '../../services/invoice.service';
import {
  InvoiceSummary,
  InvoiceStatus,
  INVOICE_STATUS_CONFIG,
} from '@shared/models/invoice.model';

/**
 * Embedded section component for displaying invoices related to a specific payment.
 *
 * Features:
 * - Compact card layout with download buttons
 * - Intended to be used inside PaymentDetailComponent
 * - French labels and EUR currency formatting
 * - Loading and empty states
 */
@Component({
  selector: 'app-invoice-section',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './invoice-section.component.html',
  styleUrl: './invoice-section.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InvoiceSectionComponent implements OnInit, OnChanges {
  @Input() paymentId!: number;

  private readonly invoiceService = inject(InvoiceService);

  /** Status display configuration (color + French label). */
  statusConfig = INVOICE_STATUS_CONFIG;

  /** Reactive signals for component state. */
  invoices = signal<InvoiceSummary[]>([]);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  ngOnInit(): void {
    if (this.paymentId) {
      this.loadInvoices();
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['paymentId'] && !changes['paymentId'].firstChange) {
      this.loadInvoices();
    }
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
   * Load invoices for the given payment ID.
   */
  private loadInvoices(): void {
    this.loading.set(true);
    this.error.set(null);

    this.invoiceService.getInvoicesByPayment(this.paymentId).subscribe({
      next: (invoices) => {
        this.invoices.set(invoices);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Erreur lors du chargement des factures');
        this.loading.set(false);
      },
    });
  }
}
