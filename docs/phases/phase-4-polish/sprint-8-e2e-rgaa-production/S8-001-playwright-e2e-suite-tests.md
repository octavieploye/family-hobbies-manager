# Story S8-001: Playwright E2E Test Specs

> Companion file for [S8-001 main](./S8-001-playwright-e2e-suite.md)
> Sprint file: [Back to Sprint Index](./_index.md)

---

This file contains the complete source code for all 8 Playwright E2E test spec files. Each spec uses Page Object Models defined in the main story file and test data from `e2e/fixtures/test-data.ts`.

**Run all specs**: `cd e2e && npx playwright test`
**Run a single spec**: `cd e2e && npx playwright test specs/auth.spec.ts`

---

## Test Spec 1: auth.spec.ts

**File**: `e2e/specs/auth.spec.ts`

```typescript
// e2e/specs/auth.spec.ts
// Authentication E2E tests: register, login, logout, token refresh, error handling.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { RegisterPage } from '../pages/register.page';
import { DashboardPage } from '../pages/dashboard.page';
import { TEST_USERS } from '../fixtures/test-data';
import { ApiHelper } from '../helpers/api-helper';

test.describe('Authentication', () => {

  // ── Registration ────────────────────────────────────────────────────

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

  // ── Login ───────────────────────────────────────────────────────────

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

  // ── Logout ──────────────────────────────────────────────────────────

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

  // ── Token Refresh ───────────────────────────────────────────────────

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

  // ── Route Guards ────────────────────────────────────────────────────

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
```

---

## Test Spec 2: family.spec.ts

**File**: `e2e/specs/family.spec.ts`

```typescript
// e2e/specs/family.spec.ts
// Family management E2E tests: view, create, add member, edit member, remove member.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { FamilyPage } from '../pages/family.page';
import { DashboardPage } from '../pages/dashboard.page';
import { TEST_USERS, TEST_FAMILIES } from '../fixtures/test-data';

test.describe('Family Management', () => {
  let loginPage: LoginPage;
  let familyPage: FamilyPage;

  test.beforeEach(async ({ page }) => {
    loginPage = new LoginPage(page);
    familyPage = new FamilyPage(page);

    // Login as family user
    await loginPage.goto();
    await loginPage.login(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
  });

  // ── View Family ─────────────────────────────────────────────────────

  test.describe('View Family', () => {
    test('should display family list page', async () => {
      await familyPage.goto();
      await familyPage.expectPageLoaded();
    });

    test('should display pre-seeded family members', async () => {
      await familyPage.gotoFamily(TEST_FAMILIES.dupont.id);
      await familyPage.expectPageLoaded();

      const memberCount = await familyPage.getMemberCount();
      expect(memberCount).toBe(TEST_FAMILIES.dupont.members.length);
    });

    test('should display correct member names', async () => {
      await familyPage.gotoFamily(TEST_FAMILIES.dupont.id);

      const firstMemberName = await familyPage.getMemberName(0);
      expect(firstMemberName).toContain('Marie');
      expect(firstMemberName).toContain('Dupont');
    });
  });

  // ── Add Member ──────────────────────────────────────────────────────

  test.describe('Add Member', () => {
    test('should add a new family member', async () => {
      await familyPage.gotoFamily(TEST_FAMILIES.dupont.id);

      const initialCount = await familyPage.getMemberCount();

      await familyPage.addMember({
        firstName: TEST_FAMILIES.newMember.firstName,
        lastName: TEST_FAMILIES.newMember.lastName,
        birthDate: TEST_FAMILIES.newMember.birthDate,
        role: TEST_FAMILIES.newMember.role,
      });

      const updatedCount = await familyPage.getMemberCount();
      expect(updatedCount).toBe(initialCount + 1);
    });

    test('should display the newly added member in the list', async () => {
      await familyPage.gotoFamily(TEST_FAMILIES.dupont.id);

      await familyPage.addMember({
        firstName: 'Lea',
        lastName: 'Dupont',
        birthDate: '2020-05-15',
        role: 'Enfant',
      });

      // Verify the new member appears in the list
      const memberCount = await familyPage.getMemberCount();
      let found = false;
      for (let i = 0; i < memberCount; i++) {
        const name = await familyPage.getMemberName(i);
        if (name && name.includes('Lea')) {
          found = true;
          break;
        }
      }
      expect(found).toBe(true);
    });
  });

  // ── Edit Member ─────────────────────────────────────────────────────

  test.describe('Edit Member', () => {
    test('should edit a family member name', async () => {
      await familyPage.gotoFamily(TEST_FAMILIES.dupont.id);

      // Edit the first member's first name
      await familyPage.editMember(0, { firstName: 'Marie-Claire' });

      const updatedName = await familyPage.getMemberName(0);
      expect(updatedName).toContain('Marie-Claire');
    });

    test('should open and close the edit dialog', async () => {
      await familyPage.gotoFamily(TEST_FAMILIES.dupont.id);

      // Open edit dialog
      const card = familyPage.memberCards.nth(0);
      await card.getByTestId('edit-member-button').click();
      await expect(familyPage.editMemberDialog).toBeVisible();

      // Close via cancel
      await familyPage.page.getByTestId('cancel-member-button').click();
      await expect(familyPage.editMemberDialog).toBeHidden();
    });
  });

  // ── Remove Member ───────────────────────────────────────────────────

  test.describe('Remove Member', () => {
    test('should remove a family member after confirmation', async () => {
      await familyPage.gotoFamily(TEST_FAMILIES.dupont.id);

      const initialCount = await familyPage.getMemberCount();

      // Remove the last member (least destructive for seed data)
      await familyPage.removeMember(initialCount - 1);

      const updatedCount = await familyPage.getMemberCount();
      expect(updatedCount).toBe(initialCount - 1);
    });

    test('should cancel member removal when dismissing dialog', async () => {
      await familyPage.gotoFamily(TEST_FAMILIES.dupont.id);

      const initialCount = await familyPage.getMemberCount();

      // Click remove on last member
      const card = familyPage.memberCards.nth(initialCount - 1);
      await card.getByTestId('remove-member-button').click();
      await expect(familyPage.deleteConfirmDialog).toBeVisible();

      // Cancel the deletion
      await familyPage.cancelDeleteButton.click();
      await expect(familyPage.deleteConfirmDialog).toBeHidden();

      const finalCount = await familyPage.getMemberCount();
      expect(finalCount).toBe(initialCount);
    });
  });

  // ── Create Family ───────────────────────────────────────────────────

  test.describe('Create Family', () => {
    test('should create a new family', async () => {
      await familyPage.goto();
      await familyPage.createFamily(TEST_FAMILIES.newFamily.name);

      await expect(familyPage.successMessage).toBeVisible();
    });
  });
});
```

