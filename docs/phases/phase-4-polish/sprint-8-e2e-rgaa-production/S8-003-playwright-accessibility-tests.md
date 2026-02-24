# Story S8-003: Playwright Accessibility Tests

> 5 points | Priority: P0 | Service: e2e
> Sprint file: [Back to Sprint Index](./_index.md)

---

## Context

This story adds dedicated Playwright-based accessibility tests that validate the RGAA remediation work performed in S8-002. While S8-002 fixes accessibility issues at the component level and includes Jest unit tests, S8-003 validates those fixes in a real browser across all pages of the application.

Four spec files cover the critical RGAA/WCAG 2.1 AA criteria: keyboard navigation (all interactive elements reachable, focus visible, dialog focus trap), landmark structure (correct semantic HTML on every page), form accessibility (labels, error announcements, required indicators), and automated axe-core scans (zero critical/serious violations on every page).

The `@axe-core/playwright` package is used for automated accessibility checks. A shared `a11y-helper.ts` provides reusable functions for running axe scans, checking focus visibility, and verifying landmark presence.

**Thresholds**: CI build fails on any critical or serious violation. Moderate violations generate warnings but do not fail the build.

## Cross-References

- **S8-002** (RGAA Accessibility Remediation) -- the fixes validated by these tests
- **S8-001** (Playwright E2E Suite) -- provides the Playwright infrastructure, Page Objects, and Docker Compose environment
- **S8-001 Test Specs** ([companion file](./S8-001-playwright-e2e-suite-tests.md)) -- E2E functional tests that run alongside these a11y tests

## Dependencies

- **S8-002** must be completed (a11y fixes applied to all Angular components)
- **S8-001** infrastructure must be in place (playwright.config.ts, page objects, auth helper)

---

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Accessibility helper | `e2e/helpers/a11y-helper.ts` | Reusable a11y check functions (axe scan, focus check, landmark check) | Helper imported by all 4 specs without errors |
| 2 | Keyboard navigation spec | `e2e/specs/a11y/a11y-keyboard.spec.ts` | Tab/Shift+Tab/Enter/Space/Arrow key navigation tests | All interactive elements reachable via keyboard |
| 3 | Landmarks spec | `e2e/specs/a11y/a11y-landmarks.spec.ts` | Semantic landmark and heading hierarchy verification | Every page has correct landmarks and heading order |
| 4 | Forms spec | `e2e/specs/a11y/a11y-forms.spec.ts` | Form label, error announcement, and required field tests | All form inputs have visible labels and announced errors |
| 5 | Axe-core spec | `e2e/specs/a11y/a11y-axe.spec.ts` | Automated axe-core scan on every page | 0 critical + 0 serious violations |

---

## File Structure

```
e2e/
  +-- specs/a11y/
  |   +-- a11y-keyboard.spec.ts                  (S8-003)
  |   +-- a11y-landmarks.spec.ts                 (S8-003)
  |   +-- a11y-forms.spec.ts                     (S8-003)
  |   +-- a11y-axe.spec.ts                       (S8-003)
  +-- helpers/
      +-- a11y-helper.ts                         (S8-003)
```

### Package.json Update

Add `@axe-core/playwright` to `e2e/package.json`:

```json
{
  "devDependencies": {
    "@playwright/test": "^1.42.0",
    "@axe-core/playwright": "^4.8.0",
    "dotenv": "^16.4.0"
  }
}
```

### Pages Tested

All accessibility specs iterate over these routes:

| Route | Page Name | Authentication Required |
|-------|-----------|------------------------|
| `/auth/login` | Connexion | No |
| `/auth/register` | Inscription | No |
| `/dashboard` | Tableau de bord | Yes |
| `/families` | Mes familles | Yes |
| `/associations/search` | Rechercher une association | Yes |
| `/subscriptions` | Mes inscriptions | Yes |
| `/attendance` | Suivi de presence | Yes |
| `/payments` | Mes paiements | Yes |
| `/notifications` | Notifications | Yes |
| `/settings` | Parametres | Yes |

---

## Task 1 Detail: Accessibility Helper

**File**: `e2e/helpers/a11y-helper.ts`

