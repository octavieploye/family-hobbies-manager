// frontend/src/app/shared/models/payment.model.ts

export enum PaymentStatus {
  PENDING = 'PENDING',
  AUTHORIZED = 'AUTHORIZED',
  COMPLETED = 'COMPLETED',
  FAILED = 'FAILED',
  REFUNDED = 'REFUNDED',
  CANCELLED = 'CANCELLED'
}

export enum PaymentMethod {
  CARD = 'CARD',
  SEPA = 'SEPA',
  INSTALLMENT_3X = 'INSTALLMENT_3X',
  INSTALLMENT_10X = 'INSTALLMENT_10X'
}

export enum PaymentType {
  ADHESION = 'ADHESION',
  COTISATION = 'COTISATION',
  DONATION = 'DONATION',
  EVENT = 'EVENT'
}

export interface CheckoutRequest {
  subscriptionId: number;
  amount: number;
  description: string;
  paymentType: PaymentType;
  returnUrl: string;
  cancelUrl: string;
}

export interface CheckoutResponse {
  paymentId: number;
  subscriptionId: number;
  amount: number;
  paymentType: string;
  status: PaymentStatus;
  checkoutUrl: string;
  helloassoCheckoutId: string;
  expiresAt: string;
  createdAt: string;
}

export interface PaymentSummary {
  id: number;
  subscriptionId: number;
  familyMemberName: string;
  associationName: string;
  activityName: string;
  amount: number;
  paymentType: string;
  status: PaymentStatus;
  paymentMethod: PaymentMethod | null;
  paidAt: string | null;
  invoiceId: number | null;
  createdAt: string;
}

export interface PaymentDetail extends PaymentSummary {
  familyId: number;
  helloassoPaymentId: string | null;
  currency: string;
  updatedAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface PaymentListParams {
  familyId: number;
  status?: PaymentStatus;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export const PAYMENT_STATUS_CONFIG: Record<PaymentStatus, { color: string; label: string }> = {
  [PaymentStatus.PENDING]: { color: 'warn', label: 'En attente' },
  [PaymentStatus.AUTHORIZED]: { color: 'primary', label: 'Autoris\u00e9' },
  [PaymentStatus.COMPLETED]: { color: 'accent', label: 'Compl\u00e9t\u00e9' },
  [PaymentStatus.FAILED]: { color: 'warn', label: '\u00c9chou\u00e9' },
  [PaymentStatus.REFUNDED]: { color: 'basic', label: 'Rembours\u00e9' },
  [PaymentStatus.CANCELLED]: { color: 'basic', label: 'Annul\u00e9' }
};
