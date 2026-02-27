// frontend/src/app/features/invoices/services/invoice.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { InvoiceService } from './invoice.service';
import {
  InvoiceResponse,
  InvoiceSummary,
  InvoicePage,
  InvoiceStatus,
} from '@shared/models/invoice.model';
import { environment } from '@environments/environment';

describe('InvoiceService', () => {
  let service: InvoiceService;
  let httpMock: HttpTestingController;
  const API_BASE = `${environment.apiBaseUrl}/invoices`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
    });
    service = TestBed.inject(InvoiceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should_getInvoice_when_validId', () => {
    const mockInvoice: InvoiceResponse = {
      id: 1,
      paymentId: 42,
      invoiceNumber: 'INV-2026-001',
      status: InvoiceStatus.PAID,
      buyerName: 'Jean Dupont',
      buyerEmail: 'jean@example.com',
      description: 'Adhesion Club Sportif',
      amount: 150.0,
      taxRate: 0.0,
      taxAmount: 0.0,
      totalAmount: 150.0,
      currency: 'EUR',
      issuedAt: '2026-02-27T10:00:00',
      dueDate: null,
      createdAt: '2026-02-27T10:00:00',
    };

    service.getInvoice(1).subscribe((result) => {
      expect(result).toEqual(mockInvoice);
      expect(result.id).toBe(1);
      expect(result.invoiceNumber).toBe('INV-2026-001');
    });

    const req = httpMock.expectOne(`${API_BASE}/1`);
    expect(req.request.method).toBe('GET');
    req.flush(mockInvoice);
  });

  it('should_getInvoicesByUser_when_calledWithPagination', () => {
    const mockPage: InvoicePage = {
      content: [
        {
          id: 1,
          invoiceNumber: 'INV-2026-001',
          status: InvoiceStatus.PAID,
          buyerName: 'Jean Dupont',
          amount: 150.0,
          totalAmount: 150.0,
          issuedAt: '2026-02-27T10:00:00',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
    };

    service.getInvoicesByUser(1, 0, 10).subscribe((result) => {
      expect(result).toEqual(mockPage);
      expect(result.content.length).toBe(1);
      expect(result.totalElements).toBe(1);
    });

    const req = httpMock.expectOne(
      (r) =>
        r.url === API_BASE &&
        r.params.get('userId') === '1' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '10'
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockPage);
  });

  it('should_getInvoicesByPayment_when_calledWithPaymentId', () => {
    const mockInvoices: InvoiceSummary[] = [
      {
        id: 1,
        invoiceNumber: 'INV-2026-001',
        status: InvoiceStatus.PAID,
        buyerName: 'Jean Dupont',
        amount: 150.0,
        totalAmount: 150.0,
        issuedAt: '2026-02-27T10:00:00',
      },
    ];

    service.getInvoicesByPayment(42).subscribe((result) => {
      expect(result).toEqual(mockInvoices);
      expect(result.length).toBe(1);
    });

    const req = httpMock.expectOne(`${API_BASE}/by-payment/42`);
    expect(req.request.method).toBe('GET');
    req.flush(mockInvoices);
  });

  it('should_downloadPdf_when_calledWithInvoiceId', () => {
    const mockBlob = new Blob(['%PDF-1.4 mock content'], { type: 'application/pdf' });

    service.downloadPdf(1).subscribe((result) => {
      expect(result).toBeInstanceOf(Blob);
      expect(result.size).toBeGreaterThan(0);
    });

    const req = httpMock.expectOne(`${API_BASE}/1/download`);
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(mockBlob);
  });
});
