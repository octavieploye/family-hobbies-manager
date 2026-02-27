// frontend/src/app/features/invoices/components/invoice-section/invoice-section.component.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { InvoiceSectionComponent } from './invoice-section.component';
import { InvoiceService } from '../../services/invoice.service';
import {
  InvoiceSummary,
  InvoiceStatus,
} from '@shared/models/invoice.model';

describe('InvoiceSectionComponent', () => {
  let component: InvoiceSectionComponent;
  let fixture: ComponentFixture<InvoiceSectionComponent>;
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

  beforeEach(async () => {
    invoiceServiceMock = {
      getInvoice: jest.fn(),
      getInvoicesByUser: jest.fn(),
      getInvoicesByPayment: jest.fn().mockReturnValue(of([mockInvoice])),
      downloadPdf: jest.fn(),
    } as unknown as jest.Mocked<InvoiceService>;

    await TestBed.configureTestingModule({
      imports: [InvoiceSectionComponent, NoopAnimationsModule],
      providers: [
        { provide: InvoiceService, useValue: invoiceServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InvoiceSectionComponent);
    component = fixture.componentInstance;
    component.paymentId = 42;
  });

  it('should_createComponent', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should_loadInvoices_when_paymentIdProvided', () => {
    fixture.detectChanges();

    expect(invoiceServiceMock.getInvoicesByPayment).toHaveBeenCalledWith(42);
    expect(component.invoices()).toEqual([mockInvoice]);
    expect(component.loading()).toBe(false);
  });

  it('should_showInvoices_when_loaded', () => {
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('INV-2026-001');
    expect(compiled.textContent).toContain('Jean Dupont');
    expect(compiled.textContent).toContain('Payee');
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
