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

  // -- View Family -----------------------------------------------------------

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

  // -- Add Member ------------------------------------------------------------

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

  // -- Edit Member -----------------------------------------------------------

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

  // -- Remove Member ---------------------------------------------------------

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

  // -- Create Family ---------------------------------------------------------

  test.describe('Create Family', () => {
    test('should create a new family', async () => {
      await familyPage.goto();
      await familyPage.createFamily(TEST_FAMILIES.newFamily.name);

      await expect(familyPage.successMessage).toBeVisible();
    });
  });
});
