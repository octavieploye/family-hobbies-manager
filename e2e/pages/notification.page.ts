import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the notification system.
 *
 * Covers:
 * - Bell icon in the application toolbar (visible on all authenticated pages)
 * - Dropdown panel with recent notifications
 * - Full notification list page (/notifications)
 *
 * Used by: notification.spec.ts
 */
export class NotificationPage {
  readonly page: Page;

  /* Bell Icon (toolbar) */
  readonly bellIcon: Locator;
  readonly unreadBadge: Locator;

  /* Dropdown Panel */
  readonly dropdown: Locator;
  readonly dropdownItems: Locator;
  readonly markAllReadButton: Locator;
  readonly viewAllLink: Locator;

  /* Full List Page */
  readonly pageTitle: Locator;
  readonly notificationList: Locator;
  readonly notificationItems: Locator;
  readonly emptyState: Locator;
  readonly filterByType: Locator;
  readonly filterByRead: Locator;

  constructor(page: Page) {
    this.page = page;

    /* Bell Icon */
    this.bellIcon = page.getByTestId('notification-bell');
    this.unreadBadge = page.getByTestId('notification-unread-count');

    /* Dropdown Panel */
    this.dropdown = page.getByTestId('notification-dropdown');
    this.dropdownItems = page.getByTestId('notification-dropdown-item');
    this.markAllReadButton = page.getByTestId('mark-all-read-button');
    this.viewAllLink = page.getByTestId('view-all-notifications-link');

    /* Full List Page */
    this.pageTitle = page.getByRole('heading', { name: /notifications/i });
    this.notificationList = page.getByTestId('notification-list');
    this.notificationItems = page.getByTestId('notification-item');
    this.emptyState = page.getByTestId('notification-empty');
    this.filterByType = page.getByTestId('notification-filter-type');
    this.filterByRead = page.getByTestId('notification-filter-read');
  }

  /**
   * Navigate to the full notification list page.
   */
  async goto(): Promise<void> {
    await this.page.goto('/notifications');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Open the notification dropdown by clicking the bell icon.
   */
  async openDropdown(): Promise<void> {
    await this.bellIcon.click();
    await this.dropdown.waitFor({ state: 'visible' });
  }

  /**
   * Close the dropdown by clicking outside.
   */
  async closeDropdown(): Promise<void> {
    await this.page.keyboard.press('Escape');
    await this.dropdown.waitFor({ state: 'hidden' });
  }

  /**
   * Get the unread notification count from the badge.
   * Returns 0 if the badge is not visible.
   */
  async getUnreadCount(): Promise<number> {
    if (await this.unreadBadge.isVisible()) {
      const text = await this.unreadBadge.textContent();
      return parseInt(text || '0', 10);
    }
    return 0;
  }

  /**
   * Click "Mark all as read" in the dropdown.
   */
  async markAllAsRead(): Promise<void> {
    await this.markAllReadButton.click();
    await this.page.waitForTimeout(300);
  }

  /**
   * Mark a single notification as read by clicking it in the dropdown.
   */
  async markAsRead(index: number): Promise<void> {
    await this.dropdownItems.nth(index).click();
    await this.page.waitForTimeout(300);
  }

  /**
   * Navigate from the dropdown to the full notifications page.
   */
  async goToFullList(): Promise<void> {
    await this.viewAllLink.click();
    await this.page.waitForURL('**/notifications');
  }

  /**
   * Get the number of notification items on the full list page.
   */
  async getNotificationCount(): Promise<number> {
    return this.notificationItems.count();
  }

  /**
   * Get the number of items in the dropdown.
   */
  async getDropdownItemCount(): Promise<number> {
    return this.dropdownItems.count();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
  }

  async expectBellVisible(): Promise<void> {
    await expect(this.bellIcon).toBeVisible();
  }
}
