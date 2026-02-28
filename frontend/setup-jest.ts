import { setupZoneTestEnv } from 'jest-preset-angular/setup-env/zone';

setupZoneTestEnv();

// Polyfill scrollIntoView for jsdom (not available in jsdom by default)
// Required by RGAA accessibility components that use smooth scrolling
if (typeof Element.prototype.scrollIntoView !== 'function') {
  Element.prototype.scrollIntoView = jest.fn();
}