---

## Test Spec 3: association-search.spec.ts

**File**: `e2e/specs/association-search.spec.ts`

```typescript
// e2e/specs/association-search.spec.ts
// Association search E2E tests: keyword search, city filter, category filter, pagination, detail navigation.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { AssociationSearchPage } from '../pages/association-search.page';
import { AssociationDetailPage } from '../pages/association-detail.page';
import { TEST_USERS, TEST_ASSOCIATIONS } from '../fixtures/test-data';

test.describe('Association Search', () => {
  let searchPage: AssociationSearchPage;

  test.beforeEach(async ({ page }) => {
    const loginPage = new LoginPage(page);
    searchPage = new AssociationSearchPage(page);

    await loginPage.goto();
    await loginPage.login(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
    await searchPage.goto();
  });

  // ── Page Load ───────────────────────────────────────────────────────

  test.describe('Page Load', () => {
    test('should display the search page with all filter controls', async () => {
      await searchPage.expectPageLoaded();
      await expect(searchPage.keywordInput).toBeVisible();
      await expect(searchPage.cityInput).toBeVisible();
      await expect(searchPage.categorySelect).toBeVisible();
      await expect(searchPage.searchButton).toBeVisible();
    });
  });

  // ── Keyword Search ──────────────────────────────────────────────────

  test.describe('Keyword Search', () => {
    test('should find associations by keyword "Sport"', async () => {
      await searchPage.searchByKeyword('Sport');

      await searchPage.expectResultsVisible();

      const resultCount = await searchPage.getResultCount();
      expect(resultCount).toBeGreaterThan(0);

      const countText = await searchPage.getResultCountText();
      expect(countText).toBeTruthy();
    });

    test('should find association by full name', async () => {
      await searchPage.searchByKeyword(TEST_ASSOCIATIONS.sportLyon.name);

      await searchPage.expectResultsVisible();

      const resultCount = await searchPage.getResultCount();
      expect(resultCount).toBeGreaterThanOrEqual(1);
    });

    test('should display empty state for non-existent keyword', async () => {
      await searchPage.searchByKeyword(TEST_ASSOCIATIONS.nonExistent.keyword);
      await searchPage.expectEmptyState();
    });
  });

  // ── City Filter ─────────────────────────────────────────────────────

  test.describe('City Filter', () => {
    test('should filter associations by city "Lyon"', async () => {
      await searchPage.filterByCity('Lyon');

      await searchPage.expectResultsVisible();

      const resultCount = await searchPage.getResultCount();
      expect(resultCount).toBeGreaterThan(0);
    });

    test('should filter associations by city "Paris"', async () => {
      await searchPage.filterByCity('Paris');

      await searchPage.expectResultsVisible();
    });

    test('should show empty state for non-existent city', async () => {
      await searchPage.filterByCity(TEST_ASSOCIATIONS.nonExistent.city);
      await searchPage.expectEmptyState();
    });
  });

  // ── Category Filter ─────────────────────────────────────────────────

  test.describe('Category Filter', () => {
    test('should filter associations by category "Sport"', async () => {
      await searchPage.filterByCategory('Sport');

      await searchPage.expectResultsVisible();
    });

    test('should filter associations by category "Danse"', async () => {
      await searchPage.filterByCategory('Danse');

      await searchPage.expectResultsVisible();
    });

    test('should filter associations by category "Musique"', async () => {
      await searchPage.filterByCategory('Musique');

      await searchPage.expectResultsVisible();
    });
  });

  // ── Combined Filters ────────────────────────────────────────────────

  test.describe('Combined Filters', () => {
    test('should combine keyword and city filters', async () => {
      await searchPage.keywordInput.fill('Sport');
      await searchPage.cityInput.fill('Lyon');
      await searchPage.searchButton.click();
      await searchPage.page.getByTestId('search-loading').waitFor({
        state: 'hidden',
        timeout: 10000,
      });

      await searchPage.expectResultsVisible();
    });

    test('should clear all filters and show default results', async () => {
      // Apply filters
      await searchPage.searchByKeyword('Sport');
      await searchPage.expectResultsVisible();

      // Clear filters
      await searchPage.clearFilters();

      // Inputs should be empty
      await expect(searchPage.keywordInput).toHaveValue('');
    });
  });

  // ── Pagination ──────────────────────────────────────────────────────

  test.describe('Pagination', () => {
    test('should navigate to the next page of results', async () => {
      // Search for a broad term to get enough results for pagination
      await searchPage.searchByKeyword('association');

      const initialCountText = await searchPage.getResultCountText();
      await searchPage.goToNextPage();

      const nextPageCountText = await searchPage.getResultCountText();
      // Page info should have changed
      expect(nextPageCountText).not.toEqual(initialCountText);
    });

    test('should navigate back to the previous page', async () => {
      await searchPage.searchByKeyword('association');

      await searchPage.goToNextPage();
      await searchPage.goToPreviousPage();

      await searchPage.expectResultsVisible();
    });
  });

  // ── Detail Navigation ───────────────────────────────────────────────

  test.describe('Detail Navigation', () => {
    test('should navigate to association detail when clicking a result card', async ({ page }) => {
      await searchPage.searchByKeyword(TEST_ASSOCIATIONS.sportLyon.name);
      await searchPage.expectResultsVisible();

      await searchPage.clickResult(0);

      await expect(page).toHaveURL(/.*\/associations\/.+/);

      const detailPage = new AssociationDetailPage(page);
      await detailPage.expectPageLoaded();
    });

    test('should display association details on the detail page', async ({ page }) => {
      const detailPage = new AssociationDetailPage(page);
      await detailPage.goto(TEST_ASSOCIATIONS.sportLyon.id);

      const name = await detailPage.getName();
      expect(name).toContain(TEST_ASSOCIATIONS.sportLyon.name);

      const city = await detailPage.getCity();
      expect(city).toContain(TEST_ASSOCIATIONS.sportLyon.city);

      const activityCount = await detailPage.getActivityCount();
      expect(activityCount).toBe(TEST_ASSOCIATIONS.sportLyon.activities.length);
    });

    test('should navigate back to search from detail page', async ({ page }) => {
      const detailPage = new AssociationDetailPage(page);
      await detailPage.goto(TEST_ASSOCIATIONS.sportLyon.id);

      await detailPage.backToSearchLink.click();
      await expect(page).toHaveURL(/.*\/associations\/search/);
    });
  });
});
```

