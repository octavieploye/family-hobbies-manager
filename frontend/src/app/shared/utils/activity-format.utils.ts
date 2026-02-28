// frontend/src/app/shared/utils/activity-format.utils.ts
// Shared utility functions for activity display formatting.

/**
 * Build a human-readable age range string in French.
 *
 * @param minAge Minimum age (or null if unbounded).
 * @param maxAge Maximum age (or null if unbounded).
 * @returns Formatted French string, or null if both ages are null.
 */
export function formatAgeRange(minAge: number | null, maxAge: number | null): string | null {
  if (minAge !== null && maxAge !== null) {
    return `${minAge} - ${maxAge} ans`;
  }
  if (minAge !== null) {
    return `\u00C0 partir de ${minAge} ans`;
  }
  if (maxAge !== null) {
    return `Jusqu'\u00E0 ${maxAge} ans`;
  }
  return null;
}

/**
 * Convert a price in cents to a display value in euros.
 *
 * @param priceCents Price expressed in euro-cents.
 * @returns Price value in euros (e.g. 15000 -> 150).
 */
export function formatPriceInEuros(priceCents: number): number {
  return priceCents / 100;
}