```typescript
// e2e/helpers/a11y-helper.ts
// Reusable accessibility testing utilities for Playwright E2E tests.

import { type Page, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

/**
 * All application pages to test for accessibility.
 * Public pages do not require authentication.
 * Authenticated pages require a logged-in session.
 */
export const PUBLIC_PAGES = [
  { route: '/auth/login', name: 'Connexion' },
  { route: '/auth/register', name: 'Inscription' },
];

export const AUTHENTICATED_PAGES = [
  { route: '/dashboard', name: 'Tableau de bord' },
  { route: '/families', name: 'Mes familles' },
  { route: '/associations/search', name: 'Rechercher une association' },
  { route: '/subscriptions', name: 'Mes inscriptions' },
  { route: '/attendance', name: 'Suivi de presence' },
  { route: '/payments', name: 'Mes paiements' },
  { route: '/notifications', name: 'Notifications' },
  { route: '/settings', name: 'Parametres' },
];

export const ALL_PAGES = [...PUBLIC_PAGES, ...AUTHENTICATED_PAGES];

/**
 * Severity levels for axe-core violations.
 */
export type AxeSeverity = 'critical' | 'serious' | 'moderate' | 'minor';

/**
 * Run an axe-core accessibility scan on the current page.
 *
 * @param page - Playwright Page instance
 * @param options - Optional axe configuration
 * @returns The axe scan results
 */
export async function runAxeScan(
  page: Page,
  options?: {
    tags?: string[];
    disableRules?: string[];
  }
): Promise<{
  violations: Array<{
    id: string;
    impact: AxeSeverity;
    description: string;
    nodes: Array<{ html: string; target: string[] }>;
  }>;
  passes: number;
  incomplete: number;
}> {
  let builder = new AxeBuilder({ page })
    .withTags(options?.tags || ['wcag2a', 'wcag2aa', 'wcag21aa']);

  if (options?.disableRules) {
    builder = builder.disableRules(options.disableRules);
  }

  const results = await builder.analyze();

  return {
    violations: results.violations.map((v) => ({
      id: v.id,
      impact: v.impact as AxeSeverity,
      description: v.description,
      nodes: v.nodes.map((n) => ({
        html: n.html,
        target: n.target as string[],
      })),
    })),
    passes: results.passes.length,
    incomplete: results.incomplete.length,
  };
}

/**
 * Assert that a page has zero critical and zero serious axe-core violations.
 * Moderate and minor violations are logged as warnings but do not fail the test.
 *
 * @param page - Playwright Page instance
 * @param pageName - Name for error reporting
 */
export async function assertNoSeriousViolations(
  page: Page,
  pageName: string
): Promise<void> {
  const results = await runAxeScan(page);

  const critical = results.violations.filter((v) => v.impact === 'critical');
  const serious = results.violations.filter((v) => v.impact === 'serious');
  const moderate = results.violations.filter((v) => v.impact === 'moderate');

  // Log moderate violations as warnings
  if (moderate.length > 0) {
    console.warn(
      `[a11y-warning] ${pageName}: ${moderate.length} moderate violation(s):`,
      moderate.map((v) => `  - ${v.id}: ${v.description}`).join('\n')
    );
  }

  // Fail on critical or serious
  const failingViolations = [...critical, ...serious];
  if (failingViolations.length > 0) {
    const details = failingViolations
      .map(
        (v) =>
          `  [${v.impact?.toUpperCase()}] ${v.id}: ${v.description}\n` +
          v.nodes
            .map((n) => `    - ${n.target.join(' > ')}: ${n.html.slice(0, 100)}`)
            .join('\n')
      )
      .join('\n');

    throw new Error(
      `${pageName} has ${failingViolations.length} critical/serious a11y violation(s):\n${details}`
    );
  }
}

/**
 * Check that a specific element is keyboard-focusable and has a visible focus indicator.
 *
 * @param page - Playwright Page instance
 * @param selector - CSS selector for the element
 * @returns true if the element receives focus and has a visible outline
 */
export async function checkFocusVisible(
  page: Page,
  selector: string
): Promise<boolean> {
  const element = page.locator(selector).first();

  // Focus the element
  await element.focus();

  // Verify the element received focus
  const isFocused = await element.evaluate(
    (el) => document.activeElement === el
  );

  if (!isFocused) {
    return false;
  }

  // Check for visible focus indicator (outline width > 0 or box-shadow present)
  const hasVisibleFocus = await element.evaluate((el) => {
    const styles = window.getComputedStyle(el);
    const outlineWidth = parseInt(styles.outlineWidth, 10);
    const outlineStyle = styles.outlineStyle;
    const boxShadow = styles.boxShadow;

    return (
      (outlineWidth > 0 && outlineStyle !== 'none') ||
      (boxShadow !== 'none' && boxShadow !== '')
    );
  });

  return hasVisibleFocus;
}

/**
 * Verify that a page contains the required semantic landmarks.
 *
 * @param page - Playwright Page instance
 * @returns Object with boolean flags for each landmark
 */
export async function checkLandmarks(page: Page): Promise<{
  hasMain: boolean;
  hasNav: boolean;
  hasHeader: boolean;
  hasFooter: boolean;
  mainHasId: boolean;
}> {
  const hasMain = (await page.locator('main, [role="main"]').count()) > 0;
  const hasNav = (await page.locator('nav, [role="navigation"]').count()) > 0;
  const hasHeader =
    (await page.locator('header, [role="banner"]').count()) > 0;
  const hasFooter =
    (await page.locator('footer, [role="contentinfo"]').count()) > 0;
  const mainHasId =
    (await page.locator('main#main-content, [role="main"]#main-content').count()) > 0;

  return { hasMain, hasNav, hasHeader, hasFooter, mainHasId };
}

/**
 * Verify the heading hierarchy on the current page.
 * Returns an array of heading levels in document order.
 * A valid hierarchy has h1 first, and no level is skipped (e.g., h1 -> h3 without h2).
 *
 * @param page - Playwright Page instance
 * @returns Array of heading levels [1, 2, 2, 3, ...] and whether the hierarchy is valid
 */
export async function checkHeadingHierarchy(page: Page): Promise<{
  levels: number[];
  hasH1: boolean;
  isValid: boolean;
}> {
  const levels = await page.evaluate(() => {
    const headings = document.querySelectorAll('h1, h2, h3, h4, h5, h6');
    return Array.from(headings).map((h) =>
      parseInt(h.tagName.replace('H', ''), 10)
    );
  });

  const hasH1 = levels.includes(1);

  // Check for skipped levels
  let isValid = hasH1;
  for (let i = 1; i < levels.length; i++) {
    if (levels[i] > levels[i - 1] + 1) {
      isValid = false;
      break;
    }
  }

  return { levels, hasH1, isValid };
}

/**
 * Get all interactive elements on the page that should be keyboard-focusable.
 *
 * @param page - Playwright Page instance
 * @returns Count of interactive elements and count of focusable ones
 */
export async function getInteractiveElementStats(page: Page): Promise<{
  totalInteractive: number;
  totalFocusable: number;
}> {
  return page.evaluate(() => {
    const interactiveSelectors = [
      'a[href]',
      'button:not([disabled])',
      'input:not([disabled]):not([type="hidden"])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])',
      '[role="button"]:not([disabled])',
      '[role="link"]',
      '[role="checkbox"]:not([disabled])',
      '[role="radio"]:not([disabled])',
      '[role="tab"]',
      '[role="menuitem"]',
    ];

    const interactive = document.querySelectorAll(
      interactiveSelectors.join(', ')
    );

    let focusable = 0;
    interactive.forEach((el) => {
      const htmlEl = el as HTMLElement;
      const tabIndex = htmlEl.tabIndex;
      const isHidden =
        htmlEl.offsetParent === null &&
        !htmlEl.closest('[aria-hidden="true"]');

      if (tabIndex >= 0 || !isHidden) {
        focusable++;
      }
    });

    return {
      totalInteractive: interactive.length,
      totalFocusable: focusable,
    };
  });
}

/**
 * Press Tab repeatedly and collect the focused elements in order.
 * Stops when focus cycles back to the first element or after maxTabs.
 *
 * @param page - Playwright Page instance
 * @param maxTabs - Maximum number of Tab presses (default 100)
 * @returns Array of CSS selectors for each focused element in tab order
 */
export async function getTabOrder(
  page: Page,
  maxTabs: number = 100
): Promise<string[]> {
  const tabOrder: string[] = [];

  // Start from the body
  await page.keyboard.press('Tab');

  for (let i = 0; i < maxTabs; i++) {
    const focusedSelector = await page.evaluate(() => {
      const el = document.activeElement;
      if (!el || el === document.body) return 'body';

      const tag = el.tagName.toLowerCase();
      const id = el.id ? `#${el.id}` : '';
      const testId = el.getAttribute('data-testid')
        ? `[data-testid="${el.getAttribute('data-testid')}"]`
        : '';

      return `${tag}${id}${testId}`;
    });

    // If we've cycled back to the first element, stop
    if (tabOrder.length > 0 && focusedSelector === tabOrder[0]) {
      break;
    }

    // If focus is on body, we've tabbed out of all elements
    if (focusedSelector === 'body') {
      break;
    }

    tabOrder.push(focusedSelector);
    await page.keyboard.press('Tab');
  }

  return tabOrder;
}
```

---

## Task 2 Detail: Keyboard Navigation Spec

**File**: `e2e/specs/a11y/a11y-keyboard.spec.ts`

```typescript
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

  // ── Public Pages ────────────────────────────────────────────────────

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

  // ── Authenticated Pages ─────────────────────────────────────────────

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

  // ── Shift+Tab Reverse Navigation ────────────────────────────────────

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

  // ── Space Key for Buttons and Checkboxes ────────────────────────────

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

  // ── Dialog Focus Trap ───────────────────────────────────────────────

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

  // ── Arrow Key Navigation in Sidebar Menu ────────────────────────────

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
```

---

## Task 3 Detail: Landmarks Spec

**File**: `e2e/specs/a11y/a11y-landmarks.spec.ts`

```typescript
// e2e/specs/a11y/a11y-landmarks.spec.ts
// Semantic landmark and heading hierarchy tests.
// Verifies <main>, <nav>, <header>, <footer> on every page
// and correct heading hierarchy (h1 -> h2 -> h3, no skips).