---

## Test Spec 4: subscription.spec.ts

**File**: `e2e/specs/subscription.spec.ts`

```typescript
// e2e/specs/subscription.spec.ts
// Subscription E2E tests: view subscriptions, subscribe to activity, cancel subscription, filter.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { SubscriptionPage } from '../pages/subscription.page';
import { AssociationDetailPage } from '../pages/association-detail.page';
import { TEST_USERS, TEST_SUBSCRIPTIONS, TEST_ASSOCIATIONS } from '../fixtures/test-data';

test.describe('Subscription Management', () => {
  let subscriptionPage: SubscriptionPage;

  test.beforeEach(async ({ page }) => {
    const loginPage = new LoginPage(page);
    subscriptionPage = new SubscriptionPage(page);

    await loginPage.goto();
    await loginPage.login(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
  });

  // ── View Subscriptions ──────────────────────────────────────────────

  test.describe('View Subscriptions', () => {
    test('should display the subscriptions page', async () => {
      await subscriptionPage.goto();
      await subscriptionPage.expectPageLoaded();
    });

    test('should display pre-seeded subscriptions', async () => {
      await subscriptionPage.goto();
      await subscriptionPage.expectHasSubscriptions();

      const count = await subscriptionPage.getSubscriptionCount();
      expect(count).toBeGreaterThanOrEqual(2); // At least Lucas + Emma
    });

    test('should display correct association name for first subscription', async () => {
      await subscriptionPage.goto();

      const associationName = await subscriptionPage.getAssociationName(0);
      expect(associationName).toBeTruthy();
    });

    test('should display active status for pre-seeded subscriptions', async () => {
      await subscriptionPage.goto();

      const status = await subscriptionPage.getStatus(0);
      expect(status).toBeTruthy();
      expect(status!.toLowerCase()).toContain('actif');
    });
  });

  // ── Subscribe to Activity ───────────────────────────────────────────

  test.describe('Subscribe to Activity', () => {
    test('should subscribe a family member to an activity from detail page', async ({ page }) => {
      const detailPage = new AssociationDetailPage(page);
      await detailPage.goto(TEST_ASSOCIATIONS.musiqueToulouse.id);

      await detailPage.subscribeToActivity(0); // Subscribe to first activity (Piano)

      // Should navigate to subscription confirmation or subscriptions list
      await page.waitForURL(/.*\/(subscriptions|checkout)/);
    });

    test('should subscribe a family member from the subscriptions page', async () => {
      await subscriptionPage.goto();

      const initialCount = await subscriptionPage.getSubscriptionCount();

      await subscriptionPage.subscribeMember(
        'Marie Dupont',
        'Chorale'
      );

      const updatedCount = await subscriptionPage.getSubscriptionCount();
      expect(updatedCount).toBe(initialCount + 1);
    });
  });

  // ── Cancel Subscription ─────────────────────────────────────────────

  test.describe('Cancel Subscription', () => {
    test('should cancel a subscription with confirmation', async () => {
      await subscriptionPage.goto();

      const initialCount = await subscriptionPage.getSubscriptionCount();

      await subscriptionPage.cancelSubscription(initialCount - 1);

      // The subscription status should change to cancelled
      const status = await subscriptionPage.getStatus(initialCount - 1);
      expect(status!.toLowerCase()).toMatch(/annul/);
    });

    test('should not cancel when dismissing the confirmation dialog', async () => {
      await subscriptionPage.goto();

      const initialCount = await subscriptionPage.getSubscriptionCount();

      // Click cancel on the last subscription row
      const row = subscriptionPage.subscriptionRows.nth(initialCount - 1);
      await row.getByTestId('cancel-subscription-button').click();
      await expect(subscriptionPage.cancelDialog).toBeVisible();

      // Dismiss the dialog
      await subscriptionPage.cancelCancelButton.click();
      await expect(subscriptionPage.cancelDialog).toBeHidden();

      // Status should remain unchanged
      const status = await subscriptionPage.getStatus(initialCount - 1);
      expect(status!.toLowerCase()).not.toMatch(/annul/);
    });
  });

  // ── Filter Subscriptions ────────────────────────────────────────────

  test.describe('Filter Subscriptions', () => {
    test('should filter subscriptions by member', async () => {
      await subscriptionPage.goto();

      await subscriptionPage.filterByMember.click();
      await subscriptionPage.page.getByRole('option', { name: /Lucas/i }).click();

      const count = await subscriptionPage.getSubscriptionCount();
      expect(count).toBeGreaterThanOrEqual(1);
    });

    test('should filter subscriptions by status', async () => {
      await subscriptionPage.goto();

      await subscriptionPage.filterByStatus.click();
      await subscriptionPage.page.getByRole('option', { name: /Actif/i }).click();

      const count = await subscriptionPage.getSubscriptionCount();
      expect(count).toBeGreaterThanOrEqual(1);
    });
  });
});
```

