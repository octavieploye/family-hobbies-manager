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
    /* -- Setup -------------------------------------------------- */
    {
      name: 'setup',
      testMatch: /global-setup\.ts/,
      teardown: 'teardown',
    },
    {
      name: 'teardown',
      testMatch: /global-teardown\.ts/,
    },

    /* -- Desktop Browsers ---------------------------------------- */
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

    /* -- Mobile Browser ------------------------------------------ */
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
