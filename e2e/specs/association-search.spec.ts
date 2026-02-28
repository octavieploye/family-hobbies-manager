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

  // -- Page Load -------------------------------------------------------------

  test.describe('Page Load', () => {
    test('should display the search page with all filter controls', async () => {
      await searchPage.expectPageLoaded();
      await expect(searchPage.keywordInput).toBeVisible();
      await expect(searchPage.cityInput).toBeVisible();
      await expect(searchPage.categorySelect).toBeVisible();
      await expect(searchPage.searchButton).toBeVisible();
    });
  });

  // -- Keyword Search --------------------------------------------------------

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

  // -- City Filter -----------------------------------------------------------

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

  // -- Category Filter -------------------------------------------------------

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

  // -- Combined Filters ------------------------------------------------------

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

  // -- Pagination ------------------------------------------------------------

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

  // -- Detail Navigation -----------------------------------------------------

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
