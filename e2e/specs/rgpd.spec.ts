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

  // -- Page Load -------------------------------------------------------------

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

  // -- Consent Management ----------------------------------------------------

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

  // -- Data Export -----------------------------------------------------------

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

  // -- Account Deletion ------------------------------------------------------

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