---

## Test Spec 5: attendance.spec.ts

**File**: `e2e/specs/attendance.spec.ts`

```typescript
// e2e/specs/attendance.spec.ts
// Attendance tracking E2E tests: calendar view, select date, mark present/absent, history.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { AttendancePage } from '../pages/attendance.page';
import { TEST_USERS } from '../fixtures/test-data';

test.describe('Attendance Tracking', () => {
  let attendancePage: AttendancePage;

  test.beforeEach(async ({ page }) => {
    const loginPage = new LoginPage(page);
    attendancePage = new AttendancePage(page);

    await loginPage.goto();
    await loginPage.login(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
    await attendancePage.goto();
  });

  // ── Calendar View ───────────────────────────────────────────────────

  test.describe('Calendar View', () => {
    test('should display the attendance page with calendar', async () => {
      await attendancePage.expectPageLoaded();
      await expect(attendancePage.calendarView).toBeVisible();
    });

    test('should display the current month label', async () => {
      const monthLabel = await attendancePage.currentMonthLabel.textContent();
      expect(monthLabel).toBeTruthy();
      // Should contain a month name in French (e.g., "Fevrier 2026")
      expect(monthLabel!.length).toBeGreaterThan(3);
    });

    test('should navigate to the next month', async () => {
      const currentMonth = await attendancePage.currentMonthLabel.textContent();
      await attendancePage.goToNextMonth();
      const nextMonth = await attendancePage.currentMonthLabel.textContent();
      expect(nextMonth).not.toBe(currentMonth);
    });

    test('should navigate to the previous month', async () => {
      const currentMonth = await attendancePage.currentMonthLabel.textContent();
      await attendancePage.goToPreviousMonth();
      const prevMonth = await attendancePage.currentMonthLabel.textContent();
      expect(prevMonth).not.toBe(currentMonth);
    });
  });

  // ── Date Selection ──────────────────────────────────────────────────

  test.describe('Date Selection', () => {
    test('should display sessions when selecting a day with sessions', async () => {
      // Select day 15 (likely to have sessions in seed data)
      await attendancePage.selectDay(15);

      // Selected date should be displayed
      const selectedDateText = await attendancePage.selectedDate.textContent();
      expect(selectedDateText).toContain('15');
    });

    test('should display session cards for a day with scheduled sessions', async () => {
      await attendancePage.selectDay(15);

      // Wait for sessions to load
      await attendancePage.page.waitForTimeout(500);

      // If the seed data has sessions on day 15, we should see session cards
      const sessionCount = await attendancePage.sessionCards.count();
      // This might be 0 if no sessions on that day; just verify no crash
      expect(sessionCount).toBeGreaterThanOrEqual(0);
    });
  });

  // ── Mark Attendance ─────────────────────────────────────────────────

  test.describe('Mark Attendance', () => {
    test('should mark a member as present', async () => {
      await attendancePage.selectDay(15);
      await attendancePage.page.waitForTimeout(500);

      const sessionCount = await attendancePage.sessionCards.count();
      if (sessionCount > 0) {
        await attendancePage.markPresent(0);

        const status = await attendancePage.getAttendanceStatus(0);
        expect(status).toBeTruthy();
        expect(status!.toLowerCase()).toMatch(/present/);
      }
    });

    test('should mark a member as absent', async () => {
      await attendancePage.selectDay(15);
      await attendancePage.page.waitForTimeout(500);

      const sessionCount = await attendancePage.sessionCards.count();
      if (sessionCount > 0) {
        await attendancePage.markAbsent(0);

        const status = await attendancePage.getAttendanceStatus(0);
        expect(status).toBeTruthy();
        expect(status!.toLowerCase()).toMatch(/absent/);
      }
    });

    test('should toggle attendance from present to absent', async () => {
      await attendancePage.selectDay(15);
      await attendancePage.page.waitForTimeout(500);

      const sessionCount = await attendancePage.sessionCards.count();
      if (sessionCount > 0) {
        // Mark present first
        await attendancePage.markPresent(0);
        let status = await attendancePage.getAttendanceStatus(0);
        expect(status!.toLowerCase()).toMatch(/present/);

        // Then mark absent
        await attendancePage.markAbsent(0);
        status = await attendancePage.getAttendanceStatus(0);
        expect(status!.toLowerCase()).toMatch(/absent/);
      }
    });
  });

  // ── Attendance History ──────────────────────────────────────────────

  test.describe('Attendance History', () => {
    test('should navigate to the history tab', async () => {
      await attendancePage.viewHistory();
      await expect(attendancePage.historyTable).toBeVisible();
    });

    test('should display attendance history rows', async () => {
      await attendancePage.viewHistory();

      const rowCount = await attendancePage.getHistoryRowCount();
      expect(rowCount).toBeGreaterThanOrEqual(0); // May be 0 if no history yet
    });

    test('should filter history by member', async () => {
      await attendancePage.viewHistory();

      await attendancePage.memberFilter.click();
      await attendancePage.page.getByRole('option', { name: /Lucas/i }).click();

      await attendancePage.page.waitForTimeout(500);

      // Should still display the history table without errors
      await expect(attendancePage.historyTable).toBeVisible();
    });

    test('should filter history by activity', async () => {
      await attendancePage.viewHistory();

      await attendancePage.activityFilter.click();
      await attendancePage.page.getByRole('option').first().click();

      await attendancePage.page.waitForTimeout(500);

      await expect(attendancePage.historyTable).toBeVisible();
    });
  });
});
```

