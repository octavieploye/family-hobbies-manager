import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the dashboard page (/dashboard).
 *
 * Used by: auth.spec.ts, family.spec.ts, subscription.spec.ts
 */
export class DashboardPage {
  readonly page: Page;
  readonly welcomeMessage: Locator;
  readonly familyCard: Locator;
  readonly recentActivityList: Locator;
  readonly quickActionSubscribe: Locator;
  readonly quickActionSearch: Locator;
  readonly quickActionAttendance: Locator;
  readonly memberCount: Locator;
  readonly subscriptionCount: Locator;
  readonly upcomingEventsCount: Locator;
  readonly notificationBell: Locator;
  readonly userMenu: Locator;
  readonly logoutButton: Locator;
  readonly navLinks: Locator;

  constructor(page: Page) {
    this.page = page;
    this.welcomeMessage = page.getByTestId('dashboard-welcome');
    this.familyCard = page.getByTestId('dashboard-family-card');
    this.recentActivityList = page.getByTestId('dashboard-recent-activity');
    this.quickActionSubscribe = page.getByTestId('dashboard-action-subscribe');
    this.quickActionSearch = page.getByTestId('dashboard-action-search');
    this.quickActionAttendance = page.getByTestId('dashboard-action-attendance');
    this.memberCount = page.getByTestId('dashboard-member-count');
    this.subscriptionCount = page.getByTestId('dashboard-subscription-count');
    this.upcomingEventsCount = page.getByTestId('dashboard-upcoming-events');
    this.notificationBell = page.getByTestId('notification-bell');
    this.userMenu = page.getByTestId('user-menu');
    this.logoutButton = page.getByTestId('user-menu-logout');
    this.navLinks = page.locator('nav a');
  }

  async goto(): Promise<void> {
    await this.page.goto('/dashboard');
    await this.welcomeMessage.waitFor({ state: 'visible' });
  }

  /**
   * Open the user menu dropdown in the top-right corner.
   */
  async openUserMenu(): Promise<void> {
    await this.userMenu.click();
  }

  /**
   * Click logout from the user menu.
   */
  async logout(): Promise<void> {
    await this.openUserMenu();
    await this.logoutButton.click();
    await this.page.waitForURL('**/auth/login');
  }

  /**
   * Navigate to a specific section via quick action buttons.
   */
  async navigateToSearch(): Promise<void> {
    await this.quickActionSearch.click();
    await this.page.waitForURL('**/associations/search');
  }

  async navigateToAttendance(): Promise<void> {
    await this.quickActionAttendance.click();
    await this.page.waitForURL('**/attendance');
  }

  /**
   * Get the displayed welcome message text.
   */
  async getWelcomeText(): Promise<string | null> {
    return this.welcomeMessage.textContent();
  }

  /**
   * Get the number of family members displayed on the dashboard.
   */
  async getMemberCount(): Promise<string | null> {
    return this.memberCount.textContent();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.welcomeMessage).toBeVisible();
    await expect(this.familyCard).toBeVisible();
  }

  /**
   * Assert that the user is on the dashboard (post-login validation).
   */
  async expectLoggedIn(): Promise<void> {
    await expect(this.page).toHaveURL(/.*\/dashboard/);
    await expect(this.welcomeMessage).toBeVisible();
    await expect(this.userMenu).toBeVisible();
  }
}
