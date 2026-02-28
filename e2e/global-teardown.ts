import { test as teardown } from '@playwright/test';
import { cleanAuthStates } from './helpers/auth-helper';

/**
 * Global teardown for Playwright E2E tests.
 *
 * Runs once after all test projects complete. Cleans up any
 * test artifacts created during the test run.
 *
 * Cleanup:
 * 1. Remove saved authentication storage states (.auth/ directory)
 * 2. Log completion message
 */

teardown('clean up test artifacts', async () => {
  console.log('[global-teardown] Cleaning up authentication storage states...');

  try {
    cleanAuthStates();
    console.log('[global-teardown] Authentication states cleaned.');
  } catch (error) {
    console.warn('[global-teardown] Failed to clean auth states:', error);
  }

  console.log('[global-teardown] Teardown complete.');
});
