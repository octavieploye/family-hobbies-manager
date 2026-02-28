import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for payment pages (/payments, /payments/:id).
 *
 * Used by: payment.spec.ts
 */
export class PaymentPage {
  readonly page: Page;
  readonly pageTitle: Locator;
  readonly paymentList: Locator;
  readonly paymentRows: Locator;
  readonly emptyState: Locator;
  readonly filterByStatus: Locator;
  readonly filterByDate: Locator;

  /* Payment Detail */
  readonly paymentAmount: Locator;
  readonly paymentStatus: Locator;
  readonly paymentDate: Locator;
  readonly paymentMethod: Locator;
  readonly paymentAssociation: Locator;
  readonly paymentTimeline: Locator;
  readonly timelineSteps: Locator;
  readonly downloadInvoiceButton: Locator;
  readonly backToListLink: Locator;

  /* Checkout */
  readonly checkoutButton: Locator;
  readonly checkoutLoadingIndicator: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: /paiements/i });
    this.paymentList = page.getByTestId('payment-list');
    this.paymentRows = page.getByTestId('payment-row');
    this.emptyState = page.getByTestId('payment-empty');
    this.filterByStatus = page.getByTestId('payment-filter-status');
    this.filterByDate = page.getByTestId('payment-filter-date');

    /* Payment Detail */
    this.paymentAmount = page.getByTestId('payment-amount');
    this.paymentStatus = page.getByTestId('payment-status');
    this.paymentDate = page.getByTestId('payment-date');
    this.paymentMethod = page.getByTestId('payment-method');
    this.paymentAssociation = page.getByTestId('payment-association');
    this.paymentTimeline = page.getByTestId('payment-timeline');
    this.timelineSteps = page.getByTestId('timeline-step');
    this.downloadInvoiceButton = page.getByTestId('download-invoice-button');
    this.backToListLink = page.getByTestId('back-to-payment-list');

    /* Checkout */
    this.checkoutButton = page.getByTestId('checkout-button');
    this.checkoutLoadingIndicator = page.getByTestId('checkout-loading');
  }

  async goto(): Promise<void> {
    await this.page.goto('/payments');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  async gotoDetail(paymentId: string): Promise<void> {
    await this.page.goto(`/payments/${paymentId}`);
    await this.paymentAmount.waitFor({ state: 'visible' });
  }

  /**
   * Click on a payment row to navigate to the detail view.
   */
  async viewPaymentDetail(index: number): Promise<void> {
    await this.paymentRows.nth(index).click();
    await this.page.waitForURL('**/payments/*');
  }

  /**
   * Initiate a checkout flow.
   */
  async initiateCheckout(): Promise<void> {
    await this.checkoutButton.click();
  }

  /**
   * Get the number of payment rows.
   */
  async getPaymentCount(): Promise<number> {
    return this.paymentRows.count();
  }

  /**
   * Get the displayed status for a specific payment row.
   */
  async getPaymentStatus(index: number): Promise<string | null> {
    const row = this.paymentRows.nth(index);
    return row.getByTestId('payment-row-status').textContent();
  }

  /**
   * Get the displayed amount for a specific payment row.
   */
  async getPaymentAmount(index: number): Promise<string | null> {
    const row = this.paymentRows.nth(index);
    return row.getByTestId('payment-row-amount').textContent();
  }

  /**
   * Get the number of completed timeline steps in the detail view.
   */
  async getTimelineStepCount(): Promise<number> {
    return this.timelineSteps.count();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
  }

  async expectPaymentDetailLoaded(): Promise<void> {
    await expect(this.paymentAmount).toBeVisible();
    await expect(this.paymentStatus).toBeVisible();
  }
}
