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
