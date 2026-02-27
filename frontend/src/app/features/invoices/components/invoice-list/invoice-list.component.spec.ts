// frontend/src/app/features/invoices/components/invoice-list/invoice-list.component.spec.ts
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, throwError } from 'rxjs';

import { InvoiceListComponent } from './invoice-list.component';
import { InvoiceService } from '../../services/invoice.service';
import {
  InvoiceSummary,
  InvoicePage,
  InvoiceStatus,
} from '@shared/models/invoice.model';

describe('InvoiceListComponent', () => {
  let component: InvoiceListComponent;
  let fixture: ComponentFixture<InvoiceListComponent>;
  let invoiceServiceMock: jest.Mocked<InvoiceService>;

  const mockInvoice: InvoiceSummary = {
    id: 1,
    invoiceNumber: 'INV-2026-001',
    status: InvoiceStatus.PAID,
    buyerName: 'Jean Dupont',
    amount: 150.0,
    totalAmount: 150.0,
    issuedAt: '2026-02-27T10:00:00',
  };

  const mockPage: InvoicePage = {
    content: [mockInvoice],
    totalElements: 1,
    totalPages: 1,
    number: 0,
    size: 10,
  };

  const emptyPage: InvoicePage = {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 10,
  };

  beforeEach(async () => {
    invoiceServiceMock = {
      getInvoice: jest.fn(),
      getInvoicesByUser: jest.fn().mockReturnValue(of(mockPage)),
      getInvoicesByPayment: jest.fn(),
      downloadPdf: jest.fn(),
    } as unknown as jest.Mocked<InvoiceService>;

    await TestBed.configureTestingModule({
      imports: [InvoiceListComponent, NoopAnimationsModule],
      providers: [
        { provide: InvoiceService, useValue: invoiceServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InvoiceListComponent);
    component = fixture.componentInstance;
  });

  it('should_createComponent', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should_loadInvoices_when_init', () => {
    fixture.detectChanges();

    expect(invoiceServiceMock.getInvoicesByUser).toHaveBeenCalledWith(1, 0, 10);
    expect(component.invoices()).toEqual([mockInvoice]);
    expect(component.totalElements()).toBe(1);
    expect(component.loading()).toBe(false);
  });

  it('should_renderTable_when_invoicesLoaded', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('table')).toBeTruthy();
    expect(compiled.textContent).toContain('INV-2026-001');
    expect(compiled.textContent).toContain('Jean Dupont');
    expect(compiled.textContent).toContain('Payee');
  });

  it('should_formatAmount_when_displayingAmount', () => {
    const formatted = component.formatAmount(150.5);
    // French locale EUR format includes the amount and EUR symbol
    expect(formatted).toContain('150');
    expect(formatted).toContain('50');
  });

  it('should_showEmptyState_when_noInvoices', () => {
    invoiceServiceMock.getInvoicesByUser.mockReturnValue(of(emptyPage));

    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Aucune facture trouvee');
  });

  it('should_triggerDownload_when_downloadClicked', () => {
    const mockBlob = new Blob(['%PDF-1.4'], { type: 'application/pdf' });
    invoiceServiceMock.downloadPdf.mockReturnValue(of(mockBlob));

    // Mock URL and anchor APIs
    const createObjectURLMock = jest.fn().mockReturnValue('blob:mock-url');
    const revokeObjectURLMock = jest.fn();
    (globalThis as any).URL.createObjectURL = createObjectURLMock;
    (globalThis as any).URL.revokeObjectURL = revokeObjectURLMock;

    const clickMock = jest.fn();
    const originalCreateElement = document.createElement.bind(document);
    jest.spyOn(document, 'createElement').mockImplementation((tagName: string) => {
      if (tagName === 'a') {
        return { href: '', download: '', click: clickMock } as unknown as HTMLAnchorElement;
      }
      return originalCreateElement(tagName);
    });

    fixture.detectChanges();

    component.downloadInvoice(mockInvoice);

    expect(invoiceServiceMock.downloadPdf).toHaveBeenCalledWith(1);
    expect(createObjectURLMock).toHaveBeenCalledWith(mockBlob);
    expect(clickMock).toHaveBeenCalled();
    expect(revokeObjectURLMock).toHaveBeenCalledWith('blob:mock-url');
  });
});
