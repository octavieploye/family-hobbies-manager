# Story S8-001: Implement Playwright E2E Test Suite

> 13 points | Priority: P0 | Service: e2e / frontend
> Sprint file: [Back to Sprint Index](./_index.md)
> Test specs: [See E2E Test Specs](./S8-001-playwright-e2e-suite-tests.md)

---

## Context

The Playwright E2E test suite is the final validation layer for the entire Family Hobbies Manager application. It exercises all user flows end-to-end across the full microservice stack: api-gateway, user-service, association-service, payment-service, notification-service, and the Angular frontend. Tests run against a complete Docker Compose environment (`docker-compose.e2e.yml`) with seeded PostgreSQL data and a running Kafka broker, ensuring that every feature delivered across Sprints 1--7 works as an integrated system.

The suite follows the **Page Object Model (POM)** pattern -- 11 page objects encapsulate all selectors and user actions, keeping spec files focused on behavior assertions rather than DOM manipulation. Each page object maps to an Angular route and exposes methods that mirror real user interactions (clicking, typing, navigating, waiting for API responses).

Eight spec files cover the critical user journeys: authentication (register, login, logout, token refresh), family management (CRUD members), association search (keyword, city, category filters with pagination), subscription management (subscribe, view, cancel), attendance tracking (calendar, mark present/absent, history), payment flow (checkout initiation, webhook mock, status verification), notifications (bell icon, dropdown, mark read), and RGPD compliance (consent toggle, data export, download verification).

The test environment is fully containerized via `docker-compose.e2e.yml`, which starts all services with health checks and seeds realistic French test data. A GitHub Actions workflow (`e2e-tests.yml`) orchestrates CI execution with Playwright browser installation, Docker Compose startup, test execution across Chromium/Firefox/WebKit, and automatic artifact upload of failure screenshots and HTML reports.

This is the largest story in the project at 13 points because it touches every service, every Angular route, and requires a complete integration environment.

## Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|----------------|---------------|
| 1 | Playwright configuration | `e2e/playwright.config.ts` | Production-grade Playwright config with browser matrix, reporters, timeouts, retries | `npx playwright test --list` shows all test files |
| 2 | Package.json + tsconfig | `e2e/package.json`, `e2e/tsconfig.json` | Node project setup with Playwright dependency | `npm install` succeeds, TypeScript compiles |
| 3 | LoginPage POM | `e2e/pages/login.page.ts` | Page object for `/auth/login` | Selectors resolve on login page |
| 4 | RegisterPage POM | `e2e/pages/register.page.ts` | Page object for `/auth/register` | Selectors resolve on register page |
| 5 | DashboardPage POM | `e2e/pages/dashboard.page.ts` | Page object for `/dashboard` | Selectors resolve on dashboard page |
| 6 | FamilyPage POM | `e2e/pages/family.page.ts` | Page object for `/families` and `/families/:id` | Selectors resolve on family pages |
| 7 | AssociationSearchPage POM | `e2e/pages/association-search.page.ts` | Page object for `/associations/search` | Selectors resolve on search page |
| 8 | AssociationDetailPage POM | `e2e/pages/association-detail.page.ts` | Page object for `/associations/:id` | Selectors resolve on detail page |
| 9 | SubscriptionPage POM | `e2e/pages/subscription.page.ts` | Page object for `/subscriptions` | Selectors resolve on subscription page |
| 10 | AttendancePage POM | `e2e/pages/attendance.page.ts` | Page object for `/attendance` | Selectors resolve on attendance page |
| 11 | PaymentPage POM | `e2e/pages/payment.page.ts` | Page object for `/payments` and `/payments/:id` | Selectors resolve on payment pages |
| 12 | NotificationPage POM | `e2e/pages/notification.page.ts` | Page object for `/notifications` (bell, dropdown, list) | Selectors resolve on notification page |
| 13 | SettingsPage POM | `e2e/pages/settings.page.ts` | Page object for `/settings` (RGPD consent, data export) | Selectors resolve on settings page |
| 14 | Test data fixtures | `e2e/fixtures/test-data.ts` | Realistic French test data (users, families, associations) | Imported successfully by spec files |
| 15 | API helper | `e2e/helpers/api-helper.ts` | Direct API calls for setup/teardown/verification | Helper functions callable from specs |
| 16 | Auth helper | `e2e/helpers/auth-helper.ts` | Login shortcut (storageState), token management | `authenticateAs()` returns valid session |
| 17 | Docker Compose E2E | `docker/docker-compose.e2e.yml` | Full stack with health checks, seeded data | `docker compose up` starts all services |
| 18 | GitHub Actions workflow | `.github/workflows/e2e-tests.yml` | CI pipeline: Docker up, Playwright run, artifact upload | Workflow runs on push/PR |
| 19 | Test spec files (8) | `e2e/specs/*.spec.ts` | All 8 E2E test scenarios | All tests pass against running stack |

> **Note**: Task 19 (all 8 spec files) is detailed in the companion file [S8-001-playwright-e2e-suite-tests.md](./S8-001-playwright-e2e-suite-tests.md).

---

## Task 1 Detail: Playwright Configuration

- **What**: Production-grade Playwright configuration file. Defines base URL pointing to the Angular dev server (proxied through api-gateway for API calls), a browser matrix covering Chromium, Firefox, and WebKit, an HTML reporter for CI artifacts, reasonable timeouts (30s navigation, 10s action, 5s assertion), 2 retries on CI, parallel execution with 4 workers, and automatic screenshot/video capture on failure.
- **Where**: `e2e/playwright.config.ts`
- **Why**: Central configuration consumed by all test specs. The browser matrix ensures cross-browser compatibility. The reporter generates HTML artifacts uploaded by CI. Retries reduce flakiness in containerized environments.
- **Content**:

```typescript
import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright configuration for Family Hobbies Manager E2E tests.
 *
 * Environment variables:
 * - BASE_URL: Frontend URL (default: http://localhost:4200)
 * - API_URL: API gateway URL (default: http://localhost:8080)
 * - CI: Set to 'true' in GitHub Actions
 *
 * Browser matrix: Chromium (desktop + mobile), Firefox, WebKit
 * Reporters: HTML (always), list (CI), dot (local)
 * Retries: 2 on CI, 0 locally
 * Workers: 4 on CI, 50% of CPUs locally
 */
export default defineConfig({
  testDir: './specs',
  testMatch: '**/*.spec.ts',

  /* Maximum time the entire test suite can run */
  globalTimeout: 30 * 60 * 1000, // 30 minutes

  /* Maximum time one test can run */
  timeout: 60 * 1000, // 60 seconds per test

  /* Expect timeout for assertions */
  expect: {
    timeout: 5 * 1000, // 5 seconds
  },

  /* Run tests in files in parallel */
  fullyParallel: true,

  /* Fail the build on CI if you accidentally left test.only in the source code */
  forbidOnly: !!process.env.CI,

  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,

  /* Limit parallel workers on CI to avoid resource exhaustion */
  workers: process.env.CI ? 4 : undefined,

  /* Reporter configuration */
  reporter: process.env.CI
    ? [
        ['list'],
        ['html', { open: 'never', outputFolder: 'playwright-report' }],
        ['junit', { outputFile: 'test-results/junit-results.xml' }],
      ]
    : [
        ['dot'],
        ['html', { open: 'on-failure', outputFolder: 'playwright-report' }],
      ],

  /* Shared settings for all projects */
  use: {
    /* Base URL for page.goto('/path') calls */
    baseURL: process.env.BASE_URL || 'http://localhost:4200',

    /* Collect trace on first retry */
    trace: 'on-first-retry',

    /* Screenshot on failure */
    screenshot: 'only-on-failure',

    /* Video on first retry */
    video: 'on-first-retry',

    /* Navigation timeout */
    navigationTimeout: 30 * 1000, // 30 seconds

    /* Action timeout (click, fill, etc.) */
    actionTimeout: 10 * 1000, // 10 seconds

    /* Extra HTTP headers for API gateway */
    extraHTTPHeaders: {
      'Accept-Language': 'fr-FR',
    },
  },

  /* Browser projects */
  projects: [
    /* ── Setup ──────────────────────────────────────────────── */
    {
      name: 'setup',
      testMatch: /global-setup\.ts/,
      teardown: 'teardown',
    },
    {
      name: 'teardown',
      testMatch: /global-teardown\.ts/,
    },

    /* ── Desktop Browsers ───────────────────────────────────── */
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        viewport: { width: 1280, height: 720 },
      },
      dependencies: ['setup'],
    },
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        viewport: { width: 1280, height: 720 },
      },
      dependencies: ['setup'],
    },
    {
      name: 'webkit',
      use: {
        ...devices['Desktop Safari'],
        viewport: { width: 1280, height: 720 },
      },
      dependencies: ['setup'],
    },

    /* ── Mobile Browser ─────────────────────────────────────── */
    {
      name: 'mobile-chrome',
      use: {
        ...devices['Pixel 5'],
      },
      dependencies: ['setup'],
    },
  ],

  /* Web server configuration -- start Angular dev server if not already running */
  webServer: process.env.CI
    ? undefined // In CI, Docker Compose handles the frontend
    : {
        command: 'npm start',
        cwd: '../frontend',
        url: 'http://localhost:4200',
        reuseExistingServer: true,
        timeout: 120 * 1000, // 2 minutes for Angular to start
      },

  /* Output directory for test artifacts */
  outputDir: 'test-results',
});
```

- **Verify**: `cd e2e && npx playwright test --list` -> lists all spec files without errors

---

## Task 2 Detail: Package.json + tsconfig

- **What**: Node.js project configuration for the `e2e/` directory. The `package.json` declares `@playwright/test` as the primary dependency along with `dotenv` for environment variable loading. The `tsconfig.json` extends strict TypeScript settings suitable for Playwright.
- **Where**: `e2e/package.json`, `e2e/tsconfig.json`
- **Why**: Isolates E2E dependencies from the Angular frontend. CI installs only `e2e/` dependencies without pulling in the full Angular build chain.
- **Content** (package.json):