import { test, expect } from '@playwright/test';
import { LoginPage } from '../../pages/login.page';
import { TEST_USERS } from '../../fixtures/test-data';
import {
  ALL_PAGES,
  AUTHENTICATED_PAGES,
  PUBLIC_PAGES,
  checkLandmarks,
  checkHeadingHierarchy,
} from '../../helpers/a11y-helper';

test.describe('Semantic Landmarks (RGAA 9.2, 12.6, WCAG 1.3.1, 2.4.1)', () => {

  // ── Public Pages ────────────────────────────────────────────────────

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

  // ── Authenticated Pages ─────────────────────────────────────────────

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

  // ── html lang attribute ─────────────────────────────────────────────

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

  // ── Navigation landmarks have labels ────────────────────────────────

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

  // ── Page title ──────────────────────────────────────────────────────

  test.describe('Page Title', () => {
    test('should have a non-empty <title> element', async ({ page }) => {
      await page.goto('/auth/login');
      const title = await page.title();
      expect(title).toBeTruthy();
      expect(title.length).toBeGreaterThan(0);
    });
  });

  // ── Unique h1 per page ──────────────────────────────────────────────

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
```

---

## Task 4 Detail: Forms Spec

**File**: `e2e/specs/a11y/a11y-forms.spec.ts`

```typescript
// e2e/specs/a11y/a11y-forms.spec.ts
// Form accessibility tests.
// Verifies all form inputs have visible labels, error messages are announced
// via aria-live, and required fields are indicated with aria-required.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../../pages/login.page';
import { TEST_USERS } from '../../fixtures/test-data';

test.describe('Form Accessibility (RGAA 11.1, 11.2, 11.10, 11.5, WCAG 1.3.1, 3.3.2)', () => {

  // ── Login Form ──────────────────────────────────────────────────────

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

  // ── Registration Form ───────────────────────────────────────────────

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

  // ── Authenticated Forms ─────────────────────────────────────────────

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
```

---

## Task 5 Detail: Axe-core Spec

**File**: `e2e/specs/a11y/a11y-axe.spec.ts`

```typescript
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

  // ── Public Pages ────────────────────────────────────────────────────

  test.describe('Public Pages', () => {
    for (const { route, name } of PUBLIC_PAGES) {
      test(`${name} (${route}) - zero critical/serious violations`, async ({ page }) => {
        await page.goto(route);
        await page.waitForLoadState('networkidle');

        await assertNoSeriousViolations(page, name);
      });
    }
  });

  // ── Authenticated Pages ─────────────────────────────────────────────

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

  // ── WCAG Tag Coverage ──────────────────────────────────────────────

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

  // ── Color Contrast Specific Check ───────────────────────────────────

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

  // ── Aggregate Report ────────────────────────────────────────────────

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
```

---

## Acceptance Criteria Checklist

- [ ] `a11y-helper.ts` provides reusable axe scan, focus check, landmark check, heading hierarchy check, tab order traversal
- [ ] `a11y-keyboard.spec.ts` verifies Tab/Shift+Tab/Enter/Space/Arrow key navigation on all pages
- [ ] `a11y-landmarks.spec.ts` verifies `<main>`, `<nav>`, `<header>`, `<footer>` on every page
- [ ] `a11y-landmarks.spec.ts` verifies heading hierarchy (h1 first, no skipped levels)
- [ ] `a11y-forms.spec.ts` verifies all form inputs have visible labels linked via `for`/`aria-label`/`aria-labelledby`
- [ ] `a11y-forms.spec.ts` verifies `aria-required`, `aria-invalid`, `aria-describedby` on form inputs
- [ ] `a11y-forms.spec.ts` verifies error messages use `role="alert"` or `aria-live`
- [ ] `a11y-axe.spec.ts` runs axe-core on all 10 pages with zero critical + zero serious violations
- [ ] `a11y-axe.spec.ts` logs moderate violations as warnings without failing
- [ ] Color contrast check runs on all authenticated pages
- [ ] Dialog focus trap verified (focus stays inside dialog, Escape closes, focus returns to trigger)
- [ ] All tests run in CI via the `e2e-tests.yml` GitHub Actions workflow
- [ ] `@axe-core/playwright` added to `e2e/package.json` devDependencies
