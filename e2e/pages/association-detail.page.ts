import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the association detail page (/associations/:id).
 *
 * Used by: association-search.spec.ts, subscription.spec.ts
 */
export class AssociationDetailPage {
  readonly page: Page;
  readonly associationName: Locator;
  readonly description: Locator;
  readonly cityLabel: Locator;
  readonly categoryBadge: Locator;
  readonly activityList: Locator;
  readonly activityCards: Locator;
  readonly subscribeButton: Locator;
  readonly backToSearchLink: Locator;
  readonly contactInfo: Locator;
  readonly sessionSchedule: Locator;

  constructor(page: Page) {
    this.page = page;
    this.associationName = page.getByTestId('association-name');
    this.description = page.getByTestId('association-description');
    this.cityLabel = page.getByTestId('association-city');
    this.categoryBadge = page.getByTestId('association-category');
    this.activityList = page.getByTestId('activity-list');
    this.activityCards = page.getByTestId('activity-card');
    this.subscribeButton = page.getByTestId('subscribe-button');
    this.backToSearchLink = page.getByTestId('back-to-search');
    this.contactInfo = page.getByTestId('association-contact');
    this.sessionSchedule = page.getByTestId('session-schedule');
  }

  async goto(id: string): Promise<void> {
    await this.page.goto(`/associations/${id}`);
    await this.associationName.waitFor({ state: 'visible' });
  }

  /**
   * Click the subscribe button for a specific activity.
   */
  async subscribeToActivity(activityIndex: number): Promise<void> {
    const card = this.activityCards.nth(activityIndex);
    await card.getByTestId('activity-subscribe-button').click();
  }

  /**
   * Get the association name text.
   */
  async getName(): Promise<string | null> {
    return this.associationName.textContent();
  }

  /**
   * Get the city text.
   */
  async getCity(): Promise<string | null> {
    return this.cityLabel.textContent();
  }

  /**
   * Get the category badge text.
   */
  async getCategory(): Promise<string | null> {
    return this.categoryBadge.textContent();
  }

  /**
   * Get the number of activities listed.
   */
  async getActivityCount(): Promise<number> {
    return this.activityCards.count();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.associationName).toBeVisible();
    await expect(this.description).toBeVisible();
  }
}