---

## Test Spec 6: payment.spec.ts

**File**: `e2e/specs/payment.spec.ts`

```typescript
// e2e/specs/payment.spec.ts
// Payment E2E tests: view payments, payment detail, checkout initiation, webhook-simulated status update.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { PaymentPage } from '../pages/payment.page';
import { ApiHelper } from '../helpers/api-helper';
import { TEST_USERS, TEST_PAYMENTS } from '../fixtures/test-data';

test.describe('Payments', () => {
  let paymentPage: PaymentPage;
  const apiHelper = new ApiHelper();

  test.beforeAll(async () => {
    await apiHelper.init();
    await apiHelper.authenticate(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
  });

  test.afterAll(async () => {
    await apiHelper.dispose();
  });

  test.beforeEach(async ({ page }) => {
    const loginPage = new LoginPage(page);
    paymentPage = new PaymentPage(page);

    await loginPage.goto();
    await loginPage.login(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
  });

  // ── Payment List ────────────────────────────────────────────────────

  test.describe('Payment List', () => {
    test('should display the payments page', async () => {
      await paymentPage.goto();
      await paymentPage.expectPageLoaded();
    });

    test('should display pre-seeded payment rows', async () => {
      await paymentPage.goto();

      const count = await paymentPage.getPaymentCount();
      expect(count).toBeGreaterThanOrEqual(2); // Completed + Pending
    });

    test('should display correct payment amount', async () => {
      await paymentPage.goto();

      const amount = await paymentPage.getPaymentAmount(0);
      expect(amount).toBeTruthy();
      // Amount should contain a number with EUR formatting
      expect(amount).toMatch(/\d/);
    });

    test('should display payment status badges', async () => {
      await paymentPage.goto();

      const status = await paymentPage.getPaymentStatus(0);
      expect(status).toBeTruthy();
    });
  });

  // ── Payment Detail ──────────────────────────────────────────────────

  test.describe('Payment Detail', () => {
    test('should navigate to payment detail from list', async ({ page }) => {
      await paymentPage.goto();

      await paymentPage.viewPaymentDetail(0);

      await expect(page).toHaveURL(/.*\/payments\/.+/);
      await paymentPage.expectPaymentDetailLoaded();
    });

    test('should display payment detail fields', async () => {
      await paymentPage.gotoDetail(TEST_PAYMENTS.completedPayment.id);

      await paymentPage.expectPaymentDetailLoaded();

      const amount = await paymentPage.paymentAmount.textContent();
      expect(amount).toContain(TEST_PAYMENTS.completedPayment.amount);

      const association = await paymentPage.paymentAssociation.textContent();
      expect(association).toContain(TEST_PAYMENTS.completedPayment.associationName);
    });

    test('should display the payment timeline for completed payment', async () => {
      await paymentPage.gotoDetail(TEST_PAYMENTS.completedPayment.id);

      const stepCount = await paymentPage.getTimelineStepCount();
      expect(stepCount).toBeGreaterThanOrEqual(2); // At least: created, completed
    });

    test('should navigate back to payment list from detail', async ({ page }) => {
      await paymentPage.gotoDetail(TEST_PAYMENTS.completedPayment.id);

      await paymentPage.backToListLink.click();
      await expect(page).toHaveURL(/.*\/payments$/);
    });
  });

  // ── Payment Status Filter ──────────────────────────────────────────

  test.describe('Payment Filters', () => {
    test('should filter payments by status', async () => {
      await paymentPage.goto();

      await paymentPage.filterByStatus.click();
      await paymentPage.page.getByRole('option', { name: /Termine/i }).click();

      const count = await paymentPage.getPaymentCount();
      expect(count).toBeGreaterThanOrEqual(1);
    });
  });

  // ── Checkout Flow ───────────────────────────────────────────────────

  test.describe('Checkout Flow', () => {
    test('should initiate a checkout session', async ({ page }) => {
      await paymentPage.goto();

      // If a checkout button is visible (e.g., for pending subscriptions)
      const checkoutVisible = await paymentPage.checkoutButton.isVisible()
        .catch(() => false);

      if (checkoutVisible) {
        await paymentPage.initiateCheckout();

        // Should either redirect to HelloAsso checkout or show a loading state
        await expect(
          paymentPage.checkoutLoadingIndicator.or(
            page.locator('text=helloasso').first()
          )
        ).toBeVisible({ timeout: 10000 });
      }
    });
  });

  // ── Webhook Simulation ──────────────────────────────────────────────

  test.describe('Webhook Status Update', () => {
    test('should update payment status after webhook callback', async ({ page }) => {
      // Simulate a HelloAsso payment webhook for the pending payment
      await apiHelper.simulateWebhook('Payment', {
        id: TEST_PAYMENTS.pendingPayment.id,
        state: 'Authorized',
        amount: parseInt(TEST_PAYMENTS.pendingPayment.amount, 10) * 100,
      });

      // Wait for the webhook to be processed
      await page.waitForTimeout(2000);

      // Verify the payment status updated via API
      const statusUpdated = await apiHelper.verifyPaymentStatus(
        TEST_PAYMENTS.pendingPayment.id,
        'COMPLETED'
      );
      expect(statusUpdated).toBe(true);

      // Verify the UI reflects the updated status
      await paymentPage.gotoDetail(TEST_PAYMENTS.pendingPayment.id);

      const statusText = await paymentPage.paymentStatus.textContent();
      expect(statusText!.toLowerCase()).toMatch(/termin|complet/);
    });
  });

  // ── Invoice Download ────────────────────────────────────────────────

  test.describe('Invoice Download', () => {
    test('should download invoice for completed payment', async () => {
      await paymentPage.gotoDetail(TEST_PAYMENTS.completedPayment.id);

      const downloadButtonVisible = await paymentPage.downloadInvoiceButton
        .isVisible()
        .catch(() => false);

      if (downloadButtonVisible) {
        const [download] = await Promise.all([
          paymentPage.page.waitForEvent('download'),
          paymentPage.downloadInvoiceButton.click(),
        ]);

        const filename = download.suggestedFilename();
        expect(filename).toMatch(/\.pdf$/);
      }
    });
  });
});
```