```json
{
  "name": "family-hobbies-manager-e2e",
  "version": "1.0.0",
  "private": true,
  "description": "Playwright E2E tests for Family Hobbies Manager",
  "scripts": {
    "test": "npx playwright test",
    "test:chromium": "npx playwright test --project=chromium",
    "test:firefox": "npx playwright test --project=firefox",
    "test:webkit": "npx playwright test --project=webkit",
    "test:mobile": "npx playwright test --project=mobile-chrome",
    "test:headed": "npx playwright test --headed",
    "test:debug": "npx playwright test --debug",
    "test:ui": "npx playwright test --ui",
    "report": "npx playwright show-report",
    "install:browsers": "npx playwright install --with-deps"
  },
  "devDependencies": {
    "@playwright/test": "^1.42.0",
    "dotenv": "^16.4.0"
  }
}
```

- **Content** (tsconfig.json):

```json
{
  "compilerOptions": {
    "target": "ES2022",
    "module": "ESNext",
    "moduleResolution": "bundler",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "forceConsistentCasingInFileNames": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "outDir": "./dist",
    "rootDir": ".",
    "baseUrl": ".",
    "paths": {
      "@pages/*": ["./pages/*"],
      "@fixtures/*": ["./fixtures/*"],
      "@helpers/*": ["./helpers/*"]
    }
  },
  "include": [
    "**/*.ts"
  ],
  "exclude": [
    "node_modules",
    "dist",
    "test-results",
    "playwright-report"
  ]
}
```

- **Verify**: `cd e2e && npm install` -> succeeds; `npx tsc --noEmit` -> no TypeScript errors

---

## Task 3 Detail: LoginPage POM

- **What**: Page Object Model for the `/auth/login` route. Encapsulates the login form selectors (email, password, submit button, error message) and provides action methods (`fillEmail`, `fillPassword`, `submit`, `login`, `getErrorMessage`). Uses `data-testid` attributes as the primary selector strategy for resilience against CSS changes.
- **Where**: `e2e/pages/login.page.ts`
- **Why**: Consumed by `auth.spec.ts` and `auth-helper.ts`. Centralizes login page interaction so that selector changes only require updating one file.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the login page (/auth/login).
 *
 * Selectors use data-testid attributes for resilience.
 * All actions return the page object for fluent chaining.
 *
 * Used by: auth.spec.ts, auth-helper.ts
 */
export class LoginPage {
  readonly page: Page;
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly submitButton: Locator;
  readonly errorMessage: Locator;
  readonly registerLink: Locator;
  readonly forgotPasswordLink: Locator;
  readonly rememberMeCheckbox: Locator;
  readonly pageTitle: Locator;
  readonly loadingSpinner: Locator;

  constructor(page: Page) {
    this.page = page;
    this.emailInput = page.getByTestId('login-email');
    this.passwordInput = page.getByTestId('login-password');
    this.submitButton = page.getByTestId('login-submit');
    this.errorMessage = page.getByTestId('login-error');
    this.registerLink = page.getByTestId('login-register-link');
    this.forgotPasswordLink = page.getByTestId('login-forgot-password');
    this.rememberMeCheckbox = page.getByTestId('login-remember-me');
    this.pageTitle = page.getByRole('heading', { name: /connexion/i });
    this.loadingSpinner = page.getByTestId('login-loading');
  }

  /**
   * Navigate to the login page.
   */
  async goto(): Promise<void> {
    await this.page.goto('/auth/login');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Fill the email input field.
   */
  async fillEmail(email: string): Promise<LoginPage> {
    await this.emailInput.fill(email);
    return this;
  }

  /**
   * Fill the password input field.
   */
  async fillPassword(password: string): Promise<LoginPage> {
    await this.passwordInput.fill(password);
    return this;
  }

  /**
   * Click the submit button and wait for navigation or error.
   */
  async submit(): Promise<void> {
    await this.submitButton.click();
  }

  /**
   * Perform a complete login flow: fill email, fill password, submit.
   * Waits for navigation to dashboard after successful login.
   */
  async login(email: string, password: string): Promise<void> {
    await this.fillEmail(email);
    await this.fillPassword(password);
    await this.submitButton.click();
    await this.page.waitForURL('**/dashboard', { timeout: 15000 });
  }

  /**
   * Get the text content of the error message element.
   * Returns null if no error is visible.
   */
  async getErrorMessage(): Promise<string | null> {
    if (await this.errorMessage.isVisible()) {
      return this.errorMessage.textContent();
    }
    return null;
  }

  /**
   * Click the link to navigate to the registration page.
   */
  async goToRegister(): Promise<void> {
    await this.registerLink.click();
    await this.page.waitForURL('**/auth/register');
  }

  /**
   * Assert that the login page is fully loaded.
   */
  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
    await expect(this.emailInput).toBeVisible();
    await expect(this.passwordInput).toBeVisible();
    await expect(this.submitButton).toBeVisible();
  }

  /**
   * Assert that the submit button is disabled (e.g., form validation).
   */
  async expectSubmitDisabled(): Promise<void> {
    await expect(this.submitButton).toBeDisabled();
  }

  /**
   * Assert that the submit button is enabled.
   */
  async expectSubmitEnabled(): Promise<void> {
    await expect(this.submitButton).toBeEnabled();
  }
}
```

- **Verify**: TypeScript compiles without errors; selectors match the Angular login component template

---

## Task 4 Detail: RegisterPage POM

- **What**: Page Object Model for the `/auth/register` route. Encapsulates all registration form fields (first name, last name, email, password, confirm password), role selection, terms checkbox, and submit button.
- **Where**: `e2e/pages/register.page.ts`
- **Why**: Consumed by `auth.spec.ts` for the "register new user" scenario. Isolates registration form selectors.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the registration page (/auth/register).
 *
 * Used by: auth.spec.ts
 */
export class RegisterPage {
  readonly page: Page;
  readonly firstNameInput: Locator;
  readonly lastNameInput: Locator;
  readonly emailInput: Locator;
  readonly passwordInput: Locator;
  readonly confirmPasswordInput: Locator;
  readonly roleSelect: Locator;
  readonly termsCheckbox: Locator;
  readonly submitButton: Locator;
  readonly errorMessage: Locator;
  readonly successMessage: Locator;
  readonly loginLink: Locator;
  readonly pageTitle: Locator;

  constructor(page: Page) {
    this.page = page;
    this.firstNameInput = page.getByTestId('register-firstname');
    this.lastNameInput = page.getByTestId('register-lastname');
    this.emailInput = page.getByTestId('register-email');
    this.passwordInput = page.getByTestId('register-password');
    this.confirmPasswordInput = page.getByTestId('register-confirm-password');
    this.roleSelect = page.getByTestId('register-role');
    this.termsCheckbox = page.getByTestId('register-terms');
    this.submitButton = page.getByTestId('register-submit');
    this.errorMessage = page.getByTestId('register-error');
    this.successMessage = page.getByTestId('register-success');
    this.loginLink = page.getByTestId('register-login-link');
    this.pageTitle = page.getByRole('heading', { name: /inscription/i });
  }

  async goto(): Promise<void> {
    await this.page.goto('/auth/register');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Fill all registration fields and submit.
   */
  async register(data: {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
  }): Promise<void> {
    await this.firstNameInput.fill(data.firstName);
    await this.lastNameInput.fill(data.lastName);
    await this.emailInput.fill(data.email);
    await this.passwordInput.fill(data.password);
    await this.confirmPasswordInput.fill(data.password);
    await this.termsCheckbox.check();
    await this.submitButton.click();
  }

  /**
   * Select a role from the role dropdown.
   */
  async selectRole(role: 'FAMILY' | 'ASSOCIATION'): Promise<void> {
    await this.roleSelect.click();
    await this.page.getByRole('option', {
      name: role === 'FAMILY' ? 'Famille' : 'Association',
    }).click();
  }

  async getErrorMessage(): Promise<string | null> {
    if (await this.errorMessage.isVisible()) {
      return this.errorMessage.textContent();
    }
    return null;
  }

  async getSuccessMessage(): Promise<string | null> {
    if (await this.successMessage.isVisible()) {
      return this.successMessage.textContent();
    }
    return null;
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
    await expect(this.firstNameInput).toBeVisible();
    await expect(this.submitButton).toBeVisible();
  }
}
```

- **Verify**: TypeScript compiles without errors

---

## Task 5 Detail: DashboardPage POM

- **What**: Page Object Model for `/dashboard`. Exposes the family overview card, recent activity list, quick action buttons, and summary statistics.
- **Where**: `e2e/pages/dashboard.page.ts`
- **Why**: Consumed by `auth.spec.ts` (post-login landing) and multiple other specs that verify navigation state.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the dashboard page (/dashboard).
 *
 * Used by: auth.spec.ts, family.spec.ts, subscription.spec.ts
 */
export class DashboardPage {
  readonly page: Page;
  readonly welcomeMessage: Locator;
  readonly familyCard: Locator;
  readonly recentActivityList: Locator;
  readonly quickActionSubscribe: Locator;
  readonly quickActionSearch: Locator;
  readonly quickActionAttendance: Locator;
  readonly memberCount: Locator;
  readonly subscriptionCount: Locator;
  readonly upcomingEventsCount: Locator;
  readonly notificationBell: Locator;
  readonly userMenu: Locator;
  readonly logoutButton: Locator;
  readonly navLinks: Locator;

  constructor(page: Page) {
    this.page = page;
    this.welcomeMessage = page.getByTestId('dashboard-welcome');
    this.familyCard = page.getByTestId('dashboard-family-card');
    this.recentActivityList = page.getByTestId('dashboard-recent-activity');
    this.quickActionSubscribe = page.getByTestId('dashboard-action-subscribe');
    this.quickActionSearch = page.getByTestId('dashboard-action-search');
    this.quickActionAttendance = page.getByTestId('dashboard-action-attendance');
    this.memberCount = page.getByTestId('dashboard-member-count');
    this.subscriptionCount = page.getByTestId('dashboard-subscription-count');
    this.upcomingEventsCount = page.getByTestId('dashboard-upcoming-events');
    this.notificationBell = page.getByTestId('notification-bell');
    this.userMenu = page.getByTestId('user-menu');
    this.logoutButton = page.getByTestId('user-menu-logout');
    this.navLinks = page.locator('nav a');
  }

