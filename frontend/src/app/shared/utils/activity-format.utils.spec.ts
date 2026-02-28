// frontend/src/app/shared/utils/activity-format.utils.spec.ts
import { formatAgeRange, formatPriceInEuros } from './activity-format.utils';

describe('Activity Format Utils', () => {
  describe('formatAgeRange', () => {
    it('should return full range when both minAge and maxAge are provided', () => {
      expect(formatAgeRange(6, 10)).toBe('6 - 10 ans');
    });

    it('should return "\u00C0 partir de" when only minAge is provided', () => {
      expect(formatAgeRange(8, null)).toBe('\u00C0 partir de 8 ans');
    });

    it('should return "Jusqu\'\u00E0" when only maxAge is provided', () => {
      expect(formatAgeRange(null, 12)).toBe('Jusqu\'\u00E0 12 ans');
    });

    it('should return null when both ages are null', () => {
      expect(formatAgeRange(null, null)).toBeNull();
    });

    it('should handle zero as a valid age', () => {
      expect(formatAgeRange(0, 5)).toBe('0 - 5 ans');
    });
  });

  describe('formatPriceInEuros', () => {
    it('should convert cents to euros', () => {
      expect(formatPriceInEuros(15000)).toBe(150);
    });

    it('should handle zero', () => {
      expect(formatPriceInEuros(0)).toBe(0);
    });

    it('should handle fractional euros', () => {
      expect(formatPriceInEuros(1550)).toBe(15.5);
    });

    it('should handle single cent', () => {
      expect(formatPriceInEuros(1)).toBe(0.01);
    });
  });
});
