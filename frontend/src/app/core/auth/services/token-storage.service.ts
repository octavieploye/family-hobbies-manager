// frontend/src/app/core/auth/services/token-storage.service.ts
import { Injectable } from '@angular/core';

/**
 * Centralized token storage abstraction.
 *
 * All token persistence operations go through this single service,
 * making it the only place to change when hardening token storage
 * (e.g., switching from localStorage to httpOnly cookies with CSRF).
 *
 * SECURITY NOTE: localStorage is used for now. For production, consider
 * httpOnly cookies with CSRF protection. localStorage is vulnerable to
 * XSS attacks — any injected script can read stored tokens. HttpOnly
 * cookies are immune to XSS but require CSRF protection on the backend.
 * This service isolates that decision to a single point of change.
 */
@Injectable({ providedIn: 'root' })
export class TokenStorageService {
  private readonly ACCESS_TOKEN_KEY = 'access_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';

  /**
   * Persist both tokens.
   * Called after successful login, register, or token refresh.
   */
  storeTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(this.ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
  }

  /**
   * Read the current access token.
   * Returns null if no token is stored.
   */
  getAccessToken(): string | null {
    return localStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  /**
   * Read the current refresh token.
   * Returns null if no token is stored.
   */
  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  /**
   * Remove all stored tokens.
   * Called on logout or when refresh fails.
   */
  clearTokens(): void {
    localStorage.removeItem(this.ACCESS_TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
  }

  /**
   * Quick check whether an access token exists.
   * Does NOT validate the token signature or expiry -- that is the backend's job.
   */
  hasAccessToken(): boolean {
    return this.getAccessToken() !== null;
  }
}