  async goto(): Promise<void> {
    await this.page.goto('/dashboard');
    await this.welcomeMessage.waitFor({ state: 'visible' });
  }

  /**
   * Open the user menu dropdown in the top-right corner.
   */
  async openUserMenu(): Promise<void> {
    await this.userMenu.click();
  }

  /**
   * Click logout from the user menu.
   */
  async logout(): Promise<void> {
    await this.openUserMenu();
    await this.logoutButton.click();
    await this.page.waitForURL('**/auth/login');
  }

  /**
   * Navigate to a specific section via quick action buttons.
   */
  async navigateToSearch(): Promise<void> {
    await this.quickActionSearch.click();
    await this.page.waitForURL('**/associations/search');
  }

  async navigateToAttendance(): Promise<void> {
    await this.quickActionAttendance.click();
    await this.page.waitForURL('**/attendance');
  }

  /**
   * Get the displayed welcome message text.
   */
  async getWelcomeText(): Promise<string | null> {
    return this.welcomeMessage.textContent();
  }

  /**
   * Get the number of family members displayed on the dashboard.
   */
  async getMemberCount(): Promise<string | null> {
    return this.memberCount.textContent();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.welcomeMessage).toBeVisible();
    await expect(this.familyCard).toBeVisible();
  }

  /**
   * Assert that the user is on the dashboard (post-login validation).
   */
  async expectLoggedIn(): Promise<void> {
    await expect(this.page).toHaveURL(/.*\/dashboard/);
    await expect(this.welcomeMessage).toBeVisible();
    await expect(this.userMenu).toBeVisible();
  }
}
```

- **Verify**: TypeScript compiles without errors

---

## Task 6 Detail: FamilyPage POM

- **What**: Page Object Model for `/families` (list) and `/families/:id` (detail/edit). Encapsulates member list, add member dialog, edit member form, remove member confirmation.
- **Where**: `e2e/pages/family.page.ts`
- **Why**: Consumed by `family.spec.ts` for all family CRUD operations.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for family pages (/families, /families/:id).
 *
 * Used by: family.spec.ts
 */
export class FamilyPage {
  readonly page: Page;
  readonly pageTitle: Locator;
  readonly familyNameInput: Locator;
  readonly createFamilyButton: Locator;
  readonly memberList: Locator;
  readonly addMemberButton: Locator;
  readonly memberCards: Locator;
  readonly saveFamilyButton: Locator;
  readonly successMessage: Locator;
  readonly errorMessage: Locator;

  /* Add Member Dialog */
  readonly memberFirstNameInput: Locator;
  readonly memberLastNameInput: Locator;
  readonly memberBirthDateInput: Locator;
  readonly memberRoleSelect: Locator;
  readonly saveMemberButton: Locator;
  readonly cancelMemberButton: Locator;

  /* Edit Member Dialog */
  readonly editMemberDialog: Locator;
  readonly editMemberFirstName: Locator;
  readonly editMemberLastName: Locator;
  readonly updateMemberButton: Locator;

  /* Delete Confirmation */
  readonly confirmDeleteButton: Locator;
  readonly cancelDeleteButton: Locator;
  readonly deleteConfirmDialog: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: /famille/i });
    this.familyNameInput = page.getByTestId('family-name');
    this.createFamilyButton = page.getByTestId('create-family-button');
    this.memberList = page.getByTestId('member-list');
    this.addMemberButton = page.getByTestId('add-member-button');
    this.memberCards = page.getByTestId('member-card');
    this.saveFamilyButton = page.getByTestId('save-family-button');
    this.successMessage = page.getByTestId('family-success');
    this.errorMessage = page.getByTestId('family-error');

    /* Add Member Dialog */
    this.memberFirstNameInput = page.getByTestId('member-firstname');
    this.memberLastNameInput = page.getByTestId('member-lastname');
    this.memberBirthDateInput = page.getByTestId('member-birthdate');
    this.memberRoleSelect = page.getByTestId('member-role');
    this.saveMemberButton = page.getByTestId('save-member-button');
    this.cancelMemberButton = page.getByTestId('cancel-member-button');

    /* Edit Member Dialog */
    this.editMemberDialog = page.getByTestId('edit-member-dialog');
    this.editMemberFirstName = page.getByTestId('edit-member-firstname');
    this.editMemberLastName = page.getByTestId('edit-member-lastname');
    this.updateMemberButton = page.getByTestId('update-member-button');

    /* Delete Confirmation */
    this.confirmDeleteButton = page.getByTestId('confirm-delete-button');
    this.cancelDeleteButton = page.getByTestId('cancel-delete-button');
    this.deleteConfirmDialog = page.getByTestId('delete-confirm-dialog');
  }

  async goto(): Promise<void> {
    await this.page.goto('/families');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  async gotoFamily(familyId: string): Promise<void> {
    await this.page.goto(`/families/${familyId}`);
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Create a new family with the given name.
   */
  async createFamily(name: string): Promise<void> {
    await this.familyNameInput.fill(name);
    await this.createFamilyButton.click();
    await this.successMessage.waitFor({ state: 'visible' });
  }

  /**
   * Open the "Add Member" dialog and fill in member details.
   */
  async addMember(data: {
    firstName: string;
    lastName: string;
    birthDate: string;
    role?: string;
  }): Promise<void> {
    await this.addMemberButton.click();
    await this.memberFirstNameInput.fill(data.firstName);
    await this.memberLastNameInput.fill(data.lastName);
    await this.memberBirthDateInput.fill(data.birthDate);
    if (data.role) {
      await this.memberRoleSelect.click();
      await this.page.getByRole('option', { name: data.role }).click();
    }
    await this.saveMemberButton.click();
    await this.page.waitForTimeout(500); // Wait for list refresh
  }

  /**
   * Click the edit button on a specific member card (by index).
   */
  async editMember(
    index: number,
    data: { firstName?: string; lastName?: string }
  ): Promise<void> {
    const card = this.memberCards.nth(index);
    await card.getByTestId('edit-member-button').click();
    await this.editMemberDialog.waitFor({ state: 'visible' });

    if (data.firstName) {
      await this.editMemberFirstName.clear();
      await this.editMemberFirstName.fill(data.firstName);
    }
    if (data.lastName) {
      await this.editMemberLastName.clear();
      await this.editMemberLastName.fill(data.lastName);
    }

    await this.updateMemberButton.click();
    await this.editMemberDialog.waitFor({ state: 'hidden' });
  }

  /**
   * Click the remove button on a specific member card and confirm.
   */
  async removeMember(index: number): Promise<void> {
    const card = this.memberCards.nth(index);
    await card.getByTestId('remove-member-button').click();
    await this.deleteConfirmDialog.waitFor({ state: 'visible' });
    await this.confirmDeleteButton.click();
    await this.deleteConfirmDialog.waitFor({ state: 'hidden' });
  }

  /**
   * Get the number of member cards currently displayed.
   */
  async getMemberCount(): Promise<number> {
    return this.memberCards.count();
  }

  /**
   * Get the name displayed on a specific member card (by index).
   */
  async getMemberName(index: number): Promise<string | null> {
    const card = this.memberCards.nth(index);
    return card.getByTestId('member-name').textContent();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
  }
}
```

- **Verify**: TypeScript compiles without errors

---

## Task 7 Detail: AssociationSearchPage POM

- **What**: Page Object Model for `/associations/search`. Encapsulates the search input, city filter, category filter, search results list, pagination controls, and empty state indicator.
- **Where**: `e2e/pages/association-search.page.ts`
- **Why**: Consumed by `association-search.spec.ts` for search, filter, and pagination scenarios.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the association search page (/associations/search).
 *
 * Used by: association-search.spec.ts
 */
