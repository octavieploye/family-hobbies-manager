import { type Browser, type BrowserContext, type Page } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { TEST_USERS } from '../fixtures/test-data';
import * as path from 'path';
import * as fs from 'fs';

/**
 * Authentication helper for Playwright E2E tests.
 *
 * Provides methods to:
 * - Authenticate and save browser storage state
 * - Create pre-authenticated browser contexts
 * - Manage multiple user sessions
 *
 * Storage state files are saved in e2e/.auth/ directory.
 */

const AUTH_DIR = path.join(__dirname, '..', '.auth');
const FAMILY_USER_STATE = path.join(AUTH_DIR, 'family-user.json');
const ADMIN_USER_STATE = path.join(AUTH_DIR, 'admin-user.json');
const ASSOCIATION_USER_STATE = path.join(AUTH_DIR, 'association-user.json');

/**
 * Ensure the .auth directory exists.
 */
function ensureAuthDir(): void {
  if (!fs.existsSync(AUTH_DIR)) {
    fs.mkdirSync(AUTH_DIR, { recursive: true });
  }
}

/**
 * Authenticate a user and save the storage state to a file.
 * This should be called in global setup, not in individual tests.
 */
export async function authenticateAndSave(
  browser: Browser,
  email: string,
  password: string,
  statePath: string
): Promise<void> {
  ensureAuthDir();

  const context = await browser.newContext();
  const page = await context.newPage();
  const loginPage = new LoginPage(page);

  await loginPage.goto();
  await loginPage.login(email, password);

  // Save the authenticated browser state
  await context.storageState({ path: statePath });
  await context.close();
}

/**
 * Authenticate the family user and save storage state.
 */
export async function authenticateFamilyUser(browser: Browser): Promise<void> {
  await authenticateAndSave(
    browser,
    TEST_USERS.familyUser.email,
    TEST_USERS.familyUser.password,
    FAMILY_USER_STATE
  );
}

/**
 * Authenticate the admin user and save storage state.
 */
export async function authenticateAdminUser(browser: Browser): Promise<void> {
  await authenticateAndSave(
    browser,
    TEST_USERS.admin.email,
    TEST_USERS.admin.password,
    ADMIN_USER_STATE
  );
}

/**
 * Authenticate the association admin user and save storage state.
 */
export async function authenticateAssociationUser(
  browser: Browser
): Promise<void> {
  await authenticateAndSave(
    browser,
    TEST_USERS.associationAdmin.email,
    TEST_USERS.associationAdmin.password,
    ASSOCIATION_USER_STATE
  );
}

/**
 * Create a new browser context pre-authenticated as the family user.
 */
export async function createFamilyUserContext(
  browser: Browser
): Promise<BrowserContext> {
  return browser.newContext({ storageState: FAMILY_USER_STATE });
}

/**
 * Create a new browser context pre-authenticated as admin.
 */
export async function createAdminContext(
  browser: Browser
): Promise<BrowserContext> {
  return browser.newContext({ storageState: ADMIN_USER_STATE });
}

/**
 * Create a new browser context pre-authenticated as association admin.
 */
export async function createAssociationContext(
  browser: Browser
): Promise<BrowserContext> {
  return browser.newContext({ storageState: ASSOCIATION_USER_STATE });
}

/**
 * Get the path to the family user storage state file.
 */
export function getFamilyUserStatePath(): string {
  return FAMILY_USER_STATE;
}

/**
 * Get the path to the admin storage state file.
 */
export function getAdminStatePath(): string {
  return ADMIN_USER_STATE;
}

/**
 * Get the path to the association user storage state file.
 */
export function getAssociationStatePath(): string {
  return ASSOCIATION_USER_STATE;
}

/**
 * Clean up all stored authentication states.
 */
export function cleanAuthStates(): void {
  if (fs.existsSync(AUTH_DIR)) {
    fs.rmSync(AUTH_DIR, { recursive: true, force: true });
  }
}
