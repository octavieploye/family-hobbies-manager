// e2e/specs/subscription.spec.ts
// Subscription E2E tests: view subscriptions, subscribe to activity, cancel subscription, filter.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { SubscriptionPage } from '../pages/subscription.page';
import { AssociationDetailPage } from '../pages/association-detail.page';
import { TEST_USERS, TEST_SUBSCRIPTIONS, TEST_ASSOCIATIONS } from '../fixtures/test-data';

test.describe('Subscription Management', () => {
  let subscriptionPage: SubscriptionPage;

  test.beforeEach(async ({ page }) => {
    const loginPage = new LoginPage(page);
    subscriptionPage = new SubscriptionPage(page);

    await loginPage.goto();
    await loginPage.login(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
  });

  // -- View Subscriptions ----------------------------------------------------

  test.describe('View Subscriptions', () => {
    test('should display the subscriptions page', async () => {
      await subscriptionPage.goto();
      await subscriptionPage.expectPageLoaded();
    });

    test('should display pre-seeded subscriptions', async () => {
      await subscriptionPage.goto();
      await subscriptionPage.expectHasSubscriptions();

      const count = await subscriptionPage.getSubscriptionCount();
      expect(count).toBeGreaterThanOrEqual(2); // At least Lucas + Emma
    });

    test('should display correct association name for first subscription', async () => {
      await subscriptionPage.goto();

      const associationName = await subscriptionPage.getAssociationName(0);
      expect(associationName).toBeTruthy();
    });

    test('should display active status for pre-seeded subscriptions', async () => {
      await subscriptionPage.goto();

      const status = await subscriptionPage.getStatus(0);
      expect(status).toBeTruthy();
      expect(status!.toLowerCase()).toContain('actif');
    });
  });

  // -- Subscribe to Activity -------------------------------------------------

  test.describe('Subscribe to Activity', () => {
    test('should subscribe a family member to an activity from detail page', async ({ page }) => {
      const detailPage = new AssociationDetailPage(page);
      await detailPage.goto(TEST_ASSOCIATIONS.musiqueToulouse.id);

      await detailPage.subscribeToActivity(0); // Subscribe to first activity (Piano)

      // Should navigate to subscription confirmation or subscriptions list
      await page.waitForURL(/.*\/(subscriptions|checkout)/);
    });

    test('should subscribe a family member from the subscriptions page', async () => {
      await subscriptionPage.goto();

      const initialCount = await subscriptionPage.getSubscriptionCount();

      await subscriptionPage.subscribeMember(
        'Marie Dupont',
        'Chorale'
      );

      const updatedCount = await subscriptionPage.getSubscriptionCount();
      expect(updatedCount).toBe(initialCount + 1);
    });
  });

  // -- Cancel Subscription ---------------------------------------------------

  test.describe('Cancel Subscription', () => {
    test('should cancel a subscription with confirmation', async () => {
      await subscriptionPage.goto();

      const initialCount = await subscriptionPage.getSubscriptionCount();

      await subscriptionPage.cancelSubscription(initialCount - 1);

      // The subscription status should change to cancelled
      const status = await subscriptionPage.getStatus(initialCount - 1);
      expect(status!.toLowerCase()).toMatch(/annul/);
    });

    test('should not cancel when dismissing the confirmation dialog', async () => {
      await subscriptionPage.goto();

      const initialCount = await subscriptionPage.getSubscriptionCount();

      // Click cancel on the last subscription row
      const row = subscriptionPage.subscriptionRows.nth(initialCount - 1);
      await row.getByTestId('cancel-subscription-button').click();
      await expect(subscriptionPage.cancelDialog).toBeVisible();

      // Dismiss the dialog
      await subscriptionPage.cancelCancelButton.click();
      await expect(subscriptionPage.cancelDialog).toBeHidden();

      // Status should remain unchanged
      const status = await subscriptionPage.getStatus(initialCount - 1);
      expect(status!.toLowerCase()).not.toMatch(/annul/);
    });
  });

  // -- Filter Subscriptions --------------------------------------------------

  test.describe('Filter Subscriptions', () => {
    test('should filter subscriptions by member', async () => {
      await subscriptionPage.goto();

      await subscriptionPage.filterByMember.click();
      await subscriptionPage.page.getByRole('option', { name: /Lucas/i }).click();

      const count = await subscriptionPage.getSubscriptionCount();
      expect(count).toBeGreaterThanOrEqual(1);
    });

    test('should filter subscriptions by status', async () => {
      await subscriptionPage.goto();

      await subscriptionPage.filterByStatus.click();
      await subscriptionPage.page.getByRole('option', { name: /Actif/i }).click();

      const count = await subscriptionPage.getSubscriptionCount();
      expect(count).toBeGreaterThanOrEqual(1);
    });
  });
});
