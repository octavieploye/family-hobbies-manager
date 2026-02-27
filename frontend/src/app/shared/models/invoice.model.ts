// frontend/src/app/shared/models/invoice.model.ts

export enum InvoiceStatus {
  DRAFT = 'DRAFT',
  ISSUED = 'ISSUED',
  PAID = 'PAID',
  CANCELLED = 'CANCELLED',
}

export interface InvoiceResponse {
  id: number;
  paymentId: number;
  invoiceNumber: string;
  status: InvoiceStatus;
  buyerName: string;
  buyerEmail: string;
  description: string;
  amount: number;
  taxRate: number;
  taxAmount: number;
  totalAmount: number;
  currency: string;
  issuedAt: string;
  dueDate: string | null;
  createdAt: string;
}

export interface InvoiceSummary {
  id: number;
  invoiceNumber: string;
  status: InvoiceStatus;
  buyerName: string;
  amount: number;
  totalAmount: number;
  issuedAt: string;
}

export interface InvoicePage {
  content: InvoiceSummary[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const INVOICE_STATUS_CONFIG: Record<InvoiceStatus, { label: string; color: string }> = {
  [InvoiceStatus.DRAFT]: { label: 'Brouillon', color: 'basic' },
  [InvoiceStatus.ISSUED]: { label: 'Emise', color: 'primary' },
  [InvoiceStatus.PAID]: { label: 'Payee', color: 'accent' },
  [InvoiceStatus.CANCELLED]: { label: 'Annulee', color: 'warn' },
};
