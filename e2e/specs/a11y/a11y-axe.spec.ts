// e2e/specs/a11y/a11y-axe.spec.ts
// Automated axe-core accessibility scans on every page.
// Asserts zero critical and zero serious violations.
// Moderate violations are logged as warnings.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../../pages/login.page';
import { TEST_USERS } from '../../fixtures/test-data';
import {
  AUTHENTICATED_PAGES,
  PUBLIC_PAGES,
  assertNoSeriousViolations,
  runAxeScan,
} from '../../helpers/a11y-helper';

test.describe('Axe-core Accessibility Audit (WCAG 2.1 AA)', () => {

  // -- Public Pages ----------------------------------------------------------

  test.describe('Public Pages', () => {
    for (const { route, name } of PUBLIC_PAGES) {
      test(`${name} (${route}) - zero critical/serious violations`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        await assertNoSeriousViolations(page, name);
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
      test(`${name} (${route}) - zero critical/serious violations`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        await assertNoSeriousViolations(page, name);
      });
    }
  });

  // -- WCAG Tag Coverage ----------------------------------------------------

  test.describe('WCAG 2.1 AA Full Tag Scan', () => {
    test('login page - full WCAG 2.1 AA scan with detailed results', async ({ page }) => {
      await page.goto('/auth/login');
      await page.waitForLoadState('networkidle');

      const results = await runAxeScan(page, {
        tags: ['wcag2a', 'wcag2aa', 'wcag21aa'],
      });

      // Log full results for debugging
      console.log(`Axe results for /auth/login:`);
      console.log(`  Passes: ${results.passes}`);
      console.log(`  Violations: ${results.violations.length}`);
      console.log(`  Incomplete: ${results.incomplete}`);

      // Log each violation
      for (const violation of results.violations) {
        console.log(
          `  [${violation.impact}] ${violation.id}: ${violation.description}`
        );
      }

      // Assert no critical/serious
      const criticalOrSerious = results.violations.filter(
        (v) => v.impact === 'critical' || v.impact === 'serious'
      );
      expect(criticalOrSerious).toHaveLength(0);
    });

    test('dashboard - full WCAG 2.1 AA scan with detailed results', async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );

      await page.goto('/dashboard');
      await page.waitForLoadState('networkidle');

      const results = await runAxeScan(page, {
        tags: ['wcag2a', 'wcag2aa', 'wcag21aa'],
      });

      console.log(`Axe results for /dashboard:`);
      console.log(`  Passes: ${results.passes}`);
      console.log(`  Violations: ${results.violations.length}`);
      console.log(`  Incomplete: ${results.incomplete}`);

      for (const violation of results.violations) {
        console.log(
          `  [${violation.impact}] ${violation.id}: ${violation.description}`
        );
      }

      const criticalOrSerious = results.violations.filter(
        (v) => v.impact === 'critical' || v.impact === 'serious'
      );
      expect(criticalOrSerious).toHaveLength(0);
    });
  });

  // -- Color Contrast Specific Check -----------------------------------------

  test.describe('Color Contrast', () => {
    test.beforeEach(async ({ page }) => {
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );
    });

    for (const { route, name } of AUTHENTICATED_PAGES) {
      test(`${name} (${route}) - no color contrast violations`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        const results = await runAxeScan(page, {
          tags: ['wcag2aa'],
        });

        const contrastViolations = results.violations.filter(
          (v) => v.id === 'color-contrast'
        );

        if (contrastViolations.length > 0) {
          console.warn(
            `[contrast-warning] ${name}: ${contrastViolations.length} contrast issue(s):`,
            contrastViolations
              .flatMap((v) => v.nodes.map((n) => `  - ${n.target.join(' > ')}: ${n.html.slice(0, 80)}`))
              .join('\n')
          );
        }

        // Fail only on critical/serious contrast issues
        const seriousContrast = contrastViolations.filter(
          (v) => v.impact === 'critical' || v.impact === 'serious'
        );
        expect(seriousContrast).toHaveLength(0);
      });
    }
  });

  // -- Aggregate Report ------------------------------------------------------

  test.describe('Aggregate Accessibility Report', () => {
    test('all pages pass with zero critical + zero serious violations', async ({ page }) => {
      const allPages = [
        ...PUBLIC_PAGES,
      ];

      // Login for authenticated pages
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );

      const authenticatedPages = [...AUTHENTICATED_PAGES];
      allPages.push(...authenticatedPages);

      const results: {
        page: string;
        violations: number;
        critical: number;
        serious: number;
        moderate: number;
      }[] = [];

      for (const { route, name } of allPages) {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        const scan = await runAxeScan(page);
        results.push({
          page: `${name} (${route})`,
          violations: scan.violations.length,
          critical: scan.violations.filter((v) => v.impact === 'critical')
            .length,
          serious: scan.violations.filter((v) => v.impact === 'serious')
            .length,
          moderate: scan.violations.filter((v) => v.impact === 'moderate')
            .length,
        });
      }

      // Log the aggregate report
      console.log('\n=== Accessibility Audit Report ===');
      console.table(results);

      // Assert all pages have zero critical + zero serious
      for (const result of results) {
        expect(result.critical).toBe(0);
        expect(result.serious).toBe(0);
      }
    });
  });
});
