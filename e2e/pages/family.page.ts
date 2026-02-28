import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for family pages (/families, /families/:id).
 *
 * Used by: family.spec.ts
 */
export class FamilyPage {
  readonly page: Page;
  readonly pageTitle: Locator;
  readonly familyNameInput: Locator;
  readonly createFamilyButton: Locator;
  readonly memberList: Locator;
  readonly addMemberButton: Locator;
  readonly memberCards: Locator;
  readonly saveFamilyButton: Locator;
  readonly successMessage: Locator;
  readonly errorMessage: Locator;

  /* Add Member Dialog */
  readonly memberFirstNameInput: Locator;
  readonly memberLastNameInput: Locator;
  readonly memberBirthDateInput: Locator;
  readonly memberRoleSelect: Locator;
  readonly saveMemberButton: Locator;
  readonly cancelMemberButton: Locator;

  /* Edit Member Dialog */
  readonly editMemberDialog: Locator;
  readonly editMemberFirstName: Locator;
  readonly editMemberLastName: Locator;
  readonly updateMemberButton: Locator;

  /* Delete Confirmation */
  readonly confirmDeleteButton: Locator;
  readonly cancelDeleteButton: Locator;
  readonly deleteConfirmDialog: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: /famille/i });
    this.familyNameInput = page.getByTestId('family-name');
    this.createFamilyButton = page.getByTestId('create-family-button');
    this.memberList = page.getByTestId('member-list');
    this.addMemberButton = page.getByTestId('add-member-button');
    this.memberCards = page.getByTestId('member-card');
    this.saveFamilyButton = page.getByTestId('save-family-button');
    this.successMessage = page.getByTestId('family-success');
    this.errorMessage = page.getByTestId('family-error');

    /* Add Member Dialog */
    this.memberFirstNameInput = page.getByTestId('member-firstname');
    this.memberLastNameInput = page.getByTestId('member-lastname');
    this.memberBirthDateInput = page.getByTestId('member-birthdate');
    this.memberRoleSelect = page.getByTestId('member-role');
    this.saveMemberButton = page.getByTestId('save-member-button');
    this.cancelMemberButton = page.getByTestId('cancel-member-button');

    /* Edit Member Dialog */
    this.editMemberDialog = page.getByTestId('edit-member-dialog');
    this.editMemberFirstName = page.getByTestId('edit-member-firstname');
    this.editMemberLastName = page.getByTestId('edit-member-lastname');
    this.updateMemberButton = page.getByTestId('update-member-button');

    /* Delete Confirmation */
    this.confirmDeleteButton = page.getByTestId('confirm-delete-button');
    this.cancelDeleteButton = page.getByTestId('cancel-delete-button');
    this.deleteConfirmDialog = page.getByTestId('delete-confirm-dialog');
  }

  async goto(): Promise<void> {
    await this.page.goto('/families');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  async gotoFamily(familyId: string): Promise<void> {
    await this.page.goto(`/families/${familyId}`);
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Create a new family with the given name.
   */
  async createFamily(name: string): Promise<void> {
    await this.familyNameInput.fill(name);
    await this.createFamilyButton.click();
    await this.successMessage.waitFor({ state: 'visible' });
  }

  /**
   * Open the "Add Member" dialog and fill in member details.
   */
  async addMember(data: {
    firstName: string;
    lastName: string;
    birthDate: string;
    role?: string;
  }): Promise<void> {
    await this.addMemberButton.click();
    await this.memberFirstNameInput.fill(data.firstName);
    await this.memberLastNameInput.fill(data.lastName);
    await this.memberBirthDateInput.fill(data.birthDate);
    if (data.role) {
      await this.memberRoleSelect.click();
      await this.page.getByRole('option', { name: data.role }).click();
    }
    await this.saveMemberButton.click();
    await this.page.waitForTimeout(500); // Wait for list refresh
  }

  /**
   * Click the edit button on a specific member card (by index).
   */
  async editMember(
    index: number,
    data: { firstName?: string; lastName?: string }
  ): Promise<void> {
    const card = this.memberCards.nth(index);
    await card.getByTestId('edit-member-button').click();
    await this.editMemberDialog.waitFor({ state: 'visible' });

    if (data.firstName) {
      await this.editMemberFirstName.clear();
      await this.editMemberFirstName.fill(data.firstName);
    }
    if (data.lastName) {
      await this.editMemberLastName.clear();
      await this.editMemberLastName.fill(data.lastName);
    }

    await this.updateMemberButton.click();
    await this.editMemberDialog.waitFor({ state: 'hidden' });
  }

  /**
   * Click the remove button on a specific member card and confirm.
   */
  async removeMember(index: number): Promise<void> {
    const card = this.memberCards.nth(index);
    await card.getByTestId('remove-member-button').click();
    await this.deleteConfirmDialog.waitFor({ state: 'visible' });
    await this.confirmDeleteButton.click();
    await this.deleteConfirmDialog.waitFor({ state: 'hidden' });
  }

  /**
   * Get the number of member cards currently displayed.
   */
  async getMemberCount(): Promise<number> {
    return this.memberCards.count();
  }

  /**
   * Get the name displayed on a specific member card (by index).
   */
  async getMemberName(index: number): Promise<string | null> {
    const card = this.memberCards.nth(index);
    return card.getByTestId('member-name').textContent();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
  }
}
