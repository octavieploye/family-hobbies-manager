// frontend/src/app/shared/utils/attendance-display.utils.spec.ts
import {
  getProgressColor,
  getAttendanceStatusLabel,
  getAttendanceStatusIcon,
  getAttendanceStatusColor,
} from './attendance-display.utils';

describe('Attendance Display Utils', () => {
  describe('getProgressColor', () => {
    it('should return "primary" for rate >= 80', () => {
      expect(getProgressColor(80)).toBe('primary');
      expect(getProgressColor(100)).toBe('primary');
      expect(getProgressColor(85)).toBe('primary');
    });

    it('should return "accent" for rate >= 50 and < 80', () => {
      expect(getProgressColor(50)).toBe('accent');
      expect(getProgressColor(79)).toBe('accent');
      expect(getProgressColor(60)).toBe('accent');
    });

    it('should return "warn" for rate < 50', () => {
      expect(getProgressColor(49)).toBe('warn');
      expect(getProgressColor(0)).toBe('warn');
      expect(getProgressColor(30)).toBe('warn');
    });
  });

  describe('getAttendanceStatusLabel', () => {
    it('should return "Pr\u00e9sent" for PRESENT', () => {
      expect(getAttendanceStatusLabel('PRESENT')).toBe('Pr\u00e9sent');
    });

    it('should return "Absent" for ABSENT', () => {
      expect(getAttendanceStatusLabel('ABSENT')).toBe('Absent');
    });

    it('should return "Excus\u00e9" for EXCUSED', () => {
      expect(getAttendanceStatusLabel('EXCUSED')).toBe('Excus\u00e9');
    });

    it('should return "En retard" for LATE', () => {
      expect(getAttendanceStatusLabel('LATE')).toBe('En retard');
    });
  });

  describe('getAttendanceStatusIcon', () => {
    it('should return "check_circle" for PRESENT', () => {
      expect(getAttendanceStatusIcon('PRESENT')).toBe('check_circle');
    });

    it('should return "cancel" for ABSENT', () => {
      expect(getAttendanceStatusIcon('ABSENT')).toBe('cancel');
    });

    it('should return "info" for EXCUSED', () => {
      expect(getAttendanceStatusIcon('EXCUSED')).toBe('info');
    });

    it('should return "schedule" for LATE', () => {
      expect(getAttendanceStatusIcon('LATE')).toBe('schedule');
    });
  });

  describe('getAttendanceStatusColor', () => {
    it('should return green for PRESENT', () => {
      expect(getAttendanceStatusColor('PRESENT')).toBe('#4caf50');
    });

    it('should return red for ABSENT', () => {
      expect(getAttendanceStatusColor('ABSENT')).toBe('#f44336');
    });

    it('should return orange for EXCUSED', () => {
      expect(getAttendanceStatusColor('EXCUSED')).toBe('#ff9800');
    });

    it('should return blue for LATE', () => {
      expect(getAttendanceStatusColor('LATE')).toBe('#2196f3');
    });
  });
});
