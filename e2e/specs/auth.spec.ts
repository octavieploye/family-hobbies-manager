// e2e/specs/auth.spec.ts
// Authentication E2E tests: register, login, logout, token refresh, error handling.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { RegisterPage } from '../pages/register.page';
import { DashboardPage } from '../pages/dashboard.page';
import { TEST_USERS } from '../fixtures/test-data';
import { ApiHelper } from '../helpers/api-helper';

test.describe('Authentication', () => {

  // -- Registration ----------------------------------------------------------

  test.describe('Registration', () => {
    let registerPage: RegisterPage;
    const apiHelper = new ApiHelper();

    test.beforeEach(async ({ page }) => {
      registerPage = new RegisterPage(page);
      await registerPage.goto();
    });

    test.afterAll(async () => {
      // Clean up the test user created during registration
      await apiHelper.init();
      await apiHelper.authenticate(
        TEST_USERS.admin.email,
        TEST_USERS.admin.password
      );
      try {
        await apiHelper.deleteTestUser(TEST_USERS.newUser.email);
      } catch {
        // User may not have been created if the test failed
      }
      await apiHelper.dispose();
    });

    test('should display the registration form', async () => {
      await registerPage.expectPageLoaded();
      await expect(registerPage.firstNameInput).toBeVisible();
      await expect(registerPage.lastNameInput).toBeVisible();
      await expect(registerPage.emailInput).toBeVisible();
      await expect(registerPage.passwordInput).toBeVisible();
      await expect(registerPage.confirmPasswordInput).toBeVisible();
      await expect(registerPage.termsCheckbox).toBeVisible();
      await expect(registerPage.submitButton).toBeVisible();
    });

    test('should register a new family user', async ({ page }) => {
      await registerPage.register({
        firstName: TEST_USERS.newUser.firstName,
        lastName: TEST_USERS.newUser.lastName,
        email: TEST_USERS.newUser.email,
        password: TEST_USERS.newUser.password,
      });

      // After registration, user should see a success message or be redirected
      const successMessage = await registerPage.getSuccessMessage();
      expect(successMessage).toBeTruthy();
      expect(successMessage).toContain('compte');
    });

    test('should show error for duplicate email', async () => {
      await registerPage.register({
        firstName: 'Doublon',
        lastName: 'Test',
        email: TEST_USERS.familyUser.email, // Already exists in seed data
        password: 'Doublon1234!',
      });

      const errorMessage = await registerPage.getErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('existe');
    });

    test('should show validation errors for empty fields', async () => {
      await registerPage.submitButton.click();

      // Form should not submit and validation errors should appear
      await expect(registerPage.page).toHaveURL(/.*\/auth\/register/);
    });

    test('should navigate to login page via link', async ({ page }) => {
      await registerPage.loginLink.click();
      await page.waitForURL('**/auth/login');
      await expect(page).toHaveURL(/.*\/auth\/login/);
    });
  });

  // -- Login -----------------------------------------------------------------

  test.describe('Login', () => {
    let loginPage: LoginPage;
    let dashboardPage: DashboardPage;

    test.beforeEach(async ({ page }) => {
      loginPage = new LoginPage(page);
      dashboardPage = new DashboardPage(page);
      await loginPage.goto();
    });

    test('should display the login form', async () => {
      await loginPage.expectPageLoaded();
      await expect(loginPage.emailInput).toBeVisible();
      await expect(loginPage.passwordInput).toBeVisible();
      await expect(loginPage.submitButton).toBeVisible();
      await expect(loginPage.registerLink).toBeVisible();
    });

    test('should login with valid family user credentials', async ({ page }) => {
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );

      await dashboardPage.expectLoggedIn();

      const welcomeText = await dashboardPage.getWelcomeText();
      expect(welcomeText).toContain(TEST_USERS.familyUser.firstName);
    });

    test('should show error for invalid credentials', async () => {
      await loginPage.fillEmail(TEST_USERS.invalidUser.email);
      await loginPage.fillPassword(TEST_USERS.invalidUser.password);
      await loginPage.submit();

      const errorMessage = await loginPage.getErrorMessage();
      expect(errorMessage).toBeTruthy();
      expect(errorMessage).toContain('incorrect');
    });

    test('should keep submit disabled when form is incomplete', async () => {
      // Only fill email, leave password empty
      await loginPage.fillEmail(TEST_USERS.familyUser.email);
      await loginPage.expectSubmitDisabled();
    });

    test('should enable submit when both fields are filled', async () => {
      await loginPage.fillEmail(TEST_USERS.familyUser.email);
      await loginPage.fillPassword(TEST_USERS.familyUser.password);
      await loginPage.expectSubmitEnabled();
    });

    test('should navigate to registration page via link', async ({ page }) => {
      await loginPage.goToRegister();
      await expect(page).toHaveURL(/.*\/auth\/register/);
    });
  });

  // -- Logout ----------------------------------------------------------------

  test.describe('Logout', () => {
    test('should logout and redirect to login page', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const dashboardPage = new DashboardPage(page);

      // Login first
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );
      await dashboardPage.expectLoggedIn();

      // Logout
      await dashboardPage.logout();

      // Should be back on login page
      await expect(page).toHaveURL(/.*\/auth\/login/);
    });

    test('should not access dashboard after logout', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const dashboardPage = new DashboardPage(page);

      // Login then logout
      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );
      await dashboardPage.logout();

      // Try to access dashboard directly
      await page.goto('/dashboard');
      await expect(page).toHaveURL(/.*\/auth\/login/);
    });
  });

  // -- Token Refresh ---------------------------------------------------------

  test.describe('Token Refresh', () => {
    test('should maintain session across page navigation', async ({ page }) => {
      const loginPage = new LoginPage(page);
      const dashboardPage = new DashboardPage(page);

      await loginPage.goto();
      await loginPage.login(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );
      await dashboardPage.expectLoggedIn();

      // Navigate to different pages and back
      await page.goto('/families');
      await page.goto('/associations/search');
      await page.goto('/dashboard');

      // Should still be logged in
      await dashboardPage.expectLoggedIn();
    });
  });

  // -- Route Guards ----------------------------------------------------------

  test.describe('Route Guards', () => {
    test('should redirect unauthenticated user to login', async ({ page }) => {
      const protectedRoutes = [
        '/dashboard',
        '/families',
        '/associations/search',
        '/subscriptions',
        '/attendance',
        '/payments',
        '/notifications',
        '/settings',
      ];

      for (const route of protectedRoutes) {
        await page.goto(route);
        await expect(page).toHaveURL(/.*\/auth\/login/);
      }
    });
  });
});
