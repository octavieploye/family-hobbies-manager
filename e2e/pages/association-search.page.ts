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
