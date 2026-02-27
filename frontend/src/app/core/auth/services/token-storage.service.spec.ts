// frontend/src/app/core/auth/services/token-storage.service.spec.ts
import { TestBed } from '@angular/core/testing';
import { TokenStorageService } from './token-storage.service';

/**
 * Unit tests for TokenStorageService.
 *
 * Story: H-015 — Tokens stored in localStorage (XSS vulnerable)
 * Tests: 5 test methods
 *
 * These tests verify:
 * 1. storeTokens persists both access and refresh tokens
 * 2. getAccessToken returns the stored token or null
 * 3. getRefreshToken returns the stored token or null
 * 4. clearTokens removes all stored tokens
 * 5. hasAccessToken returns correct boolean based on token presence
 */
describe('TokenStorageService', () => {
  let service: TokenStorageService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TokenStorageService);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('storeTokens_shouldPersistBothTokens', () => {
    service.storeTokens('access-abc', 'refresh-xyz');

    expect(localStorage.getItem('access_token')).toBe('access-abc');
    expect(localStorage.getItem('refresh_token')).toBe('refresh-xyz');
  });

  it('getAccessToken_shouldReturnStoredTokenOrNull', () => {
    expect(service.getAccessToken()).toBeNull();

    localStorage.setItem('access_token', 'my-token');
    expect(service.getAccessToken()).toBe('my-token');
  });

  it('getRefreshToken_shouldReturnStoredTokenOrNull', () => {
    expect(service.getRefreshToken()).toBeNull();

    localStorage.setItem('refresh_token', 'my-refresh');
    expect(service.getRefreshToken()).toBe('my-refresh');
  });

  it('clearTokens_shouldRemoveAllTokens', () => {
    localStorage.setItem('access_token', 'token-a');
    localStorage.setItem('refresh_token', 'token-r');

    service.clearTokens();

    expect(localStorage.getItem('access_token')).toBeNull();
    expect(localStorage.getItem('refresh_token')).toBeNull();
  });

  it('hasAccessToken_shouldReturnCorrectBoolean', () => {
    expect(service.hasAccessToken()).toBe(false);

    localStorage.setItem('access_token', 'some-token');
    expect(service.hasAccessToken()).toBe(true);
  });
});