export class AssociationSearchPage {
  readonly page: Page;
  readonly pageTitle: Locator;
  readonly keywordInput: Locator;
  readonly cityInput: Locator;
  readonly categorySelect: Locator;
  readonly searchButton: Locator;
  readonly clearFiltersButton: Locator;
  readonly resultList: Locator;
  readonly resultCards: Locator;
  readonly resultCount: Locator;
  readonly emptyState: Locator;
  readonly loadingIndicator: Locator;
  readonly paginationNext: Locator;
  readonly paginationPrev: Locator;
  readonly paginationPages: Locator;
  readonly paginationInfo: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', {
      name: /rechercher.*association/i,
    });
    this.keywordInput = page.getByTestId('search-keyword');
    this.cityInput = page.getByTestId('search-city');
    this.categorySelect = page.getByTestId('search-category');
    this.searchButton = page.getByTestId('search-submit');
    this.clearFiltersButton = page.getByTestId('search-clear-filters');
    this.resultList = page.getByTestId('search-results');
    this.resultCards = page.getByTestId('association-card');
    this.resultCount = page.getByTestId('search-result-count');
    this.emptyState = page.getByTestId('search-empty-state');
    this.loadingIndicator = page.getByTestId('search-loading');
    this.paginationNext = page.getByTestId('pagination-next');
    this.paginationPrev = page.getByTestId('pagination-prev');
    this.paginationPages = page.getByTestId('pagination-page');
    this.paginationInfo = page.getByTestId('pagination-info');
  }

  async goto(): Promise<void> {
    await this.page.goto('/associations/search');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Perform a keyword search.
   */
  async searchByKeyword(keyword: string): Promise<void> {
    await this.keywordInput.fill(keyword);
    await this.searchButton.click();
    await this.loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 });
  }

  /**
   * Filter results by city name.
   */
  async filterByCity(city: string): Promise<void> {
    await this.cityInput.fill(city);
    await this.searchButton.click();
    await this.loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 });
  }

  /**
   * Select a category from the dropdown.
   */
  async filterByCategory(category: string): Promise<void> {
    await this.categorySelect.click();
    await this.page.getByRole('option', { name: category }).click();
    await this.searchButton.click();
    await this.loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 });
  }

  /**
   * Navigate to the next page of results.
   */
  async goToNextPage(): Promise<void> {
    await this.paginationNext.click();
    await this.loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 });
  }

  /**
   * Navigate to the previous page of results.
   */
  async goToPreviousPage(): Promise<void> {
    await this.paginationPrev.click();
    await this.loadingIndicator.waitFor({ state: 'hidden', timeout: 10000 });
  }

  /**
   * Click on an association card to navigate to its detail page.
   */
  async clickResult(index: number): Promise<void> {
    await this.resultCards.nth(index).click();
    await this.page.waitForURL('**/associations/*');
  }

  /**
   * Clear all filters and reset the search.
   */
  async clearFilters(): Promise<void> {
    await this.clearFiltersButton.click();
  }

  /**
   * Get the number of association cards currently displayed.
   */
  async getResultCount(): Promise<number> {
    return this.resultCards.count();
  }

  /**
   * Get the displayed total result count text (e.g., "142 associations trouvees").
   */
  async getResultCountText(): Promise<string | null> {
    return this.resultCount.textContent();
  }

  async expectResultsVisible(): Promise<void> {
    await expect(this.resultList).toBeVisible();
    const count = await this.resultCards.count();
    expect(count).toBeGreaterThan(0);
  }

  async expectEmptyState(): Promise<void> {
    await expect(this.emptyState).toBeVisible();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
    await expect(this.keywordInput).toBeVisible();
    await expect(this.searchButton).toBeVisible();
  }
}
```

- **Verify**: TypeScript compiles without errors

---

## Task 8 Detail: AssociationDetailPage POM

- **What**: Page Object Model for `/associations/:id`. Displays association name, description, location, activities, and a "Subscribe" call-to-action button.
- **Where**: `e2e/pages/association-detail.page.ts`
- **Why**: Consumed by `association-search.spec.ts` and `subscription.spec.ts` to verify detail navigation and trigger subscription flows.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the association detail page (/associations/:id).
 *
 * Used by: association-search.spec.ts, subscription.spec.ts
 */
export class AssociationDetailPage {
  readonly page: Page;
  readonly associationName: Locator;
  readonly description: Locator;
  readonly cityLabel: Locator;
  readonly categoryBadge: Locator;
  readonly activityList: Locator;
  readonly activityCards: Locator;
  readonly subscribeButton: Locator;
  readonly backToSearchLink: Locator;
  readonly contactInfo: Locator;
  readonly sessionSchedule: Locator;

  constructor(page: Page) {
    this.page = page;
    this.associationName = page.getByTestId('association-name');
    this.description = page.getByTestId('association-description');
    this.cityLabel = page.getByTestId('association-city');
    this.categoryBadge = page.getByTestId('association-category');
    this.activityList = page.getByTestId('activity-list');
    this.activityCards = page.getByTestId('activity-card');
    this.subscribeButton = page.getByTestId('subscribe-button');
    this.backToSearchLink = page.getByTestId('back-to-search');
    this.contactInfo = page.getByTestId('association-contact');
    this.sessionSchedule = page.getByTestId('session-schedule');
  }

  async goto(id: string): Promise<void> {
    await this.page.goto(`/associations/${id}`);
    await this.associationName.waitFor({ state: 'visible' });
  }

  /**
   * Click the subscribe button for a specific activity.
   */
  async subscribeToActivity(activityIndex: number): Promise<void> {
    const card = this.activityCards.nth(activityIndex);
    await card.getByTestId('activity-subscribe-button').click();
  }

  /**
   * Get the association name text.
   */
  async getName(): Promise<string | null> {
    return this.associationName.textContent();
  }

  /**
   * Get the city text.
   */
  async getCity(): Promise<string | null> {
    return this.cityLabel.textContent();
  }

  /**
   * Get the category badge text.
   */
  async getCategory(): Promise<string | null> {
    return this.categoryBadge.textContent();
  }

  /**
   * Get the number of activities listed.
   */
  async getActivityCount(): Promise<number> {
    return this.activityCards.count();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.associationName).toBeVisible();
    await expect(this.description).toBeVisible();
  }
}
```

- **Verify**: TypeScript compiles without errors

---

## Task 9 Detail: SubscriptionPage POM

- **What**: Page Object Model for `/subscriptions`. Displays a list of active subscriptions with member name, association, activity, status, and cancel action.
- **Where**: `e2e/pages/subscription.page.ts`
- **Why**: Consumed by `subscription.spec.ts` for view, subscribe, and cancel scenarios.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the subscription page (/subscriptions).
 *
 * Used by: subscription.spec.ts
 */
export class SubscriptionPage {
  readonly page: Page;
  readonly pageTitle: Locator;
  readonly subscriptionList: Locator;
  readonly subscriptionRows: Locator;
  readonly emptyState: Locator;
  readonly filterByMember: Locator;
  readonly filterByStatus: Locator;
  readonly cancelDialog: Locator;
  readonly confirmCancelButton: Locator;
  readonly cancelCancelButton: Locator;
  readonly successMessage: Locator;

  /* Subscription Form (inline or dialog) */
  readonly memberSelect: Locator;
  readonly activitySelect: Locator;
  readonly submitSubscriptionButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: /abonnements/i });
    this.subscriptionList = page.getByTestId('subscription-list');
    this.subscriptionRows = page.getByTestId('subscription-row');
    this.emptyState = page.getByTestId('subscription-empty');
    this.filterByMember = page.getByTestId('filter-member');
    this.filterByStatus = page.getByTestId('filter-status');
    this.cancelDialog = page.getByTestId('cancel-subscription-dialog');
    this.confirmCancelButton = page.getByTestId('confirm-cancel-subscription');
    this.cancelCancelButton = page.getByTestId('dismiss-cancel-dialog');
    this.successMessage = page.getByTestId('subscription-success');

    /* Subscription Form */
    this.memberSelect = page.getByTestId('subscription-member-select');
    this.activitySelect = page.getByTestId('subscription-activity-select');
    this.submitSubscriptionButton = page.getByTestId('submit-subscription');
  }

  async goto(): Promise<void> {
    await this.page.goto('/subscriptions');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Subscribe a family member to an activity.
   */
  async subscribeMember(memberName: string, activityName: string): Promise<void> {
    await this.memberSelect.click();
    await this.page.getByRole('option', { name: memberName }).click();
    await this.activitySelect.click();
    await this.page.getByRole('option', { name: activityName }).click();
    await this.submitSubscriptionButton.click();
    await this.successMessage.waitFor({ state: 'visible' });
  }

  /**
   * Cancel a subscription by row index.
   */
  async cancelSubscription(index: number): Promise<void> {
    const row = this.subscriptionRows.nth(index);
    await row.getByTestId('cancel-subscription-button').click();
    await this.cancelDialog.waitFor({ state: 'visible' });
    await this.confirmCancelButton.click();
    await this.cancelDialog.waitFor({ state: 'hidden' });
  }

  /**
   * Get the number of subscription rows.
   */
  async getSubscriptionCount(): Promise<number> {
    return this.subscriptionRows.count();
  }

  /**
   * Get the displayed association name for a given subscription row.
   */
  async getAssociationName(index: number): Promise<string | null> {
    const row = this.subscriptionRows.nth(index);
    return row.getByTestId('subscription-association').textContent();
  }

  /**
   * Get the displayed status for a given subscription row.
   */
  async getStatus(index: number): Promise<string | null> {
    const row = this.subscriptionRows.nth(index);
    return row.getByTestId('subscription-status').textContent();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
  }

  async expectHasSubscriptions(): Promise<void> {
    const count = await this.subscriptionRows.count();
    expect(count).toBeGreaterThan(0);
  }
}
```

- **Verify**: TypeScript compiles without errors

---

## Task 10 Detail: AttendancePage POM

- **What**: Page Object Model for `/attendance`. Exposes the calendar view, date selection, session list, mark present/absent buttons, and attendance history table.
- **Where**: `e2e/pages/attendance.page.ts`
- **Why**: Consumed by `attendance.spec.ts` for calendar interaction, marking attendance, and history verification.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the attendance page (/attendance).
 *
 * Used by: attendance.spec.ts
 */
export class AttendancePage {
  readonly page: Page;
  readonly pageTitle: Locator;
  readonly calendarView: Locator;
  readonly calendarDays: Locator;
  readonly selectedDate: Locator;
  readonly sessionList: Locator;
  readonly sessionCards: Locator;
  readonly historyTab: Locator;
  readonly historyTable: Locator;
  readonly historyRows: Locator;
  readonly memberFilter: Locator;
  readonly activityFilter: Locator;
  readonly monthNavigateNext: Locator;
  readonly monthNavigatePrev: Locator;
  readonly currentMonthLabel: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: /presence/i });
    this.calendarView = page.getByTestId('attendance-calendar');
    this.calendarDays = page.getByTestId('calendar-day');
    this.selectedDate = page.getByTestId('selected-date');
    this.sessionList = page.getByTestId('session-list');
    this.sessionCards = page.getByTestId('session-card');
    this.historyTab = page.getByTestId('attendance-history-tab');
    this.historyTable = page.getByTestId('attendance-history-table');
    this.historyRows = page.getByTestId('attendance-history-row');
    this.memberFilter = page.getByTestId('attendance-member-filter');
    this.activityFilter = page.getByTestId('attendance-activity-filter');
    this.monthNavigateNext = page.getByTestId('calendar-next-month');
    this.monthNavigatePrev = page.getByTestId('calendar-prev-month');
    this.currentMonthLabel = page.getByTestId('calendar-current-month');
  }

  async goto(): Promise<void> {
    await this.page.goto('/attendance');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Click on a specific day in the calendar.
   */
  async selectDay(dayNumber: number): Promise<void> {
    await this.calendarDays
      .filter({ hasText: String(dayNumber) })
      .first()
      .click();
  }

  /**
   * Mark a member as present for a session.
   */
  async markPresent(sessionIndex: number): Promise<void> {
    const card = this.sessionCards.nth(sessionIndex);
    await card.getByTestId('mark-present-button').click();
    await this.page.waitForTimeout(300);
  }

  /**
   * Mark a member as absent for a session.
   */
  async markAbsent(sessionIndex: number): Promise<void> {
    const card = this.sessionCards.nth(sessionIndex);
    await card.getByTestId('mark-absent-button').click();
    await this.page.waitForTimeout(300);
  }

  /**
   * Get the attendance status icon/text for a session card.
   */
  async getAttendanceStatus(sessionIndex: number): Promise<string | null> {
    const card = this.sessionCards.nth(sessionIndex);
    return card.getByTestId('attendance-status').textContent();
  }

  /**
   * Switch to the history tab.
   */
  async viewHistory(): Promise<void> {
    await this.historyTab.click();
    await this.historyTable.waitFor({ state: 'visible' });
  }

  /**
   * Get the number of rows in the attendance history table.
   */
  async getHistoryRowCount(): Promise<number> {
    return this.historyRows.count();
  }

  /**
   * Navigate to the next month in the calendar.
   */
  async goToNextMonth(): Promise<void> {
    await this.monthNavigateNext.click();
  }

  /**
   * Navigate to the previous month in the calendar.
   */
  async goToPreviousMonth(): Promise<void> {
    await this.monthNavigatePrev.click();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
    await expect(this.calendarView).toBeVisible();
  }

  async expectSessionsVisible(): Promise<void> {
    const count = await this.sessionCards.count();
    expect(count).toBeGreaterThan(0);
  }
}
```

