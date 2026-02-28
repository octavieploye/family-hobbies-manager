// e2e/specs/notification.spec.ts
// Notification E2E tests: bell icon, dropdown, mark read, full list, filtering.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { NotificationPage } from '../pages/notification.page';
import { DashboardPage } from '../pages/dashboard.page';
import { TEST_USERS, TEST_NOTIFICATIONS } from '../fixtures/test-data';

test.describe('Notifications', () => {
  let notificationPage: NotificationPage;

  test.beforeEach(async ({ page }) => {
    const loginPage = new LoginPage(page);
    notificationPage = new NotificationPage(page);

    await loginPage.goto();
    await loginPage.login(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
  });

  // -- Bell Icon -------------------------------------------------------------

  test.describe('Bell Icon', () => {
    test('should display the notification bell icon on dashboard', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.expectBellVisible();
    });

    test('should show unread badge count', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      const unreadCount = await notificationPage.getUnreadCount();
      expect(unreadCount).toBeGreaterThanOrEqual(
        TEST_NOTIFICATIONS.expectedUnreadCount
      );
    });

    test('should display bell icon on all authenticated pages', async ({ page }) => {
      const routes = ['/dashboard', '/families', '/associations/search', '/subscriptions'];

      for (const route of routes) {
        await page.goto(route);
        await notificationPage.expectBellVisible();
      }
    });
  });

  // -- Dropdown Panel --------------------------------------------------------

  test.describe('Dropdown Panel', () => {
    test('should open dropdown when clicking bell icon', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await expect(notificationPage.dropdown).toBeVisible();
    });

    test('should display notification items in the dropdown', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();

      const itemCount = await notificationPage.getDropdownItemCount();
      expect(itemCount).toBeGreaterThan(0);
    });

    test('should close dropdown on Escape key', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await expect(notificationPage.dropdown).toBeVisible();

      await notificationPage.closeDropdown();
      await expect(notificationPage.dropdown).toBeHidden();
    });

    test('should display "Mark all as read" button', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await expect(notificationPage.markAllReadButton).toBeVisible();
    });

    test('should display "View all" link to full notification list', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await expect(notificationPage.viewAllLink).toBeVisible();
    });
  });

  // -- Mark as Read ----------------------------------------------------------

  test.describe('Mark as Read', () => {
    test('should mark a single notification as read', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      const initialUnread = await notificationPage.getUnreadCount();

      await notificationPage.openDropdown();
      await notificationPage.markAsRead(0);

      const updatedUnread = await notificationPage.getUnreadCount();
      expect(updatedUnread).toBeLessThan(initialUnread);
    });

    test('should mark all notifications as read', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await notificationPage.markAllAsRead();

      // Badge should disappear or show 0
      const unreadCount = await notificationPage.getUnreadCount();
      expect(unreadCount).toBe(0);
    });
  });

  // -- Full List Page --------------------------------------------------------

  test.describe('Full List Page', () => {
    test('should display the full notification list page', async () => {
      await notificationPage.goto();
      await notificationPage.expectPageLoaded();
    });

    test('should display all notification items', async () => {
      await notificationPage.goto();

      const count = await notificationPage.getNotificationCount();
      expect(count).toBeGreaterThan(0);
    });

    test('should navigate from dropdown to full list', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await notificationPage.goToFullList();

      await expect(page).toHaveURL(/.*\/notifications/);
      await notificationPage.expectPageLoaded();
    });
  });

  // -- Filter Notifications --------------------------------------------------

  test.describe('Filter Notifications', () => {
    test('should filter notifications by type', async () => {
      await notificationPage.goto();

      await notificationPage.filterByType.click();
      await notificationPage.page
        .getByRole('option')
        .first()
        .click();

      await notificationPage.page.waitForTimeout(500);

      // Should still display the notification list
      await expect(notificationPage.notificationList).toBeVisible();
    });

    test('should filter notifications by read status', async () => {
      await notificationPage.goto();

      await notificationPage.filterByRead.click();
      await notificationPage.page
        .getByRole('option', { name: /non lu/i })
        .click();

      await notificationPage.page.waitForTimeout(500);

      await expect(notificationPage.notificationList).toBeVisible();
    });
  });
});
