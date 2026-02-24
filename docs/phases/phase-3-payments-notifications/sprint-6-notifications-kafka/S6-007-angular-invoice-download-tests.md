# Story S6-007: Angular Invoice Download -- TDD Tests

> Companion file for [S6-007: Angular Invoice Download](./S6-007-angular-invoice-download.md)
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Test File 1: InvoiceService Tests

**File**: `frontend/src/app/features/invoices/services/invoice.service.spec.ts`

```typescript
// frontend/src/app/features/invoices/services/invoice.service.spec.ts

import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController
} from '@angular/common/http/testing';
import { InvoiceService } from './invoice.service';
import {
  InvoiceResponse,
  InvoiceSummaryResponse,
  InvoiceStatus,
  PageResponse
} from '../../../shared/models/invoice.model';
import { environment } from '../../../../environments/environment';

describe('InvoiceService', () => {
  let service: InvoiceService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/api/v1/invoices`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [InvoiceService]
    });
    service = TestBed.inject(InvoiceService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getInvoice', () => {
    it('should call GET /{id}', () => {
      const mockInvoice: InvoiceResponse = {
        id: 1,
        invoiceNumber: 'FHM-2026-000001',
        paymentId: 10,
        subscriptionId: 5,
        familyId: 1,
        familyName: 'Famille Dupont',
        associationName: 'Judo Club Lyon',
        activityName: 'Judo enfant',
        familyMemberName: 'Lucas Dupont',
        season: '2025-2026',
        lineItems: [
          {
            description: 'Cotisation Judo enfant - Saison 2025-2026',
            quantity: 1,
            unitPrice: 150.0,
            total: 150.0
          }
        ],
        subtotal: 150.0,
        tax: 0,
        total: 150.0,
        currency: 'EUR',
        status: InvoiceStatus.PAID,
        issuedAt: '2026-02-20T10:00:00Z',
        paidAt: '2026-02-20T10:05:00Z',
        payerEmail: 'dupont@example.com',
        payerName: 'Pierre Dupont',
        downloadUrl: '/api/v1/invoices/1/download',
        createdAt: '2026-02-20T10:00:00Z'
      };

      service.getInvoice(1).subscribe((result) => {
        expect(result).toEqual(mockInvoice);
      });

      const req = httpMock.expectOne(`${baseUrl}/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockInvoice);
    });
  });

  describe('getMyInvoices', () => {
    it('should call GET /family/{familyId} with params', () => {
      const mockPage: PageResponse<InvoiceSummaryResponse> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 20,
        number: 0,
        first: true,
        last: true
      };

      service
        .getMyInvoices(1, {
          status: InvoiceStatus.PAID,
          page: 0,
          size: 20,
          sort: 'issuedAt,desc'
        })
        .subscribe((result) => {
          expect(result).toEqual(mockPage);
        });

      const req = httpMock.expectOne(
        (r) =>
          r.url === `${baseUrl}/family/1` &&
          r.params.get('status') === 'PAID' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '20' &&
          r.params.get('sort') === 'issuedAt,desc'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockPage);
    });

    it('should call GET /family/{familyId} without optional params', () => {
      const mockPage: PageResponse<InvoiceSummaryResponse> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        size: 20,
        number: 0,
        first: true,
        last: true
      };

      service.getMyInvoices(1).subscribe((result) => {
        expect(result).toEqual(mockPage);
      });

      const req = httpMock.expectOne(`${baseUrl}/family/1`);
      expect(req.request.method).toBe('GET');
      req.flush(mockPage);
    });
  });

  describe('downloadInvoice', () => {
    it('should call GET /{id}/download with blob responseType', () => {
      // Mock URL.createObjectURL and revokeObjectURL for blob download
      const createObjectURLMock = jest.fn().mockReturnValue('blob:test-url');
      const revokeObjectURLMock = jest.fn();
      Object.defineProperty(window, 'URL', {
        value: {
          createObjectURL: createObjectURLMock,
          revokeObjectURL: revokeObjectURLMock
        },
        writable: true
      });

      // Mock anchor element click
      const clickMock = jest.fn();
      const appendChildMock = jest.spyOn(document.body, 'appendChild').mockImplementation(
        (node) => node
      );
      const removeChildMock = jest.spyOn(document.body, 'removeChild').mockImplementation(
        (node) => node
      );
      jest.spyOn(document, 'createElement').mockReturnValue({
        click: clickMock,
        href: '',
        download: '',
        style: { display: '' }
      } as any);

      const mockBlob = new Blob(['pdf-content'], {
        type: 'application/pdf'
      });

      service.downloadInvoice(1).subscribe((result) => {
        expect(result).toBeInstanceOf(Blob);
        expect(createObjectURLMock).toHaveBeenCalled();
        expect(clickMock).toHaveBeenCalled();
      });

      const req = httpMock.expectOne(`${baseUrl}/1/download`);
      expect(req.request.method).toBe('GET');
      expect(req.request.responseType).toBe('blob');
      req.flush(mockBlob);
    });
  });
});
```

---

## Test File 2: InvoiceListComponent Tests

**File**: `frontend/src/app/features/invoices/pages/invoice-list/invoice-list.component.spec.ts`

```typescript
// frontend/src/app/features/invoices/pages/invoice-list/invoice-list.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { InvoiceListComponent } from './invoice-list.component';
import { InvoiceService } from '../../services/invoice.service';
import {
  InvoiceSummaryResponse,
  InvoiceStatus,
  PageResponse
} from '../../../../shared/models/invoice.model';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';

describe('InvoiceListComponent', () => {
  let component: InvoiceListComponent;
  let fixture: ComponentFixture<InvoiceListComponent>;
  let invoiceServiceMock: jest.Mocked<Partial<InvoiceService>>;

  const mockInvoices: InvoiceSummaryResponse[] = [
    {
      id: 1,
      invoiceNumber: 'FHM-2026-000001',
      associationName: 'Judo Club Lyon',
      activityName: 'Judo enfant',
      familyMemberName: 'Lucas Dupont',
      season: '2025-2026',
      total: 150.0,
      currency: 'EUR',
      status: InvoiceStatus.PAID,
      issuedAt: '2026-02-20T10:00:00Z',
      paidAt: '2026-02-20T10:05:00Z',
      downloadUrl: '/api/v1/invoices/1/download'
    },
    {
      id: 2,
      invoiceNumber: 'FHM-2026-000002',
      associationName: 'Ecole de Danse Paris',
      activityName: 'Danse classique',
      familyMemberName: 'Emma Dupont',
      season: '2025-2026',
      total: 200.0,
      currency: 'EUR',
      status: InvoiceStatus.ISSUED,
      issuedAt: '2026-02-22T14:00:00Z',
      paidAt: null,
      downloadUrl: '/api/v1/invoices/2/download'
    }
  ];

  const mockPage: PageResponse<InvoiceSummaryResponse> = {
    content: mockInvoices,
    totalElements: 2,
    totalPages: 1,
    size: 20,
    number: 0,
    first: true,
    last: true
  };

  beforeEach(async () => {
    invoiceServiceMock = {
      getMyInvoices: jest.fn().mockReturnValue(of(mockPage)),
      downloadInvoice: jest.fn().mockReturnValue(of(new Blob()))
    };

    await TestBed.configureTestingModule({
      imports: [InvoiceListComponent, NoopAnimationsModule],
      providers: [
        { provide: InvoiceService, useValue: invoiceServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(InvoiceListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should load invoices on init', () => {
    expect(invoiceServiceMock.getMyInvoices).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        page: 0,
        size: 20,
        sort: 'issuedAt,desc'
      })
    );
  });

  it('should render table rows for each invoice', () => {
    const rows = fixture.debugElement.queryAll(By.css('tr[mat-row]'));
    expect(rows.length).toBe(2);
  });

  it('should show page title as "Factures"', () => {
    const title = fixture.debugElement.query(
      By.css('.invoice-list__title')
    );
    expect(title.nativeElement.textContent).toContain('Factures');
  });

  it('should call downloadInvoice when download button is clicked', () => {
    const downloadBtn = fixture.debugElement.queryAll(
      By.css('button[matTooltip="Telecharger"]')
    )[0];
    downloadBtn.nativeElement.click();
    expect(invoiceServiceMock.downloadInvoice).toHaveBeenCalledWith(1);
  });
});
```

---

## Test File 3: InvoiceSectionComponent Tests

**File**: `frontend/src/app/features/invoices/components/invoice-section/invoice-section.component.spec.ts`

```typescript
// frontend/src/app/features/invoices/components/invoice-section/invoice-section.component.spec.ts

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { InvoiceSectionComponent } from './invoice-section.component';
import { InvoiceService } from '../../services/invoice.service';
import {
  InvoiceResponse,
  InvoiceStatus
} from '../../../../shared/models/invoice.model';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';

describe('InvoiceSectionComponent', () => {
  let component: InvoiceSectionComponent;
  let fixture: ComponentFixture<InvoiceSectionComponent>;
  let invoiceServiceMock: jest.Mocked<Partial<InvoiceService>>;

  const mockInvoice: InvoiceResponse = {
    id: 1,
    invoiceNumber: 'FHM-2026-000001',
    paymentId: 10,
    subscriptionId: 5,
    familyId: 1,
    familyName: 'Famille Dupont',
    associationName: 'Judo Club Lyon',
    activityName: 'Judo enfant',
    familyMemberName: 'Lucas Dupont',
    season: '2025-2026',
    lineItems: [
      {
        description: 'Cotisation Judo enfant - Saison 2025-2026',
        quantity: 1,
        unitPrice: 150.0,
        total: 150.0
      }
    ],
    subtotal: 150.0,
    tax: 0,
    total: 150.0,
    currency: 'EUR',
    status: InvoiceStatus.PAID,
    issuedAt: '2026-02-20T10:00:00Z',
    paidAt: '2026-02-20T10:05:00Z',
    payerEmail: 'dupont@example.com',
    payerName: 'Pierre Dupont',
    downloadUrl: '/api/v1/invoices/1/download',
    createdAt: '2026-02-20T10:00:00Z'
  };

  beforeEach(async () => {
    invoiceServiceMock = {
      downloadInvoice: jest.fn().mockReturnValue(of(new Blob()))
    };

    await TestBed.configureTestingModule({
      imports: [InvoiceSectionComponent, NoopAnimationsModule],
      providers: [
        { provide: InvoiceService, useValue: invoiceServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(InvoiceSectionComponent);
    component = fixture.componentInstance;
    component.invoice = mockInvoice;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('should display the invoice number', () => {
    const element = fixture.debugElement.query(
      By.css('.invoice-section__value')
    );
    expect(element.nativeElement.textContent).toContain('FHM-2026-000001');
  });

  it('should display the status label in French', () => {
    expect(component.statusLabel).toBe('Payee');
  });

  it('should call downloadInvoice on download button click', () => {
    const downloadBtn = fixture.debugElement.query(
      By.css('button[mat-raised-button]')
    );
    downloadBtn.nativeElement.click();
    expect(invoiceServiceMock.downloadInvoice).toHaveBeenCalledWith(1);
  });

  it('should show payment date when invoice is paid', () => {
    const rows = fixture.debugElement.queryAll(
      By.css('.invoice-section__row')
    );
    const labels = rows.map((r) =>
      r.query(By.css('.invoice-section__label'))?.nativeElement.textContent.trim()
    );
    expect(labels).toContain('Date de paiement');
  });
});
```

---

## Test Summary

| Test File | Test Count | What It Verifies |
|-----------|-----------|------------------|
| `invoice.service.spec.ts` | 4 | GET invoice, GET family invoices (with/without params), blob download |
| `invoice-list.component.spec.ts` | 5 | Init load, table rows, title, download button |
| `invoice-section.component.spec.ts` | 5 | Invoice number display, status label, download click, payment date |
| **Total** | **14** | |

### Run All Invoice Tests

```bash
cd frontend
npx jest --testPathPattern="features/invoices" --verbose
```

Expected: all 14 tests pass (will initially fail -- TDD red phase).