- **Verify**: TypeScript compiles without errors

---

## Task 11 Detail: PaymentPage POM

- **What**: Page Object Model for `/payments` (list) and `/payments/:id` (detail). Displays payment history, status badges, checkout initiation, and individual payment detail with timeline.
- **Where**: `e2e/pages/payment.page.ts`
- **Why**: Consumed by `payment.spec.ts` for checkout, status verification, and webhook-based flow testing.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for payment pages (/payments, /payments/:id).
 *
 * Used by: payment.spec.ts
 */
export class PaymentPage {
  readonly page: Page;
  readonly pageTitle: Locator;
  readonly paymentList: Locator;
  readonly paymentRows: Locator;
  readonly emptyState: Locator;
  readonly filterByStatus: Locator;
  readonly filterByDate: Locator;

  /* Payment Detail */
  readonly paymentAmount: Locator;
  readonly paymentStatus: Locator;
  readonly paymentDate: Locator;
  readonly paymentMethod: Locator;
  readonly paymentAssociation: Locator;
  readonly paymentTimeline: Locator;
  readonly timelineSteps: Locator;
  readonly downloadInvoiceButton: Locator;
  readonly backToListLink: Locator;

  /* Checkout */
  readonly checkoutButton: Locator;
  readonly checkoutLoadingIndicator: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: /paiements/i });
    this.paymentList = page.getByTestId('payment-list');
    this.paymentRows = page.getByTestId('payment-row');
    this.emptyState = page.getByTestId('payment-empty');
    this.filterByStatus = page.getByTestId('payment-filter-status');
    this.filterByDate = page.getByTestId('payment-filter-date');

    /* Payment Detail */
    this.paymentAmount = page.getByTestId('payment-amount');
    this.paymentStatus = page.getByTestId('payment-status');
    this.paymentDate = page.getByTestId('payment-date');
    this.paymentMethod = page.getByTestId('payment-method');
    this.paymentAssociation = page.getByTestId('payment-association');
    this.paymentTimeline = page.getByTestId('payment-timeline');
    this.timelineSteps = page.getByTestId('timeline-step');
    this.downloadInvoiceButton = page.getByTestId('download-invoice-button');
    this.backToListLink = page.getByTestId('back-to-payment-list');

    /* Checkout */
    this.checkoutButton = page.getByTestId('checkout-button');
    this.checkoutLoadingIndicator = page.getByTestId('checkout-loading');
  }

  async goto(): Promise<void> {
    await this.page.goto('/payments');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  async gotoDetail(paymentId: string): Promise<void> {
    await this.page.goto(`/payments/${paymentId}`);
    await this.paymentAmount.waitFor({ state: 'visible' });
  }

  /**
   * Click on a payment row to navigate to the detail view.
   */
  async viewPaymentDetail(index: number): Promise<void> {
    await this.paymentRows.nth(index).click();
    await this.page.waitForURL('**/payments/*');
  }

  /**
   * Initiate a checkout flow.
   */
  async initiateCheckout(): Promise<void> {
    await this.checkoutButton.click();
  }

  /**
   * Get the number of payment rows.
   */
  async getPaymentCount(): Promise<number> {
    return this.paymentRows.count();
  }

  /**
   * Get the displayed status for a specific payment row.
   */
  async getPaymentStatus(index: number): Promise<string | null> {
    const row = this.paymentRows.nth(index);
    return row.getByTestId('payment-row-status').textContent();
  }

  /**
   * Get the displayed amount for a specific payment row.
   */
  async getPaymentAmount(index: number): Promise<string | null> {
    const row = this.paymentRows.nth(index);
    return row.getByTestId('payment-row-amount').textContent();
  }

  /**
   * Get the number of completed timeline steps in the detail view.
   */
  async getTimelineStepCount(): Promise<number> {
    return this.timelineSteps.count();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
  }

  async expectPaymentDetailLoaded(): Promise<void> {
    await expect(this.paymentAmount).toBeVisible();
    await expect(this.paymentStatus).toBeVisible();
  }
}
```

- **Verify**: TypeScript compiles without errors

---

## Task 12 Detail: NotificationPage POM

- **What**: Page Object Model for the notification system. Covers the bell icon (in the toolbar), the dropdown panel, and the full notification list page at `/notifications`. Includes methods for checking the unread badge count, opening the dropdown, marking notifications as read, and navigating to the full list.
- **Where**: `e2e/pages/notification.page.ts`
- **Why**: Consumed by `notification.spec.ts` for bell icon, dropdown, mark read, and full list scenarios.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the notification system.
 *
 * Covers:
 * - Bell icon in the application toolbar (visible on all authenticated pages)
 * - Dropdown panel with recent notifications
 * - Full notification list page (/notifications)
 *
 * Used by: notification.spec.ts
 */
export class NotificationPage {
  readonly page: Page;

  /* Bell Icon (toolbar) */
  readonly bellIcon: Locator;
  readonly unreadBadge: Locator;

  /* Dropdown Panel */
  readonly dropdown: Locator;
  readonly dropdownItems: Locator;
  readonly markAllReadButton: Locator;
  readonly viewAllLink: Locator;

  /* Full List Page */
  readonly pageTitle: Locator;
  readonly notificationList: Locator;
  readonly notificationItems: Locator;
  readonly emptyState: Locator;
  readonly filterByType: Locator;
  readonly filterByRead: Locator;

  constructor(page: Page) {
    this.page = page;

    /* Bell Icon */
    this.bellIcon = page.getByTestId('notification-bell');
    this.unreadBadge = page.getByTestId('notification-unread-count');

    /* Dropdown Panel */
    this.dropdown = page.getByTestId('notification-dropdown');
    this.dropdownItems = page.getByTestId('notification-dropdown-item');
    this.markAllReadButton = page.getByTestId('mark-all-read-button');
    this.viewAllLink = page.getByTestId('view-all-notifications-link');

    /* Full List Page */
    this.pageTitle = page.getByRole('heading', { name: /notifications/i });
    this.notificationList = page.getByTestId('notification-list');
    this.notificationItems = page.getByTestId('notification-item');
    this.emptyState = page.getByTestId('notification-empty');
    this.filterByType = page.getByTestId('notification-filter-type');
    this.filterByRead = page.getByTestId('notification-filter-read');
  }

  /**
   * Navigate to the full notification list page.
   */
  async goto(): Promise<void> {
    await this.page.goto('/notifications');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Open the notification dropdown by clicking the bell icon.
   */
  async openDropdown(): Promise<void> {
    await this.bellIcon.click();
    await this.dropdown.waitFor({ state: 'visible' });
  }

  /**
   * Close the dropdown by clicking outside.
   */
  async closeDropdown(): Promise<void> {
    await this.page.keyboard.press('Escape');
    await this.dropdown.waitFor({ state: 'hidden' });
  }

  /**
   * Get the unread notification count from the badge.
   * Returns 0 if the badge is not visible.
   */
  async getUnreadCount(): Promise<number> {
    if (await this.unreadBadge.isVisible()) {
      const text = await this.unreadBadge.textContent();
      return parseInt(text || '0', 10);
    }
    return 0;
  }

  /**
   * Click "Mark all as read" in the dropdown.
   */
  async markAllAsRead(): Promise<void> {
    await this.markAllReadButton.click();
    await this.page.waitForTimeout(300);
  }

  /**
   * Mark a single notification as read by clicking it in the dropdown.
   */
  async markAsRead(index: number): Promise<void> {
    await this.dropdownItems.nth(index).click();
    await this.page.waitForTimeout(300);
  }

  /**
   * Navigate from the dropdown to the full notifications page.
   */
  async goToFullList(): Promise<void> {
    await this.viewAllLink.click();
    await this.page.waitForURL('**/notifications');
  }

  /**
   * Get the number of notification items on the full list page.
   */
  async getNotificationCount(): Promise<number> {
    return this.notificationItems.count();
  }

  /**
   * Get the number of items in the dropdown.
   */
  async getDropdownItemCount(): Promise<number> {
    return this.dropdownItems.count();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
  }

  async expectBellVisible(): Promise<void> {
    await expect(this.bellIcon).toBeVisible();
  }
}
```

- **Verify**: TypeScript compiles without errors

---

## Task 13 Detail: SettingsPage POM

- **What**: Page Object Model for `/settings`. Covers RGPD consent toggles, data export request, data download verification, and account deletion.
- **Where**: `e2e/pages/settings.page.ts`
- **Why**: Consumed by `rgpd.spec.ts` for consent toggle, data export, and download scenarios.
- **Content**:

