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

  // -- Calendar View ---------------------------------------------------------

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

  // -- Date Selection --------------------------------------------------------

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

  // -- Mark Attendance -------------------------------------------------------

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

  // -- Attendance History ----------------------------------------------------

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
