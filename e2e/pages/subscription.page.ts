import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the subscription page (/subscriptions).
 *
 * Used by: subscription.spec.ts
 */
export class SubscriptionPage {
  readonly page: Page;
  readonly pageTitle: Locator;
  readonly subscriptionList: Locator;
  readonly subscriptionRows: Locator;
  readonly emptyState: Locator;
  readonly filterByMember: Locator;
  readonly filterByStatus: Locator;
  readonly cancelDialog: Locator;
  readonly confirmCancelButton: Locator;
  readonly cancelCancelButton: Locator;
  readonly successMessage: Locator;

  /* Subscription Form (inline or dialog) */
  readonly memberSelect: Locator;
  readonly activitySelect: Locator;
  readonly submitSubscriptionButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: /abonnements/i });
    this.subscriptionList = page.getByTestId('subscription-list');
    this.subscriptionRows = page.getByTestId('subscription-row');
    this.emptyState = page.getByTestId('subscription-empty');
    this.filterByMember = page.getByTestId('filter-member');
    this.filterByStatus = page.getByTestId('filter-status');
    this.cancelDialog = page.getByTestId('cancel-subscription-dialog');
    this.confirmCancelButton = page.getByTestId('confirm-cancel-subscription');
    this.cancelCancelButton = page.getByTestId('dismiss-cancel-dialog');
    this.successMessage = page.getByTestId('subscription-success');

    /* Subscription Form */
    this.memberSelect = page.getByTestId('subscription-member-select');
    this.activitySelect = page.getByTestId('subscription-activity-select');
    this.submitSubscriptionButton = page.getByTestId('submit-subscription');
  }

  async goto(): Promise<void> {
    await this.page.goto('/subscriptions');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Subscribe a family member to an activity.
   */
  async subscribeMember(memberName: string, activityName: string): Promise<void> {
    await this.memberSelect.click();
    await this.page.getByRole('option', { name: memberName }).click();
    await this.activitySelect.click();
    await this.page.getByRole('option', { name: activityName }).click();
    await this.submitSubscriptionButton.click();
    await this.successMessage.waitFor({ state: 'visible' });
  }

  /**
   * Cancel a subscription by row index.
   */
  async cancelSubscription(index: number): Promise<void> {
    const row = this.subscriptionRows.nth(index);
    await row.getByTestId('cancel-subscription-button').click();
    await this.cancelDialog.waitFor({ state: 'visible' });
    await this.confirmCancelButton.click();
    await this.cancelDialog.waitFor({ state: 'hidden' });
  }

  /**
   * Get the number of subscription rows.
   */
  async getSubscriptionCount(): Promise<number> {
    return this.subscriptionRows.count();
  }

  /**
   * Get the displayed association name for a given subscription row.
   */
  async getAssociationName(index: number): Promise<string | null> {
    const row = this.subscriptionRows.nth(index);
    return row.getByTestId('subscription-association').textContent();
  }

  /**
   * Get the displayed status for a given subscription row.
   */
  async getStatus(index: number): Promise<string | null> {
    const row = this.subscriptionRows.nth(index);
    return row.getByTestId('subscription-status').textContent();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
  }

  async expectHasSubscriptions(): Promise<void> {
    const count = await this.subscriptionRows.count();
    expect(count).toBeGreaterThan(0);
  }
}