```typescript
import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the settings / RGPD page (/settings).
 *
 * Used by: rgpd.spec.ts
 */
export class SettingsPage {
  readonly page: Page;
  readonly pageTitle: Locator;

  /* RGPD Consent Section */
  readonly consentSection: Locator;
  readonly analyticsConsentToggle: Locator;
  readonly marketingConsentToggle: Locator;
  readonly thirdPartyConsentToggle: Locator;
  readonly saveConsentButton: Locator;
  readonly consentSuccessMessage: Locator;

  /* Data Export Section */
  readonly exportSection: Locator;
  readonly requestExportButton: Locator;
  readonly exportStatusLabel: Locator;
  readonly downloadExportButton: Locator;
  readonly exportProgressBar: Locator;

  /* Account Section */
  readonly deleteAccountButton: Locator;
  readonly deleteConfirmDialog: Locator;
  readonly confirmDeleteInput: Locator;
  readonly confirmDeleteButton: Locator;
  readonly cancelDeleteButton: Locator;

  /* Profile Section */
  readonly profileSection: Locator;
  readonly changePasswordButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: /param/i });

    /* RGPD Consent */
    this.consentSection = page.getByTestId('rgpd-consent-section');
    this.analyticsConsentToggle = page.getByTestId('consent-analytics');
    this.marketingConsentToggle = page.getByTestId('consent-marketing');
    this.thirdPartyConsentToggle = page.getByTestId('consent-third-party');
    this.saveConsentButton = page.getByTestId('save-consent-button');
    this.consentSuccessMessage = page.getByTestId('consent-success');

    /* Data Export */
    this.exportSection = page.getByTestId('data-export-section');
    this.requestExportButton = page.getByTestId('request-export-button');
    this.exportStatusLabel = page.getByTestId('export-status');
    this.downloadExportButton = page.getByTestId('download-export-button');
    this.exportProgressBar = page.getByTestId('export-progress');

    /* Account */
    this.deleteAccountButton = page.getByTestId('delete-account-button');
    this.deleteConfirmDialog = page.getByTestId('delete-account-dialog');
    this.confirmDeleteInput = page.getByTestId('delete-confirm-input');
    this.confirmDeleteButton = page.getByTestId('confirm-delete-button');
    this.cancelDeleteButton = page.getByTestId('cancel-delete-button');

    /* Profile */
    this.profileSection = page.getByTestId('profile-section');
    this.changePasswordButton = page.getByTestId('change-password-button');
  }

  async goto(): Promise<void> {
    await this.page.goto('/settings');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Toggle the analytics consent switch.
   */
  async toggleAnalyticsConsent(): Promise<void> {
    await this.analyticsConsentToggle.click();
  }

  /**
   * Toggle the marketing consent switch.
   */
  async toggleMarketingConsent(): Promise<void> {
    await this.marketingConsentToggle.click();
  }

  /**
   * Toggle the third-party consent switch.
   */
  async toggleThirdPartyConsent(): Promise<void> {
    await this.thirdPartyConsentToggle.click();
  }

  /**
   * Save the current consent settings.
   */
  async saveConsent(): Promise<void> {
    await this.saveConsentButton.click();
    await this.consentSuccessMessage.waitFor({ state: 'visible' });
  }

  /**
   * Check if a consent toggle is currently checked.
   */
  async isAnalyticsConsentEnabled(): Promise<boolean> {
    return this.analyticsConsentToggle.isChecked();
  }

  async isMarketingConsentEnabled(): Promise<boolean> {
    return this.marketingConsentToggle.isChecked();
  }

  /**
   * Request a RGPD data export.
   */
  async requestDataExport(): Promise<void> {
    await this.requestExportButton.click();
    await this.exportStatusLabel.waitFor({ state: 'visible' });
  }

  /**
   * Wait for the export to be ready and download it.
   * Returns the download object for verification.
   */
  async downloadExport(): Promise<void> {
    await this.downloadExportButton.waitFor({ state: 'visible', timeout: 30000 });
    const [download] = await Promise.all([
      this.page.waitForEvent('download'),
      this.downloadExportButton.click(),
    ]);
    const path = await download.path();
    expect(path).toBeTruthy();
  }

  /**
   * Get the export status text.
   */
  async getExportStatus(): Promise<string | null> {
    return this.exportStatusLabel.textContent();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
    await expect(this.consentSection).toBeVisible();
  }
}
```

- **Verify**: TypeScript compiles without errors

---

## Task 14 Detail: Test Data Fixtures

- **What**: Centralized test data file with realistic French data for users, families, associations, activities, and payment scenarios. All spec files import from this single source, ensuring consistency and easy maintenance.
- **Where**: `e2e/fixtures/test-data.ts`
- **Why**: Prevents test data duplication across specs. All data uses French names, cities, and associations to match the application's target audience. Data matches the seed data loaded by `docker-compose.e2e.yml`.
- **Content**:

```typescript
/**
 * Centralized test data for all E2E specs.
 *
 * This data matches the seed data loaded by docker-compose.e2e.yml
 * into the PostgreSQL databases. All names, cities, and associations
 * use realistic French data.
 *
 * IMPORTANT: If you change this file, also update the SQL seed scripts
 * in docker/init-scripts/e2e-seed-*.sql to stay in sync.
 */

// ── Users ─────────────────────────────────────────────────────────────

export const TEST_USERS = {
  /** Pre-seeded family user -- exists in DB on startup */
  familyUser: {
    email: 'famille.dupont@test.familyhobbies.fr',
    password: 'Test1234!',
    firstName: 'Marie',
    lastName: 'Dupont',
    role: 'FAMILY' as const,
  },

  /** Pre-seeded association admin user */
  associationAdmin: {
    email: 'admin.assolyonnaise@test.familyhobbies.fr',
    password: 'Test1234!',
    firstName: 'Jean',
    lastName: 'Martin',
    role: 'ASSOCIATION' as const,
  },

  /** Pre-seeded platform admin */
  admin: {
    email: 'admin@test.familyhobbies.fr',
    password: 'Admin1234!',
    firstName: 'Philippe',
    lastName: 'Leroy',
    role: 'ADMIN' as const,
  },

  /** New user for registration tests -- does NOT exist in DB */
  newUser: {
    email: `test.nouveau.${Date.now()}@test.familyhobbies.fr`,
    password: 'Nouveau1234!',
    firstName: 'Sophie',
    lastName: 'Bernard',
    role: 'FAMILY' as const,
  },

  /** User with invalid credentials -- for error testing */
  invalidUser: {
    email: 'inexistant@test.familyhobbies.fr',
    password: 'MauvaisMotDePasse!',
  },
};

// ── Families ──────────────────────────────────────────────────────────

export const TEST_FAMILIES = {
  /** Pre-seeded family with 3 members */
  dupont: {
    id: '550e8400-e29b-41d4-a716-446655440001',
    name: 'Famille Dupont',
    members: [
      {
        firstName: 'Marie',
        lastName: 'Dupont',
        birthDate: '1985-03-15',
        role: 'Parent',
      },
      {
        firstName: 'Lucas',
        lastName: 'Dupont',
        birthDate: '2012-07-22',
        role: 'Enfant',
      },
      {
        firstName: 'Emma',
        lastName: 'Dupont',
        birthDate: '2015-11-08',
        role: 'Enfant',
      },
    ],
  },

  /** Data for creating a new family in tests */
  newFamily: {
    name: 'Famille Moreau',
    members: [
      {
        firstName: 'Claire',
        lastName: 'Moreau',
        birthDate: '1990-06-20',
        role: 'Parent',
      },
    ],
  },

  /** Data for adding a new member */
  newMember: {
    firstName: 'Hugo',
    lastName: 'Dupont',
    birthDate: '2018-01-30',
    role: 'Enfant',
  },
};

// ── Associations ──────────────────────────────────────────────────────

export const TEST_ASSOCIATIONS = {
  /** Pre-seeded sport association in Lyon */
  sportLyon: {
    id: '660e8400-e29b-41d4-a716-446655440010',
    name: 'Association Sportive de Lyon',
    slug: 'association-sportive-de-lyon',
    city: 'Lyon',
    postalCode: '69001',
    category: 'Sport',
    activities: ['Football', 'Basketball', 'Gymnastique'],
  },

  /** Pre-seeded dance school in Paris */
  danseParis: {
    id: '660e8400-e29b-41d4-a716-446655440011',
    name: 'Ecole de Danse Classique de Paris',
    slug: 'ecole-danse-classique-paris',
    city: 'Paris',
    postalCode: '75004',
    category: 'Danse',
    activities: ['Danse classique', 'Modern jazz'],
  },

  /** Pre-seeded music conservatory in Toulouse */
  musiqueToulouse: {
    id: '660e8400-e29b-41d4-a716-446655440012',
    name: 'Conservatoire Municipal de Musique de Toulouse',
    slug: 'conservatoire-musique-toulouse',
    city: 'Toulouse',
    postalCode: '31000',
    category: 'Musique',
    activities: ['Piano', 'Violon', 'Chorale'],
  },

  /** Association that returns no search results (for empty state tests) */
  nonExistent: {
    keyword: 'ZzzAssociationInexistante999',
    city: 'VilleImaginaire',
  },
};

// ── Subscriptions ─────────────────────────────────────────────────────

export const TEST_SUBSCRIPTIONS = {
  /** Pre-seeded active subscription: Lucas Dupont -> Football @ Lyon */
  lucasFootball: {
    id: '770e8400-e29b-41d4-a716-446655440020',
    memberName: 'Lucas Dupont',
    associationName: 'Association Sportive de Lyon',
    activityName: 'Football',
    status: 'ACTIVE',
  },

  /** Pre-seeded active subscription: Emma Dupont -> Danse classique @ Paris */
  emmaDanse: {
    id: '770e8400-e29b-41d4-a716-446655440021',
    memberName: 'Emma Dupont',
    associationName: 'Ecole de Danse Classique de Paris',
    activityName: 'Danse classique',
    status: 'ACTIVE',
  },
};

// ── Payments ──────────────────────────────────────────────────────────

export const TEST_PAYMENTS = {
  /** Pre-seeded completed payment */
  completedPayment: {
    id: '880e8400-e29b-41d4-a716-446655440030',
    amount: '120.00',
    status: 'COMPLETED',
    associationName: 'Association Sportive de Lyon',
    method: 'CARD',
  },

  /** Pre-seeded pending payment (awaiting webhook) */
  pendingPayment: {
    id: '880e8400-e29b-41d4-a716-446655440031',
    amount: '85.00',
    status: 'PENDING',
    associationName: 'Ecole de Danse Classique de Paris',
    method: 'CARD',
  },
};

// ── Notifications ─────────────────────────────────────────────────────

export const TEST_NOTIFICATIONS = {
  /** Expected minimum number of unread notifications for familyUser */
  expectedUnreadCount: 3,

  /** Notification types that should appear */
  expectedTypes: [
    'SUBSCRIPTION_CONFIRMED',
    'PAYMENT_COMPLETED',
    'SESSION_REMINDER',
  ],
};

// ── API Configuration ─────────────────────────────────────────────────

export const API_CONFIG = {
  baseUrl: process.env.API_URL || 'http://localhost:8080',
  timeout: 10000,
};
```

