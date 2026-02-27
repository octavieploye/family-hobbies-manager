// frontend/src/app/features/payments/services/payment.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { PaymentService } from './payment.service';
import {
  CheckoutRequest,
  CheckoutResponse,
  Page,
  PaymentDetail,
  PaymentListParams,
  PaymentStatus,
  PaymentSummary,
  PaymentType,
} from '@shared/models/payment.model';
import { environment } from '@environments/environment';

describe('PaymentService', () => {
  let service: PaymentService;
  let httpMock: HttpTestingController;
  const API_BASE = `${environment.apiBaseUrl}/payments`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(PaymentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should call POST /checkout when initiating checkout', () => {
    const request: CheckoutRequest = {
      subscriptionId: 1,
      amount: 150,
      description: 'Adhesion Club Sportif',
      paymentType: PaymentType.ADHESION,
      returnUrl: 'http://localhost:4200/payments/checkout/redirect?status=success',
      cancelUrl: 'http://localhost:4200/payments/checkout/redirect?status=cancelled',
    };

    const mockResponse: CheckoutResponse = {
      paymentId: 42,
      subscriptionId: 1,
      amount: 150,
      paymentType: 'ADHESION',
      status: PaymentStatus.PENDING,
      checkoutUrl: 'https://checkout.helloasso-sandbox.com/abc123',
      helloassoCheckoutId: 'abc123',
      expiresAt: '2026-03-01T10:00:00',
      createdAt: '2026-02-27T10:00:00',
    };

    service.initiateCheckout(request).subscribe((result) => {
      expect(result).toEqual(mockResponse);
      expect(result.checkoutUrl).toContain('helloasso');
    });

    const req = httpMock.expectOne(`${API_BASE}/checkout`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(mockResponse);
  });

  it('should call GET /payments/{id} when loading a payment', () => {
    const mockDetail: PaymentDetail = {
      id: 42,
      subscriptionId: 1,
      familyMemberName: 'Jean Dupont',
      associationName: 'Club Sportif Paris',
      activityName: 'Football',
      amount: 150,
      paymentType: 'ADHESION',
      status: PaymentStatus.COMPLETED,
      paymentMethod: null,
      paidAt: '2026-02-27T12:00:00',
      invoiceId: null,
      createdAt: '2026-02-27T10:00:00',
      familyId: 1,
      helloassoPaymentId: 'ha_pay_123',
      currency: 'EUR',
      updatedAt: '2026-02-27T12:00:00',
    };

    service.getById(42).subscribe((result) => {
      expect(result).toEqual(mockDetail);
      expect(result.id).toBe(42);
    });

    const req = httpMock.expectOne(`${API_BASE}/42`);
    expect(req.request.method).toBe('GET');
    req.flush(mockDetail);
  });

  it('should call GET /payments with familyId when loading payments', () => {
    const params: PaymentListParams = {
      familyId: 1,
      page: 0,
      size: 10,
    };

    const mockPage: Page<PaymentSummary> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 10,
      first: true,
      last: true,
    };

    service.getMyPayments(params).subscribe((result) => {
      expect(result).toEqual(mockPage);
    });

    const req = httpMock.expectOne(
      (r) =>
        r.url === API_BASE &&
        r.params.get('familyId') === '1' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '10'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should include status filter when provided', () => {
    const params: PaymentListParams = {
      familyId: 1,
      status: PaymentStatus.COMPLETED,
      page: 0,
      size: 10,
    };

    const mockPage: Page<PaymentSummary> = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      number: 0,
      size: 10,
      first: true,
      last: true,
    };

    service.getMyPayments(params).subscribe((result) => {
      expect(result.content.length).toBe(0);
    });

    const req = httpMock.expectOne(
      (r) =>
        r.url === API_BASE &&
        r.params.get('familyId') === '1' &&
        r.params.get('status') === 'COMPLETED' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '10'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });
});
