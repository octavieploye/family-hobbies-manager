// e2e/specs/a11y/a11y-landmarks.spec.ts
// Semantic landmark and heading hierarchy tests.
// Verifies <main>, <nav>, <header>, <footer> on every page
// and correct heading hierarchy (h1 -> h2 -> h3, no skips).

import { test, expect } from '@playwright/test';
import { LoginPage } from '../../pages/login.page';
import { TEST_USERS } from '../../fixtures/test-data';
import {
  AUTHENTICATED_PAGES,
  PUBLIC_PAGES,
  checkLandmarks,
  checkHeadingHierarchy,
} from '../../helpers/a11y-helper';

test.describe('Semantic Landmarks (RGAA 9.2, 12.6, WCAG 1.3.1, 2.4.1)', () => {

  // -- Public Pages ----------------------------------------------------------

  test.describe('Public Pages', () => {
    for (const { route, name } of PUBLIC_PAGES) {
      test(`${name} (${route}) - has required landmarks`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        const landmarks = await checkLandmarks(page);

        expect(landmarks.hasMain).toBe(true);
        expect(landmarks.hasHeader).toBe(true);
        expect(landmarks.hasFooter).toBe(true);
        expect(landmarks.mainHasId).toBe(true);
      });
    }

    for (const { route, name } of PUBLIC_PAGES) {
      test(`${name} (${route}) - has valid heading hierarchy`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        const headings = await checkHeadingHierarchy(page);

        expect(headings.hasH1).toBe(true);
        expect(headings.isValid).toBe(true);
      });
    }
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
      test(`${name} (${route}) - has required landmarks`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        const landmarks = await checkLandmarks(page);

        expect(landmarks.hasMain).toBe(true);
        expect(landmarks.hasNav).toBe(true);
        expect(landmarks.hasHeader).toBe(true);
        expect(landmarks.hasFooter).toBe(true);
        expect(landmarks.mainHasId).toBe(true);
      });
    }

    for (const { route, name } of AUTHENTICATED_PAGES) {
      test(`${name} (${route}) - has valid heading hierarchy`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        const headings = await checkHeadingHierarchy(page);

        expect(headings.hasH1).toBe(true);
        expect(headings.isValid).toBe(true);
      });
    }
  });

  // -- html lang attribute ---------------------------------------------------

  test.describe('Language Attribute', () => {
    test('should have lang="fr" on the <html> element', async ({ page }) => {
      await page.goto('/auth/login');
      await page.waitForLoadState('networkidle');

      const lang = await page.evaluate(() =>
        document.documentElement.getAttribute('lang')
      );
      expect(lang).toBe('fr');
    });
  });

  // -- Navigation landmarks have labels --------------------------------------

  test.describe('Navigation Labels', () => {
    test('should have labeled navigation landmarks', async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );

      await page.goto('/dashboard');
      await page.waitForLoadState('networkidle');

      const navElements = page.locator('nav, [role="navigation"]');
      const count = await navElements.count();

      for (let i = 0; i < count; i++) {
        const nav = navElements.nth(i);
        const ariaLabel = await nav.getAttribute('aria-label');
        const ariaLabelledby = await nav.getAttribute('aria-labelledby');

        // Each nav must have either aria-label or aria-labelledby
        expect(ariaLabel || ariaLabelledby).toBeTruthy();
      }
    });
  });

  // -- Page title ------------------------------------------------------------

  test.describe('Page Title', () => {
    test('should have a non-empty <title> element', async ({ page }) => {
      await page.goto('/auth/login');
      const title = await page.title();
      expect(title).toBeTruthy();
      expect(title.length).toBeGreaterThan(0);
    });
  });

  // -- Unique h1 per page ----------------------------------------------------

  test.describe('Single h1 Per Page', () => {
    test.beforeEach(async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );
    });

    for (const { route, name } of AUTHENTICATED_PAGES) {
      test(`${name} (${route}) - has exactly one h1`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        const h1Count = await page.locator('h1').count();
        expect(h1Count).toBe(1);
      });
    }
  });
});
