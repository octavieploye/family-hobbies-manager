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
