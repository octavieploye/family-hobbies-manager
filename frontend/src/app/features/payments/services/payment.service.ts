// frontend/src/app/features/payments/services/payment.service.ts
import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@environments/environment';
import {
  CheckoutRequest,
  CheckoutResponse,
  Page,
  PaymentDetail,
  PaymentListParams,
  PaymentSummary,
} from '@shared/models/payment.model';

/**
 * Service for payment-related HTTP operations.
 *
 * All endpoints target the payment-service via the API gateway.
 * Authentication is handled by the JWT interceptor.
 */
@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiBaseUrl}/payments`;

  /**
   * Initiate a HelloAsso checkout session.
   * Returns a CheckoutResponse containing the redirect URL.
   */
  initiateCheckout(request: CheckoutRequest): Observable<CheckoutResponse> {
    return this.http.post<CheckoutResponse>(`${this.baseUrl}/checkout`, request);
  }

  /**
   * Load a single payment by its ID.
   */
  getById(paymentId: number): Observable<PaymentDetail> {
    return this.http.get<PaymentDetail>(`${this.baseUrl}/${paymentId}`);
  }

  /**
   * Load paginated payments for a family.
   * Builds query params dynamically, skipping null/undefined values.
   */
  getMyPayments(params: PaymentListParams): Observable<Page<PaymentSummary>> {
    let httpParams = new HttpParams()
      .set('familyId', params.familyId.toString());

    if (params.status != null) {
      httpParams = httpParams.set('status', params.status);
    }
    if (params.from != null) {
      httpParams = httpParams.set('from', params.from);
    }
    if (params.to != null) {
      httpParams = httpParams.set('to', params.to);
    }
    if (params.page != null) {
      httpParams = httpParams.set('page', params.page.toString());
    }
    if (params.size != null) {
      httpParams = httpParams.set('size', params.size.toString());
    }
    if (params.sort != null) {
      httpParams = httpParams.set('sort', params.sort);
    }

    return this.http.get<Page<PaymentSummary>>(this.baseUrl, { params: httpParams });
  }
}
