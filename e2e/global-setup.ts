import { test as setup, expect } from '@playwright/test';
import { API_CONFIG } from './fixtures/test-data';

/**
 * Global setup for Playwright E2E tests.
 *
 * Runs once before all test projects. Verifies that the application
 * stack is healthy and seed data is available before tests begin.
 *
 * Checks:
 * 1. Frontend is reachable at BASE_URL (http://localhost:4200)
 * 2. API gateway is reachable at API_URL (http://localhost:8080)
 * 3. Backend services respond to health checks
 */

setup('verify application stack is healthy', async ({ request }) => {
  // -- Check frontend is reachable --
  const frontendUrl = process.env.BASE_URL || 'http://localhost:4200';
  console.log(`[global-setup] Checking frontend at ${frontendUrl}...`);

  const frontendResponse = await request.get(frontendUrl, {
    timeout: 30000,
    failOnStatusCode: false,
  });
  expect(
    frontendResponse.ok(),
    `Frontend at ${frontendUrl} is not reachable (status: ${frontendResponse.status()})`
  ).toBeTruthy();
  console.log('[global-setup] Frontend is reachable.');

  // -- Check API gateway health --
  const apiUrl = API_CONFIG.baseUrl;
  console.log(`[global-setup] Checking API gateway at ${apiUrl}...`);

  const gatewayResponse = await request.get(`${apiUrl}/actuator/health`, {
    timeout: 30000,
    failOnStatusCode: false,
  });
  expect(
    gatewayResponse.ok(),
    `API gateway at ${apiUrl} is not healthy (status: ${gatewayResponse.status()})`
  ).toBeTruthy();
  console.log('[global-setup] API gateway is healthy.');

  // -- Check individual service health via gateway --
  const serviceEndpoints = [
    '/api/v1/auth/health',
    '/api/v1/associations/health',
    '/api/v1/payments/health',
    '/api/v1/notifications/health',
  ];

  for (const endpoint of serviceEndpoints) {
    const url = `${apiUrl}${endpoint}`;
    console.log(`[global-setup] Checking service at ${url}...`);

    const response = await request.get(url, {
      timeout: 15000,
      failOnStatusCode: false,
    });

    // Some services may not have /health endpoints; just log and continue
    if (response.ok()) {
      console.log(`[global-setup] ${endpoint} is healthy.`);
    } else {
      console.warn(
        `[global-setup] ${endpoint} returned status ${response.status()} — may not have health endpoint.`
      );
    }
  }

  console.log('[global-setup] Application stack verification complete.');
});

setup('verify seed data is available', async ({ request }) => {
  const apiUrl = API_CONFIG.baseUrl;

  // Attempt to login with the seeded family user to verify DB has seed data
  console.log('[global-setup] Verifying seed data by attempting login...');

  const loginResponse = await request.post(`${apiUrl}/api/auth/login`, {
    data: {
      email: 'famille.dupont@test.familyhobbies.fr',
      password: 'Test1234!',
    },
    timeout: 15000,
    failOnStatusCode: false,
  });

  if (loginResponse.ok()) {
    console.log('[global-setup] Seed data verified — family user login successful.');
  } else {
    console.warn(
      `[global-setup] Seed data login check returned status ${loginResponse.status()}. ` +
      'Tests that depend on seed data may fail.'
    );
  }
});
