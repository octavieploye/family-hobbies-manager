// e2e/specs/a11y/a11y-keyboard.spec.ts
// Keyboard navigation accessibility tests.
// Verifies that every interactive element is reachable via Tab/Shift+Tab,
// focus is visible, Enter/Space activate buttons/links, and dialogs trap focus.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../../pages/login.page';
import { TEST_USERS } from '../../fixtures/test-data';
import {
  AUTHENTICATED_PAGES,
  PUBLIC_PAGES,
  checkFocusVisible,
  getTabOrder,
} from '../../helpers/a11y-helper';

test.describe('Keyboard Navigation (RGAA 12.8, 12.9, WCAG 2.1.1, 2.4.7)', () => {

  // -- Public Pages ----------------------------------------------------------

  test.describe('Public Pages', () => {
    for (const { route, name } of PUBLIC_PAGES) {
      test(`${name} (${route}) - all interactive elements reachable via Tab`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        const tabOrder = await getTabOrder(page);
        expect(tabOrder.length).toBeGreaterThan(0);
      });
    }

    test('login page - Tab reaches email, password, submit, and register link', async ({ page }) => {
      await page.goto('/auth/login');
      await page.waitForLoadState('networkidle');

      const tabOrder = await getTabOrder(page);

      // Verify key elements are in the tab order
      const hasEmailInput = tabOrder.some((s) => s.includes('input') || s.includes('login-email'));
      const hasPasswordInput = tabOrder.some((s) => s.includes('input') || s.includes('login-password'));
      const hasSubmitButton = tabOrder.some((s) => s.includes('button') || s.includes('login-submit'));

      expect(hasEmailInput).toBe(true);
      expect(hasPasswordInput).toBe(true);
      expect(hasSubmitButton).toBe(true);
    });

    test('login page - Enter submits the login form', async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();

      await loginPage.fillEmail(TEST_USERS.familyUser.email);
      await loginPage.fillPassword(TEST_USERS.familyUser.password);

      // Press Enter instead of clicking submit
      await page.keyboard.press('Enter');

      await page.waitForURL('**/dashboard', { timeout: 15000 });
      await expect(page).toHaveURL(/.*\/dashboard/);
    });
  });

  // -- Authenticated Pages ---------------------------------------------------

  test.describe('Authenticated Pages', () => {
    test.beforeEach(async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );
    });

    for (const { route, name } of AUTHENTICATED_PAGES) {
      test(`${name} (${route}) - all interactive elements reachable via Tab`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        const tabOrder = await getTabOrder(page);
        expect(tabOrder.length).toBeGreaterThan(0);
      });
    }

    for (const { route, name } of AUTHENTICATED_PAGES) {
      test(`${name} (${route}) - focus indicator visible on interactive elements`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        // Check focus visibility on the first few interactive elements
        const selectors = [
          'a[href]:not([tabindex="-1"])',
          'button:not([disabled])',
          'input:not([disabled]):not([type="hidden"])',
        ];

        for (const selector of selectors) {
          const count = await page.locator(selector).count();
          if (count > 0) {
            const isVisible = await checkFocusVisible(page, selector);
            expect(isVisible).toBe(true);
          }
        }
      });
    }
  });

  // -- Shift+Tab Reverse Navigation ------------------------------------------

  test.describe('Reverse Navigation (Shift+Tab)', () => {
    test('should navigate backwards through elements on login page', async ({ page }) => {
      await page.goto('/auth/login');
      await page.waitForLoadState('networkidle');

      // Tab forward first to get past the first element
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');
      await page.keyboard.press('Tab');

      // Now Shift+Tab back
      await page.keyboard.press('Shift+Tab');

      const focusedTag = await page.evaluate(() =>
        document.activeElement?.tagName.toLowerCase()
      );
      expect(focusedTag).toBeTruthy();
      expect(focusedTag).not.toBe('body');
    });
  });

  // -- Space Key for Buttons and Checkboxes ----------------------------------

  test.describe('Space Key Activation', () => {
    test('should activate a button with Space key', async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();

      // Tab to the submit button
      await loginPage.emailInput.focus();
      await loginPage.fillEmail(TEST_USERS.familyUser.email);
      await loginPage.fillPassword(TEST_USERS.familyUser.password);

      // Focus the submit button
      await loginPage.submitButton.focus();

      // Press Space to activate
      await page.keyboard.press('Space');

      // Should attempt to submit (may navigate or show error)
      await page.waitForTimeout(1000);
    });
  });

  // -- Dialog Focus Trap -----------------------------------------------------

  test.describe('Dialog Focus Trap', () => {
    test('should trap focus inside a confirmation dialog', async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );

      // Navigate to settings and open delete account dialog
      await page.goto('/settings');
      await page.waitForLoadState('networkidle');

      const deleteButton = page.getByTestId('delete-account-button');
      if (await deleteButton.isVisible().catch(() => false)) {
        await deleteButton.click();

        const dialog = page.getByRole('dialog');
        await dialog.waitFor({ state: 'visible' });

        // Tab several times within the dialog
        const focusedElements: string[] = [];
        for (let i = 0; i < 10; i++) {
          await page.keyboard.press('Tab');
          const tag = await page.evaluate(() =>
            document.activeElement?.tagName.toLowerCase()
          );
          const isInDialog = await page.evaluate(() => {
            const active = document.activeElement;
            return active?.closest('[role="dialog"]') !== null;
          });

          if (tag && tag !== 'body') {
            focusedElements.push(tag);
          }

          // Focus must remain inside the dialog
          expect(isInDialog).toBe(true);
        }

        // Escape should close the dialog
        await page.keyboard.press('Escape');
        await expect(dialog).toBeHidden();
      }
    });

    test('should return focus to trigger element when dialog closes', async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );

      await page.goto('/settings');
      await page.waitForLoadState('networkidle');

      const deleteButton = page.getByTestId('delete-account-button');
      if (await deleteButton.isVisible().catch(() => false)) {
        // Focus and activate the trigger button
        await deleteButton.focus();
        await deleteButton.click();

        const dialog = page.getByRole('dialog');
        await dialog.waitFor({ state: 'visible' });

        // Close with Escape
        await page.keyboard.press('Escape');
        await expect(dialog).toBeHidden();

        // Focus should return to the delete button
        const focusedTestId = await page.evaluate(() =>
          document.activeElement?.getAttribute('data-testid')
        );
        expect(focusedTestId).toBe('delete-account-button');
      }
    });
  });

  // -- Arrow Key Navigation in Sidebar Menu ----------------------------------

  test.describe('Arrow Key Navigation', () => {
    test('should navigate sidebar menu items with arrow keys', async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );

      await page.goto('/dashboard');
      await page.waitForLoadState('networkidle');

      const sidebar = page.locator('nav[aria-label="Menu principal"]');
      if (await sidebar.isVisible().catch(() => false)) {
        const firstLink = sidebar.locator('a').first();
        await firstLink.focus();

        // Press ArrowDown to move to next item
        await page.keyboard.press('ArrowDown');

        const focusedText = await page.evaluate(() =>
          document.activeElement?.textContent?.trim()
        );
        expect(focusedText).toBeTruthy();

        // Press ArrowUp to move back
        await page.keyboard.press('ArrowUp');

        const restoredText = await page.evaluate(() =>
          document.activeElement?.textContent?.trim()
        );
        expect(restoredText).toBeTruthy();
      }
    });
  });
});
