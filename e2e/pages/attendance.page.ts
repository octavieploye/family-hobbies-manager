import { type Locator, type Page, expect } from '@playwright/test';

/**
 * Page Object Model for the attendance page (/attendance).
 *
 * Used by: attendance.spec.ts
 */
export class AttendancePage {
  readonly page: Page;
  readonly pageTitle: Locator;
  readonly calendarView: Locator;
  readonly calendarDays: Locator;
  readonly selectedDate: Locator;
  readonly sessionList: Locator;
  readonly sessionCards: Locator;
  readonly historyTab: Locator;
  readonly historyTable: Locator;
  readonly historyRows: Locator;
  readonly memberFilter: Locator;
  readonly activityFilter: Locator;
  readonly monthNavigateNext: Locator;
  readonly monthNavigatePrev: Locator;
  readonly currentMonthLabel: Locator;

  constructor(page: Page) {
    this.page = page;
    this.pageTitle = page.getByRole('heading', { name: /presence/i });
    this.calendarView = page.getByTestId('attendance-calendar');
    this.calendarDays = page.getByTestId('calendar-day');
    this.selectedDate = page.getByTestId('selected-date');
    this.sessionList = page.getByTestId('session-list');
    this.sessionCards = page.getByTestId('session-card');
    this.historyTab = page.getByTestId('attendance-history-tab');
    this.historyTable = page.getByTestId('attendance-history-table');
    this.historyRows = page.getByTestId('attendance-history-row');
    this.memberFilter = page.getByTestId('attendance-member-filter');
    this.activityFilter = page.getByTestId('attendance-activity-filter');
    this.monthNavigateNext = page.getByTestId('calendar-next-month');
    this.monthNavigatePrev = page.getByTestId('calendar-prev-month');
    this.currentMonthLabel = page.getByTestId('calendar-current-month');
  }

  async goto(): Promise<void> {
    await this.page.goto('/attendance');
    await this.pageTitle.waitFor({ state: 'visible' });
  }

  /**
   * Click on a specific day in the calendar.
   */
  async selectDay(dayNumber: number): Promise<void> {
    await this.calendarDays
      .filter({ hasText: String(dayNumber) })
      .first()
      .click();
  }

  /**
   * Mark a member as present for a session.
   */
  async markPresent(sessionIndex: number): Promise<void> {
    const card = this.sessionCards.nth(sessionIndex);
    await card.getByTestId('mark-present-button').click();
    await this.page.waitForTimeout(300);
  }

  /**
   * Mark a member as absent for a session.
   */
  async markAbsent(sessionIndex: number): Promise<void> {
    const card = this.sessionCards.nth(sessionIndex);
    await card.getByTestId('mark-absent-button').click();
    await this.page.waitForTimeout(300);
  }

  /**
   * Get the attendance status icon/text for a session card.
   */
  async getAttendanceStatus(sessionIndex: number): Promise<string | null> {
    const card = this.sessionCards.nth(sessionIndex);
    return card.getByTestId('attendance-status').textContent();
  }

  /**
   * Switch to the history tab.
   */
  async viewHistory(): Promise<void> {
    await this.historyTab.click();
    await this.historyTable.waitFor({ state: 'visible' });
  }

  /**
   * Get the number of rows in the attendance history table.
   */
  async getHistoryRowCount(): Promise<number> {
    return this.historyRows.count();
  }

  /**
   * Navigate to the next month in the calendar.
   */
  async goToNextMonth(): Promise<void> {
    await this.monthNavigateNext.click();
  }

  /**
   * Navigate to the previous month in the calendar.
   */
  async goToPreviousMonth(): Promise<void> {
    await this.monthNavigatePrev.click();
  }

  async expectPageLoaded(): Promise<void> {
    await expect(this.pageTitle).toBeVisible();
    await expect(this.calendarView).toBeVisible();
  }

  async expectSessionsVisible(): Promise<void> {
    const count = await this.sessionCards.count();
    expect(count).toBeGreaterThan(0);
  }
}