- **Verify**: Imported by all spec files without errors

---

## Task 15 Detail: API Helper

- **What**: Utility module for making direct REST API calls to the backend services through the api-gateway. Used for test setup (creating seed data), teardown (cleaning up), and verification (checking database state independent of the UI).
- **Where**: `e2e/helpers/api-helper.ts`
- **Why**: E2E tests sometimes need to bypass the UI for setup/teardown efficiency or to verify backend state directly. For example, `payment.spec.ts` uses the API helper to simulate a webhook callback from HelloAsso.
- **Content**:

```typescript
import { APIRequestContext, request } from '@playwright/test';
import { API_CONFIG } from '../fixtures/test-data';

/**
 * Helper for direct REST API calls to the backend through api-gateway.
 *
 * Used for:
 * - Test setup: creating seed data via API instead of UI
 * - Test teardown: cleaning up created resources
 * - Verification: checking backend state independent of the UI
 * - Webhook simulation: triggering payment webhooks
 *
 * All methods use the api-gateway base URL (default: http://localhost:8080).
 */
export class ApiHelper {
  private context: APIRequestContext | null = null;
  private authToken: string | null = null;

  /**
   * Initialize the API request context.
   */
  async init(): Promise<void> {
    this.context = await request.newContext({
      baseURL: API_CONFIG.baseUrl,
      extraHTTPHeaders: {
        'Content-Type': 'application/json',
        'Accept-Language': 'fr-FR',
      },
    });
  }

  /**
   * Dispose the API request context.
   */
  async dispose(): Promise<void> {
    if (this.context) {
      await this.context.dispose();
      this.context = null;
    }
  }

  /**
   * Authenticate and store the JWT token for subsequent requests.
   */
  async authenticate(email: string, password: string): Promise<string> {
    this.ensureContext();
    const response = await this.context!.post('/api/auth/login', {
      data: { email, password },
    });

    if (!response.ok()) {
      throw new Error(
        `Authentication failed: ${response.status()} ${response.statusText()}`
      );
    }

    const body = await response.json();
    this.authToken = body.token;
    return this.authToken!;
  }

  /**
   * Make an authenticated GET request.
   */
  async get<T>(endpoint: string): Promise<T> {
    this.ensureContext();
    this.ensureAuthenticated();

    const response = await this.context!.get(endpoint, {
      headers: { Authorization: `Bearer ${this.authToken}` },
    });

    if (!response.ok()) {
      throw new Error(
        `GET ${endpoint} failed: ${response.status()} ${response.statusText()}`
      );
    }

    return response.json() as Promise<T>;
  }

  /**
   * Make an authenticated POST request.
   */
  async post<T>(endpoint: string, data: unknown): Promise<T> {
    this.ensureContext();
    this.ensureAuthenticated();

    const response = await this.context!.post(endpoint, {
      data,
      headers: { Authorization: `Bearer ${this.authToken}` },
    });

    if (!response.ok()) {
      throw new Error(
        `POST ${endpoint} failed: ${response.status()} ${response.statusText()}`
      );
    }

    return response.json() as Promise<T>;
  }

  /**
   * Make an authenticated DELETE request.
   */
  async delete(endpoint: string): Promise<void> {
    this.ensureContext();
    this.ensureAuthenticated();

    const response = await this.context!.delete(endpoint, {
      headers: { Authorization: `Bearer ${this.authToken}` },
    });

    if (!response.ok()) {
      throw new Error(
        `DELETE ${endpoint} failed: ${response.status()} ${response.statusText()}`
      );
    }
  }

  /**
   * Simulate a HelloAsso webhook callback.
   * This bypasses the signature validation for test purposes.
   */
  async simulateWebhook(
    eventType: string,
    payload: Record<string, unknown>
  ): Promise<void> {
    this.ensureContext();

    const response = await this.context!.post('/api/webhooks/helloasso', {
      data: {
        eventType,
        data: payload,
      },
      headers: {
        'X-HelloAsso-Signature': 'e2e-test-signature',
        'X-E2E-Test': 'true',
      },
    });

    if (!response.ok()) {
      throw new Error(
        `Webhook simulation failed: ${response.status()} ${response.statusText()}`
      );
    }
  }

  /**
   * Verify that a payment has the expected status in the database.
   */
  async verifyPaymentStatus(
    paymentId: string,
    expectedStatus: string
  ): Promise<boolean> {
    const payment = await this.get<{ status: string }>(
      `/api/payments/${paymentId}`
    );
    return payment.status === expectedStatus;
  }

  /**
   * Get the current user's notification count.
   */
  async getUnreadNotificationCount(): Promise<number> {
    const response = await this.get<{ count: number }>(
      '/api/notifications/unread/count'
    );
    return response.count;
  }

  /**
   * Request a RGPD data export via API.
   */
  async requestDataExport(): Promise<{ exportId: string; status: string }> {
    return this.post('/api/users/me/export', {});
  }

  /**
   * Clean up a test user by email (for registration test teardown).
   */
  async deleteTestUser(email: string): Promise<void> {
    // Authenticate as admin first
    const adminToken = this.authToken;
    await this.authenticate('admin@test.familyhobbies.fr', 'Admin1234!');
    await this.delete(`/api/admin/users/by-email/${encodeURIComponent(email)}`);
    // Restore previous token
    this.authToken = adminToken;
  }

  private ensureContext(): void {
    if (!this.context) {
      throw new Error(
        'ApiHelper not initialized. Call init() before making requests.'
      );
    }
  }

  private ensureAuthenticated(): void {
    if (!this.authToken) {
      throw new Error(
        'Not authenticated. Call authenticate() before making authenticated requests.'
      );
    }
  }
}
```

- **Verify**: TypeScript compiles; `authenticate()` returns a valid token against the running stack

---

## Task 16 Detail: Auth Helper

- **What**: Authentication shortcut helper that creates and saves a browser storage state (cookies + local storage) for reuse across tests. Avoids repeating the login flow in every test by saving the authenticated state to a JSON file.
- **Where**: `e2e/helpers/auth-helper.ts`
- **Why**: Playwright's `storageState` feature allows tests to start already logged in. The auth helper performs the login once and saves the state, so all subsequent tests in a project can use `{ storageState: 'path' }` to skip the login page.
- **Content**:

```typescript
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
```

- **Verify**: TypeScript compiles; `authenticateFamilyUser()` creates `.auth/family-user.json`

---

## Task 17 Detail: Docker Compose E2E

- **What**: Complete Docker Compose file for running the full application stack in E2E test mode. Includes all 6 microservices, PostgreSQL, Kafka (KRaft mode), Zookeeper, the Angular frontend served via nginx, and health checks for startup orchestration. Each service connects to its own database, and SQL seed scripts pre-populate test data.
- **Where**: `docker/docker-compose.e2e.yml`
- **Why**: E2E tests require the entire application stack running with realistic data. The health checks ensure services start in the correct order. The seed scripts insert the same data referenced in `e2e/fixtures/test-data.ts`.
- **Content**:

