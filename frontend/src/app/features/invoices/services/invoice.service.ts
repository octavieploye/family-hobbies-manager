// frontend/src/app/features/invoices/services/invoice.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@environments/environment';
import {
  InvoiceResponse,
  InvoiceSummary,
  InvoicePage,
} from '@shared/models/invoice.model';

/**
 * Service for invoice-related HTTP operations.
 *
 * All endpoints target the payment-service via the API gateway.
 * Authentication is handled by the JWT interceptor.
 */
@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/invoices`;

  /**
   * Load a single invoice by its ID.
   */
  getInvoice(id: number): Observable<InvoiceResponse> {
    return this.http.get<InvoiceResponse>(`${this.baseUrl}/${id}`);
  }

  /**
   * Load paginated invoices for a given user.
   */
  getInvoicesByUser(userId: number, page: number, size: number): Observable<InvoicePage> {
    const params = new HttpParams()
      .set('userId', userId.toString())
      .set('page', page.toString())
      .set('size', size.toString());

    return this.http.get<InvoicePage>(this.baseUrl, { params });
  }

  /**
   * Load all invoices related to a specific payment.
   */
  getInvoicesByPayment(paymentId: number): Observable<InvoiceSummary[]> {
    return this.http.get<InvoiceSummary[]>(`${this.baseUrl}/by-payment/${paymentId}`);
  }

  /**
   * Download an invoice as a PDF blob.
   */
  downloadPdf(invoiceId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/${invoiceId}/download`, {
      responseType: 'blob',
    });
  }
}