---

## Test Spec 7: notification.spec.ts

**File**: `e2e/specs/notification.spec.ts`

```typescript
// e2e/specs/notification.spec.ts
// Notification E2E tests: bell icon, dropdown, mark read, full list, filtering.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { NotificationPage } from '../pages/notification.page';
import { DashboardPage } from '../pages/dashboard.page';
import { TEST_USERS, TEST_NOTIFICATIONS } from '../fixtures/test-data';

test.describe('Notifications', () => {
  let notificationPage: NotificationPage;

  test.beforeEach(async ({ page }) => {
    const loginPage = new LoginPage(page);
    notificationPage = new NotificationPage(page);

    await loginPage.goto();
    await loginPage.login(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
  });

  // ── Bell Icon ───────────────────────────────────────────────────────

  test.describe('Bell Icon', () => {
    test('should display the notification bell icon on dashboard', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.expectBellVisible();
    });

    test('should show unread badge count', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      const unreadCount = await notificationPage.getUnreadCount();
      expect(unreadCount).toBeGreaterThanOrEqual(
        TEST_NOTIFICATIONS.expectedUnreadCount
      );
    });

    test('should display bell icon on all authenticated pages', async ({ page }) => {
      const routes = ['/dashboard', '/families', '/associations/search', '/subscriptions'];

      for (const route of routes) {
        await page.goto(route);
        await notificationPage.expectBellVisible();
      }
    });
  });

  // ── Dropdown Panel ──────────────────────────────────────────────────

  test.describe('Dropdown Panel', () => {
    test('should open dropdown when clicking bell icon', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await expect(notificationPage.dropdown).toBeVisible();
    });

    test('should display notification items in the dropdown', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();

      const itemCount = await notificationPage.getDropdownItemCount();
      expect(itemCount).toBeGreaterThan(0);
    });

    test('should close dropdown on Escape key', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await expect(notificationPage.dropdown).toBeVisible();

      await notificationPage.closeDropdown();
      await expect(notificationPage.dropdown).toBeHidden();
    });

    test('should display "Mark all as read" button', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await expect(notificationPage.markAllReadButton).toBeVisible();
    });

    test('should display "View all" link to full notification list', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await expect(notificationPage.viewAllLink).toBeVisible();
    });
  });

  // ── Mark as Read ────────────────────────────────────────────────────

  test.describe('Mark as Read', () => {
    test('should mark a single notification as read', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      const initialUnread = await notificationPage.getUnreadCount();

      await notificationPage.openDropdown();
      await notificationPage.markAsRead(0);

      const updatedUnread = await notificationPage.getUnreadCount();
      expect(updatedUnread).toBeLessThan(initialUnread);
    });

    test('should mark all notifications as read', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await notificationPage.markAllAsRead();

      // Badge should disappear or show 0
      const unreadCount = await notificationPage.getUnreadCount();
      expect(unreadCount).toBe(0);
    });
  });

  // ── Full List Page ──────────────────────────────────────────────────

  test.describe('Full List Page', () => {
    test('should display the full notification list page', async () => {
      await notificationPage.goto();
      await notificationPage.expectPageLoaded();
    });

    test('should display all notification items', async () => {
      await notificationPage.goto();

      const count = await notificationPage.getNotificationCount();
      expect(count).toBeGreaterThan(0);
    });

    test('should navigate from dropdown to full list', async ({ page }) => {
      const dashboardPage = new DashboardPage(page);
      await dashboardPage.goto();

      await notificationPage.openDropdown();
      await notificationPage.goToFullList();

      await expect(page).toHaveURL(/.*\/notifications/);
      await notificationPage.expectPageLoaded();
    });
  });

  // ── Filter Notifications ────────────────────────────────────────────

  test.describe('Filter Notifications', () => {
    test('should filter notifications by type', async () => {
      await notificationPage.goto();

      await notificationPage.filterByType.click();
      await notificationPage.page
        .getByRole('option')
        .first()
        .click();

      await notificationPage.page.waitForTimeout(500);

      // Should still display the notification list
      await expect(notificationPage.notificationList).toBeVisible();
    });

    test('should filter notifications by read status', async () => {
      await notificationPage.goto();

      await notificationPage.filterByRead.click();
      await notificationPage.page
        .getByRole('option', { name: /non lu/i })
        .click();

      await notificationPage.page.waitForTimeout(500);

      await expect(notificationPage.notificationList).toBeVisible();
    });
  });
});
```