```yaml
# docker/docker-compose.e2e.yml
# Full stack for Playwright E2E tests.
# Usage: docker compose -f docker-compose.e2e.yml up -d --wait

version: "3.9"

services:
  # ── Infrastructure ──────────────────────────────────────────────────

  postgres:
    image: postgres:16-alpine
    container_name: fhm-e2e-postgres
    environment:
      POSTGRES_USER: fhm_admin
      POSTGRES_PASSWORD: fhm_e2e_password
      POSTGRES_MULTIPLE_DATABASES: >-
        familyhobbies_users,
        familyhobbies_associations,
        familyhobbies_payments,
        familyhobbies_notifications
    volumes:
      - ./init-scripts/create-multiple-databases.sh:/docker-entrypoint-initdb.d/01-create-databases.sh
      - ./init-scripts/e2e-seed-users.sql:/docker-entrypoint-initdb.d/10-seed-users.sql
      - ./init-scripts/e2e-seed-associations.sql:/docker-entrypoint-initdb.d/11-seed-associations.sql
      - ./init-scripts/e2e-seed-payments.sql:/docker-entrypoint-initdb.d/12-seed-payments.sql
      - ./init-scripts/e2e-seed-notifications.sql:/docker-entrypoint-initdb.d/13-seed-notifications.sql
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U fhm_admin"]
      interval: 5s
      timeout: 5s
      retries: 10
    networks:
      - fhm-e2e

  kafka:
    image: confluentinc/cp-kafka:7.6.0
    container_name: fhm-e2e-kafka
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0
      CLUSTER_ID: "e2e-test-cluster-id-001"
    ports:
      - "9092:9092"
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "kafka:9092"]
      interval: 10s
      timeout: 10s
      retries: 15
    networks:
      - fhm-e2e

  # ── Microservices ───────────────────────────────────────────────────

  discovery-service:
    build:
      context: ../backend/discovery-service
      dockerfile: Dockerfile
    container_name: fhm-e2e-discovery
    ports:
      - "8761:8761"
    environment:
      SPRING_PROFILES_ACTIVE: e2e
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 30s
    networks:
      - fhm-e2e

  api-gateway:
    build:
      context: ../backend/api-gateway
      dockerfile: Dockerfile
    container_name: fhm-e2e-gateway
    ports:
      - "8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: e2e
      EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: http://discovery-service:8761/eureka
      JWT_SECRET: e2e-test-jwt-secret-key-at-least-256-bits-long-for-hs256
    depends_on:
      discovery-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 30s
    networks:
      - fhm-e2e

  user-service:
    build:
      context: ../backend/user-service
      dockerfile: Dockerfile
    container_name: fhm-e2e-user
    ports:
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: e2e
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/familyhobbies_users
      SPRING_DATASOURCE_USERNAME: fhm_admin
      SPRING_DATASOURCE_PASSWORD: fhm_e2e_password
      EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: http://discovery-service:8761/eureka
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      JWT_SECRET: e2e-test-jwt-secret-key-at-least-256-bits-long-for-hs256
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
      discovery-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 45s
    networks:
      - fhm-e2e

  association-service:
    build:
      context: ../backend/association-service
      dockerfile: Dockerfile
    container_name: fhm-e2e-association
    ports:
      - "8082:8082"
    environment:
      SPRING_PROFILES_ACTIVE: e2e
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/familyhobbies_associations
      SPRING_DATASOURCE_USERNAME: fhm_admin
      SPRING_DATASOURCE_PASSWORD: fhm_e2e_password
      EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: http://discovery-service:8761/eureka
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      HELLOASSO_CLIENT_ID: e2e-sandbox-client-id
      HELLOASSO_CLIENT_SECRET: e2e-sandbox-client-secret
      HELLOASSO_BASE_URL: https://api.helloasso-sandbox.com/v5
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
      discovery-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 45s
    networks:
      - fhm-e2e

  payment-service:
    build:
      context: ../backend/payment-service
      dockerfile: Dockerfile
    container_name: fhm-e2e-payment
    ports:
      - "8083:8083"
    environment:
      SPRING_PROFILES_ACTIVE: e2e
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/familyhobbies_payments
      SPRING_DATASOURCE_USERNAME: fhm_admin
      SPRING_DATASOURCE_PASSWORD: fhm_e2e_password
      EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: http://discovery-service:8761/eureka
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      HELLOASSO_CLIENT_ID: e2e-sandbox-client-id
      HELLOASSO_CLIENT_SECRET: e2e-sandbox-client-secret
      HELLOASSO_BASE_URL: https://api.helloasso-sandbox.com/v5
      HELLOASSO_WEBHOOK_SECRET: e2e-webhook-secret
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
      discovery-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8083/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 45s
    networks:
      - fhm-e2e

  notification-service:
    build:
      context: ../backend/notification-service
      dockerfile: Dockerfile
    container_name: fhm-e2e-notification
    ports:
      - "8084:8084"
    environment:
      SPRING_PROFILES_ACTIVE: e2e
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/familyhobbies_notifications
      SPRING_DATASOURCE_USERNAME: fhm_admin
      SPRING_DATASOURCE_PASSWORD: fhm_e2e_password
      EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: http://discovery-service:8761/eureka
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SPRING_MAIL_HOST: mailhog
      SPRING_MAIL_PORT: 1025
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_healthy
      discovery-service:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8084/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 15
      start_period: 45s
    networks:
      - fhm-e2e

  # ── Frontend ────────────────────────────────────────────────────────

  frontend:
    build:
      context: ../frontend
      dockerfile: Dockerfile
      args:
        API_URL: http://api-gateway:8080
    container_name: fhm-e2e-frontend
    ports:
      - "4200:80"
    depends_on:
      api-gateway:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:80"]
      interval: 5s
      timeout: 5s
      retries: 10
    networks:
      - fhm-e2e

  # ── Supporting Services ─────────────────────────────────────────────

  mailhog:
    image: mailhog/mailhog:v1.0.1
    container_name: fhm-e2e-mailhog
    ports:
      - "1025:1025"  # SMTP
      - "8025:8025"  # Web UI
    networks:
      - fhm-e2e

networks:
  fhm-e2e:
    driver: bridge
    name: fhm-e2e-network
```

- **Verify**: `docker compose -f docker/docker-compose.e2e.yml config` -> valid YAML; `docker compose -f docker/docker-compose.e2e.yml up -d --wait` -> all services healthy within 5 minutes

---

## Task 18 Detail: GitHub Actions Workflow

- **What**: Complete CI workflow for running Playwright E2E tests on every push and pull request. Starts the Docker Compose E2E environment, waits for all services to be healthy, installs Playwright browsers, runs the full test suite, and uploads failure screenshots, videos, traces, and the HTML report as artifacts.
- **Where**: `.github/workflows/e2e-tests.yml`
- **Why**: Automated E2E testing on every push ensures regressions are caught immediately. Artifact upload allows debugging failures without reproducing locally.
- **Content**:

```yaml
# .github/workflows/e2e-tests.yml
# Playwright E2E test pipeline for Family Hobbies Manager.
#
# Triggers: push to main, pull requests to main
# Environment: Docker Compose (all services + databases)
# Browsers: Chromium, Firefox, WebKit
# Artifacts: HTML report, screenshots, videos, traces

name: E2E Tests (Playwright)

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
  workflow_dispatch:

concurrency:
  group: e2e-${{ github.ref }}
  cancel-in-progress: true

env:
  CI: true
  BASE_URL: http://localhost:4200
  API_URL: http://localhost:8080

jobs:
  e2e-tests:
    name: Playwright E2E
    runs-on: ubuntu-latest
    timeout-minutes: 45

    steps:
      # ── Checkout ──────────────────────────────────────────────────────
      - name: Checkout repository
        uses: actions/checkout@v4

      # ── Java Setup (for building backend services) ────────────────────
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'

      # ── Node.js Setup (for Playwright) ────────────────────────────────
      - name: Set up Node.js 18
        uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'npm'
          cache-dependency-path: 'e2e/package-lock.json'

      # ── Install E2E dependencies ──────────────────────────────────────
      - name: Install E2E dependencies
        working-directory: e2e
        run: npm ci

      # ── Install Playwright browsers ───────────────────────────────────
      - name: Install Playwright browsers
        working-directory: e2e
        run: npx playwright install --with-deps chromium firefox webkit

      # ── Build backend services ────────────────────────────────────────
      - name: Build backend services
        working-directory: backend
        run: |
          mvn clean package -DskipTests -pl \
            error-handling,\
            common,\
            discovery-service,\
            api-gateway,\
            user-service,\
            association-service,\
            payment-service,\
            notification-service \
            -am

      # ── Start Docker Compose E2E environment ──────────────────────────
      - name: Start E2E environment
        working-directory: docker
        run: |
          docker compose -f docker-compose.e2e.yml up -d --build --wait
        timeout-minutes: 10

      # ── Wait for all services to be healthy ───────────────────────────
      - name: Wait for services to be healthy
        run: |
          echo "Waiting for all services to be healthy..."

          # Wait for each service endpoint
          services=(
            "http://localhost:8761/actuator/health:discovery-service"
            "http://localhost:8080/actuator/health:api-gateway"
            "http://localhost:8081/actuator/health:user-service"
            "http://localhost:8082/actuator/health:association-service"
            "http://localhost:8083/actuator/health:payment-service"
            "http://localhost:8084/actuator/health:notification-service"
            "http://localhost:4200:frontend"
          )

          for service_entry in "${services[@]}"; do
            IFS=":" read -r url name <<< "$service_entry"
            echo "Waiting for $name at $url..."
            for i in $(seq 1 60); do
              if curl -sf "$url" > /dev/null 2>&1; then
                echo "$name is healthy!"
                break
              fi
              if [ "$i" -eq 60 ]; then
                echo "TIMEOUT: $name did not become healthy within 5 minutes"
                docker compose -f docker/docker-compose.e2e.yml logs "$name"
                exit 1
              fi
              sleep 5
            done
          done

          echo "All services are healthy!"
        timeout-minutes: 10

      # ── Run Playwright tests ──────────────────────────────────────────
      - name: Run Playwright E2E tests
        working-directory: e2e
        run: npx playwright test
        timeout-minutes: 20

      # ── Upload test results (always, even on failure) ─────────────────
      - name: Upload Playwright HTML report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-html-report
          path: e2e/playwright-report/
          retention-days: 14

      - name: Upload test results (screenshots, videos, traces)
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-test-results
          path: e2e/test-results/
          retention-days: 14

      - name: Upload JUnit results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: playwright-junit-results
          path: e2e/test-results/junit-results.xml
          retention-days: 14

      # ── Tear down Docker Compose ──────────────────────────────────────
      - name: Stop E2E environment
        if: always()
        working-directory: docker
        run: docker compose -f docker-compose.e2e.yml down -v --remove-orphans

      # ── Docker logs on failure ────────────────────────────────────────
      - name: Dump Docker logs on failure
        if: failure()
        working-directory: docker
        run: docker compose -f docker-compose.e2e.yml logs --tail=200
```

- **Verify**: `act -n` (dry run) shows valid workflow; push to feature branch triggers the workflow

---

## Task 19: Test Spec Files (8)

> All 8 test spec files are documented in the companion file:
> **[S8-001-playwright-e2e-suite-tests.md](./S8-001-playwright-e2e-suite-tests.md)**
>
> This split keeps each documentation file under the 1000-line limit while providing
> complete, production-ready Playwright test implementations.

---

## Acceptance Criteria Checklist

- [ ] `playwright.config.ts` is valid and lists all test files via `npx playwright test --list`
- [ ] `package.json` and `tsconfig.json` are valid; `npm install` and `tsc --noEmit` succeed
- [ ] All 11 Page Object Models compile and export correct selectors with action methods
- [ ] `test-data.ts` contains realistic French test data matching the SQL seed scripts
- [ ] `api-helper.ts` provides setup/teardown/verification methods callable from specs
- [ ] `auth-helper.ts` saves and restores browser storage state for authenticated sessions
- [ ] `docker-compose.e2e.yml` starts all services and seeds data; `docker compose up --wait` succeeds
- [ ] `e2e-tests.yml` GitHub Actions workflow triggers on push/PR and uploads artifacts
- [ ] All 8 test spec files pass against the running Docker Compose stack
- [ ] POM pattern used consistently across all specs -- no raw selectors in test files
- [ ] Cross-browser testing works (Chromium minimum, Firefox and WebKit validated)
- [ ] Failure screenshots, videos, and traces are captured and uploaded as CI artifacts
- [ ] HTML report is generated and uploaded
