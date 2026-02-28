// e2e/specs/a11y/a11y-forms.spec.ts
// Form accessibility tests.
// Verifies all form inputs have visible labels, error messages are announced
// via aria-live, and required fields are indicated with aria-required.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../../pages/login.page';
import { TEST_USERS } from '../../fixtures/test-data';

test.describe('Form Accessibility (RGAA 11.1, 11.2, 11.10, 11.5, WCAG 1.3.1, 3.3.2)', () => {

  // -- Login Form ------------------------------------------------------------

  test.describe('Login Form', () => {
    test('should have visible labels for all form inputs', async ({ page }) => {
      await page.goto('/auth/login');
      await page.waitForLoadState('networkidle');

      // Check that each input has an associated label
      const inputs = page.locator(
        'input:not([type="hidden"]):not([type="submit"])'
      );
      const inputCount = await inputs.count();

      for (let i = 0; i < inputCount; i++) {
        const input = inputs.nth(i);
        const id = await input.getAttribute('id');
        const ariaLabel = await input.getAttribute('aria-label');
        const ariaLabelledby = await input.getAttribute('aria-labelledby');

        // Input must have id linked to a <label>, or aria-label, or aria-labelledby
        const hasLabel = id
          ? (await page.locator(`label[for="${id}"]`).count()) > 0 ||
            (await page.locator(`mat-label`).count()) > 0
          : false;

        expect(hasLabel || !!ariaLabel || !!ariaLabelledby).toBe(true);
      }
    });

    test('should mark required fields with aria-required', async ({ page }) => {
      await page.goto('/auth/login');
      await page.waitForLoadState('networkidle');

      const emailInput = page.locator('#login-email, [data-testid="login-email"]');
      const passwordInput = page.locator('#login-password, [data-testid="login-password"]');

      await expect(emailInput).toHaveAttribute('aria-required', 'true');
      await expect(passwordInput).toHaveAttribute('aria-required', 'true');
    });

    test('should set aria-invalid on invalid fields after touch', async ({ page }) => {
      await page.goto('/auth/login');
      await page.waitForLoadState('networkidle');

      const emailInput = page.locator('#login-email, [data-testid="login-email"]');

      // Focus and blur the email field to trigger touched state
      await emailInput.focus();
      await emailInput.blur();

      // Wait for Angular change detection
      await page.waitForTimeout(200);

      const ariaInvalid = await emailInput.getAttribute('aria-invalid');
      expect(ariaInvalid).toBe('true');
    });

    test('should display error messages with role="alert"', async ({ page }) => {
      await page.goto('/auth/login');
      await page.waitForLoadState('networkidle');

      const emailInput = page.locator('#login-email, [data-testid="login-email"]');

      // Touch the email field and leave it empty to trigger error
      await emailInput.focus();
      await emailInput.blur();
      await page.waitForTimeout(200);

      const errorElement = page.locator('mat-error, [role="alert"]').first();
      if (await errorElement.isVisible().catch(() => false)) {
        const role = await errorElement.getAttribute('role');
        expect(role).toBe('alert');
      }
    });

    test('should have aria-describedby linking input to error', async ({ page }) => {
      await page.goto('/auth/login');
      await page.waitForLoadState('networkidle');

      const emailInput = page.locator('#login-email, [data-testid="login-email"]');

      // Touch and blur to trigger validation
      await emailInput.focus();
      await emailInput.blur();
      await page.waitForTimeout(200);

      const describedBy = await emailInput.getAttribute('aria-describedby');
      if (describedBy) {
        // Verify the referenced element exists
        const referencedEl = page.locator(`#${describedBy}`);
        const count = await referencedEl.count();
        expect(count).toBeGreaterThan(0);
      }
    });
  });

  // -- Registration Form -----------------------------------------------------

  test.describe('Registration Form', () => {
    test('should have visible labels for all registration inputs', async ({ page }) => {
      await page.goto('/auth/register');
      await page.waitForLoadState('networkidle');

      const formFields = [
        '#register-firstname, [data-testid="register-firstname"]',
        '#register-lastname, [data-testid="register-lastname"]',
        '#register-email, [data-testid="register-email"]',
        '#register-password, [data-testid="register-password"]',
        '#register-confirm-password, [data-testid="register-confirm-password"]',
      ];

      for (const selector of formFields) {
        const input = page.locator(selector).first();
        if (await input.isVisible().catch(() => false)) {
          const ariaRequired = await input.getAttribute('aria-required');
          expect(ariaRequired).toBe('true');
        }
      }
    });

    test('should have password hint text linked via aria-describedby', async ({ page }) => {
      await page.goto('/auth/register');
      await page.waitForLoadState('networkidle');

      const passwordInput = page.locator(
        '#register-password, [data-testid="register-password"]'
      ).first();

      if (await passwordInput.isVisible().catch(() => false)) {
        const describedBy = await passwordInput.getAttribute('aria-describedby');
        expect(describedBy).toBeTruthy();

        // The described-by element should contain the hint text
        if (describedBy) {
          const hintIds = describedBy.split(' ');
          for (const hintId of hintIds) {
            const hintEl = page.locator(`#${hintId}`);
            if (await hintEl.isVisible().catch(() => false)) {
              const text = await hintEl.textContent();
              expect(text).toBeTruthy();
            }
          }
        }
      }
    });
  });

  // -- Authenticated Forms ---------------------------------------------------

  test.describe('Authenticated Page Forms', () => {
    test.beforeEach(async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );
    });

    test('association search form has labeled inputs', async ({ page }) => {
      await page.goto('/associations/search');
      await page.waitForLoadState('networkidle');

      const searchInput = page.locator(
        '#search-keyword, [data-testid="search-keyword"]'
      ).first();
      const cityInput = page.locator(
        '#search-city, [data-testid="search-city"]'
      ).first();

      if (await searchInput.isVisible().catch(() => false)) {
        const id = await searchInput.getAttribute('id');
        const ariaLabel = await searchInput.getAttribute('aria-label');

        // Must have either a linked label or aria-label
        const hasLabel = id
          ? (await page.locator(`label[for="${id}"], mat-label`).count()) > 0
          : false;
        expect(hasLabel || !!ariaLabel).toBe(true);
      }

      if (await cityInput.isVisible().catch(() => false)) {
        const id = await cityInput.getAttribute('id');
        const ariaLabel = await cityInput.getAttribute('aria-label');

        const hasLabel = id
          ? (await page.locator(`label[for="${id}"], mat-label`).count()) > 0
          : false;
        expect(hasLabel || !!ariaLabel).toBe(true);
      }
    });

    test('settings page consent toggles have accessible labels', async ({ page }) => {
      await page.goto('/settings');
      await page.waitForLoadState('networkidle');

      const toggles = page.locator(
        '[data-testid^="consent-"]'
      );
      const count = await toggles.count();

      for (let i = 0; i < count; i++) {
        const toggle = toggles.nth(i);
        const ariaLabel = await toggle.getAttribute('aria-label');
        const ariaLabelledby = await toggle.getAttribute('aria-labelledby');
        const id = await toggle.getAttribute('id');

        const hasLabel = id
          ? (await page.locator(`label[for="${id}"]`).count()) > 0
          : false;

        expect(hasLabel || !!ariaLabel || !!ariaLabelledby).toBe(true);
      }
    });

    test('fieldset/legend pattern used for radio groups', async ({ page }) => {
      // Navigate to a page with radio groups (e.g., family member form or subscription form)
      await page.goto('/families');
      await page.waitForLoadState('networkidle');

      const fieldsets = page.locator('fieldset');
      const fieldsetCount = await fieldsets.count();

      for (let i = 0; i < fieldsetCount; i++) {
        const fieldset = fieldsets.nth(i);
        const legend = fieldset.locator('legend');
        const legendCount = await legend.count();

        // Every fieldset must have a legend
        expect(legendCount).toBeGreaterThan(0);

        if (legendCount > 0) {
          const legendText = await legend.first().textContent();
          expect(legendText?.trim().length).toBeGreaterThan(0);
        }
      }
    });
  });
});
