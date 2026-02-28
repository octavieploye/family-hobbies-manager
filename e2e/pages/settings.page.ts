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