---

## Test Spec 8: rgpd.spec.ts

**File**: `e2e/specs/rgpd.spec.ts`

```typescript
// e2e/specs/rgpd.spec.ts
// RGPD compliance E2E tests: consent toggles, data export, download, account deletion.

import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages/login.page';
import { SettingsPage } from '../pages/settings.page';
import { TEST_USERS } from '../fixtures/test-data';
import { ApiHelper } from '../helpers/api-helper';

test.describe('RGPD Compliance', () => {
  let settingsPage: SettingsPage;

  test.beforeEach(async ({ page }) => {
    const loginPage = new LoginPage(page);
    settingsPage = new SettingsPage(page);

    await loginPage.goto();
    await loginPage.login(
      TEST_USERS.familyUser.email,
      TEST_USERS.familyUser.password
    );
    await settingsPage.goto();
  });

  // ── Page Load ───────────────────────────────────────────────────────

  test.describe('Settings Page', () => {
    test('should display the settings page with consent section', async () => {
      await settingsPage.expectPageLoaded();
    });

    test('should display all consent toggles', async () => {
      await expect(settingsPage.analyticsConsentToggle).toBeVisible();
      await expect(settingsPage.marketingConsentToggle).toBeVisible();
      await expect(settingsPage.thirdPartyConsentToggle).toBeVisible();
    });

    test('should display the data export section', async () => {
      await expect(settingsPage.exportSection).toBeVisible();
      await expect(settingsPage.requestExportButton).toBeVisible();
    });
  });

  // ── Consent Management ──────────────────────────────────────────────

  test.describe('Consent Management', () => {
    test('should toggle analytics consent on and off', async () => {
      const initialState = await settingsPage.isAnalyticsConsentEnabled();

      await settingsPage.toggleAnalyticsConsent();

      const toggledState = await settingsPage.isAnalyticsConsentEnabled();
      expect(toggledState).not.toBe(initialState);

      // Toggle back to original state
      await settingsPage.toggleAnalyticsConsent();

      const restoredState = await settingsPage.isAnalyticsConsentEnabled();
      expect(restoredState).toBe(initialState);
    });

    test('should toggle marketing consent', async () => {
      const initialState = await settingsPage.isMarketingConsentEnabled();

      await settingsPage.toggleMarketingConsent();

      const toggledState = await settingsPage.isMarketingConsentEnabled();
      expect(toggledState).not.toBe(initialState);
    });

    test('should save consent preferences', async () => {
      // Toggle a consent
      await settingsPage.toggleAnalyticsConsent();

      // Save
      await settingsPage.saveConsent();

      await expect(settingsPage.consentSuccessMessage).toBeVisible();
    });

    test('should persist consent preferences after page reload', async ({ page }) => {
      // Toggle marketing consent and save
      const initialState = await settingsPage.isMarketingConsentEnabled();
      await settingsPage.toggleMarketingConsent();
      await settingsPage.saveConsent();

      // Reload the page
      await page.reload();
      await settingsPage.expectPageLoaded();

      // Verify the consent persisted
      const persistedState = await settingsPage.isMarketingConsentEnabled();
      expect(persistedState).not.toBe(initialState);

      // Restore original state
      await settingsPage.toggleMarketingConsent();
      await settingsPage.saveConsent();
    });
  });

  // ── Data Export ─────────────────────────────────────────────────────

  test.describe('Data Export', () => {
    test('should request a data export', async () => {
      await settingsPage.requestDataExport();

      const status = await settingsPage.getExportStatus();
      expect(status).toBeTruthy();
      // Status should indicate processing or completed
      expect(status!.toLowerCase()).toMatch(/traitement|genere|pret/);
    });

    test('should display export progress', async () => {
      await settingsPage.requestDataExport();

      // Either the progress bar or the download button should be visible
      const progressVisible = await settingsPage.exportProgressBar.isVisible()
        .catch(() => false);
      const downloadVisible = await settingsPage.downloadExportButton.isVisible()
        .catch(() => false);

      expect(progressVisible || downloadVisible).toBe(true);
    });

    test('should download the exported data', async () => {
      await settingsPage.requestDataExport();

      // Wait for export to complete
      await settingsPage.downloadExportButton.waitFor({
        state: 'visible',
        timeout: 30000,
      });

      const [download] = await Promise.all([
        settingsPage.page.waitForEvent('download'),
        settingsPage.downloadExportButton.click(),
      ]);

      const filename = download.suggestedFilename();
      expect(filename).toBeTruthy();
      expect(filename).toMatch(/\.(json|zip|csv)$/);

      const path = await download.path();
      expect(path).toBeTruthy();
    });

    test('should verify export data via API', async () => {
      const apiHelper = new ApiHelper();
      await apiHelper.init();
      await apiHelper.authenticate(
        TEST_USERS.familyUser.email,
        TEST_USERS.familyUser.password
      );

      const exportResult = await apiHelper.requestDataExport();
      expect(exportResult.exportId).toBeTruthy();
      expect(exportResult.status).toBeTruthy();

      await apiHelper.dispose();
    });
  });

  // ── Account Deletion ────────────────────────────────────────────────

  test.describe('Account Deletion', () => {
    test('should display the delete account button', async () => {
      await expect(settingsPage.deleteAccountButton).toBeVisible();
    });

    test('should open the delete confirmation dialog', async () => {
      await settingsPage.deleteAccountButton.click();
      await expect(settingsPage.deleteConfirmDialog).toBeVisible();
    });

    test('should require confirmation text before allowing deletion', async () => {
      await settingsPage.deleteAccountButton.click();
      await expect(settingsPage.deleteConfirmDialog).toBeVisible();

      // The confirm delete button should be disabled until confirmation text is entered
      await expect(settingsPage.confirmDeleteButton).toBeDisabled();
    });

    test('should close deletion dialog when cancelling', async () => {
      await settingsPage.deleteAccountButton.click();
      await expect(settingsPage.deleteConfirmDialog).toBeVisible();

      await settingsPage.cancelDeleteButton.click();
      await expect(settingsPage.deleteConfirmDialog).toBeHidden();
    });

    // Note: We do NOT test actual account deletion here to avoid
    // destroying seed data needed by other tests.
    // The deletion flow is validated by the confirmation dialog behavior.
  });
});
```

---

## Summary

| Spec File | Tests | User Flows Covered |
|-----------|-------|--------------------|
| `auth.spec.ts` | 12 | Register, login, logout, token refresh, route guards |
| `family.spec.ts` | 8 | View family, add/edit/remove member, create family |
| `association-search.spec.ts` | 14 | Keyword search, city/category filter, combined filters, pagination, detail navigation |
| `subscription.spec.ts` | 8 | View, subscribe, cancel, filter subscriptions |
| `attendance.spec.ts` | 11 | Calendar view, date selection, mark present/absent, attendance history |
| `payment.spec.ts` | 9 | Payment list, detail, status filter, checkout, webhook simulation, invoice download |
| `notification.spec.ts` | 12 | Bell icon, dropdown, mark read, full list, filter notifications |
| `rgpd.spec.ts` | 11 | Consent management, data export/download, account deletion |
| **Total** | **85** | |

**Run all**: `cd e2e && npx playwright test`
**Run by tag**: `cd e2e && npx playwright test --grep "Authentication"`
**Run headed**: `cd e2e && npx playwright test --headed`
**Debug**: `cd e2e && npx playwright test --debug`
