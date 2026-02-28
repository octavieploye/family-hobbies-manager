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
