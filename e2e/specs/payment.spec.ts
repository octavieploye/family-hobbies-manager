// e2e/specs/payment.spec.ts
// Payment E2E tests: view payments, payment detail, checkout initiation, webhook-simulated status update.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { PaymentPage } from '../pages/payment.page';
import { ApiHelper } from '../helpers/api-helper';
import { TEST_USERS, TEST_PAYMENTS } from '../fixtures/test-data';

test.describe('Payments', () => {
  let paymentPage: PaymentPage;
  const apiHelper = new ApiHelper();

  test.beforeAll(async () => {
    await apiHelper.init();
    await apiHelper.authenticate(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
  });

  test.afterAll(async () => {
    await apiHelper.dispose();
  });

  test.beforeEach(async ({ page }) => {
    const loginPage = new LoginPage(page);
    paymentPage = new PaymentPage(page);

    await loginPage.goto();
    await loginPage.login(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
  });

  // -- Payment List ----------------------------------------------------------

  test.describe('Payment List', () => {
    test('should display the payments page', async () => {
      await paymentPage.goto();
      await paymentPage.expectPageLoaded();
    });

    test('should display pre-seeded payment rows', async () => {
      await paymentPage.goto();

      const count = await paymentPage.getPaymentCount();
      expect(count).toBeGreaterThanOrEqual(2); // Completed + Pending
    });

    test('should display correct payment amount', async () => {
      await paymentPage.goto();

      const amount = await paymentPage.getPaymentAmount(0);
      expect(amount).toBeTruthy();
      // Amount should contain a number with EUR formatting
      expect(amount).toMatch(/\d/);
    });

    test('should display payment status badges', async () => {
      await paymentPage.goto();

      const status = await paymentPage.getPaymentStatus(0);
      expect(status).toBeTruthy();
    });
  });

  // -- Payment Detail --------------------------------------------------------

  test.describe('Payment Detail', () => {
    test('should navigate to payment detail from list', async ({ page }) => {
      await paymentPage.goto();

      await paymentPage.viewPaymentDetail(0);

      await expect(page).toHaveURL(/.*\/payments\/.+/);
      await paymentPage.expectPaymentDetailLoaded();
    });

    test('should display payment detail fields', async () => {
      await paymentPage.gotoDetail(TEST_PAYMENTS.completedPayment.id);

      await paymentPage.expectPaymentDetailLoaded();

      const amount = await paymentPage.paymentAmount.textContent();
      expect(amount).toContain(TEST_PAYMENTS.completedPayment.amount);

      const association = await paymentPage.paymentAssociation.textContent();
      expect(association).toContain(TEST_PAYMENTS.completedPayment.associationName);
    });

    test('should display the payment timeline for completed payment', async () => {
      await paymentPage.gotoDetail(TEST_PAYMENTS.completedPayment.id);

      const stepCount = await paymentPage.getTimelineStepCount();
      expect(stepCount).toBeGreaterThanOrEqual(2); // At least: created, completed
    });

    test('should navigate back to payment list from detail', async ({ page }) => {
      await paymentPage.gotoDetail(TEST_PAYMENTS.completedPayment.id);

      await paymentPage.backToListLink.click();
      await expect(page).toHaveURL(/.*\/payments$/);
    });
  });

  // -- Payment Status Filter -------------------------------------------------

  test.describe('Payment Filters', () => {
    test('should filter payments by status', async () => {
      await paymentPage.goto();

      await paymentPage.filterByStatus.click();
      await paymentPage.page.getByRole('option', { name: /Termine/i }).click();

      const count = await paymentPage.getPaymentCount();
      expect(count).toBeGreaterThanOrEqual(1);
    });
  });

  // -- Checkout Flow ---------------------------------------------------------

  test.describe('Checkout Flow', () => {
    test('should initiate a checkout session', async ({ page }) => {
      await paymentPage.goto();

      // If a checkout button is visible (e.g., for pending subscriptions)
      const checkoutVisible = await paymentPage.checkoutButton.isVisible()
        .catch(() => false);

      if (checkoutVisible) {
        await paymentPage.initiateCheckout();

        // Should either redirect to HelloAsso checkout or show a loading state
        await expect(
          paymentPage.checkoutLoadingIndicator.or(
            page.locator('text=helloasso').first()
          )
        ).toBeVisible({ timeout: 10000 });
      }
    });
  });

  // -- Webhook Simulation ----------------------------------------------------

  test.describe('Webhook Status Update', () => {
    test('should update payment status after webhook callback', async ({ page }) => {
      // Simulate a HelloAsso payment webhook for the pending payment
      await apiHelper.simulateWebhook('Payment', {
        id: TEST_PAYMENTS.pendingPayment.id,
        state: 'Authorized',
        amount: parseInt(TEST_PAYMENTS.pendingPayment.amount, 10) * 100,
      });

      // Wait for the webhook to be processed
      await page.waitForTimeout(2000);

      // Verify the payment status updated via API
      const statusUpdated = await apiHelper.verifyPaymentStatus(
        TEST_PAYMENTS.pendingPayment.id,
        'COMPLETED'
      );
      expect(statusUpdated).toBe(true);

      // Verify the UI reflects the updated status
      await paymentPage.gotoDetail(TEST_PAYMENTS.pendingPayment.id);

      const statusText = await paymentPage.paymentStatus.textContent();
      expect(statusText!.toLowerCase()).toMatch(/termin|complet/);
    });
  });

  // -- Invoice Download ------------------------------------------------------

  test.describe('Invoice Download', () => {
    test('should download invoice for completed payment', async () => {
      await paymentPage.gotoDetail(TEST_PAYMENTS.completedPayment.id);

      const downloadButtonVisible = await paymentPage.downloadInvoiceButton
        .isVisible()
        .catch(() => false);

      if (downloadButtonVisible) {
        const [download] = await Promise.all([
          paymentPage.page.waitForEvent('download'),
          paymentPage.downloadInvoiceButton.click(),
        ]);

        const filename = download.suggestedFilename();
        expect(filename).toMatch(/\.pdf$/);
      }
    });
  });
});
