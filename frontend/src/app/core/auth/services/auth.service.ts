// frontend/src/app/core/auth/services/auth.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import {
  LoginRequest,
  RegisterRequest,
  AuthResponse,
} from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API_BASE = '/api/v1/auth';
  private readonly ACCESS_TOKEN_KEY = 'access_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router
  ) {}

  /**
   * Authenticate user with email and password.
   */
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_BASE}/login`, request);
  }

  /**
   * Register a new family account.
   */
  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_BASE}/register`, request);
  }

  /**
   * Exchange a valid refresh token for a new token pair.
   * Called by the JWT interceptor when a 401 is received.
   */
  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    return this.http
      .post<AuthResponse>(`${this.API_BASE}/refresh`, { refreshToken })
      .pipe(
        tap((response) => {
          this.storeTokens(response.accessToken, response.refreshToken);
        })
      );
  }

  /**
   * Clear tokens from localStorage and redirect to login.
   */
  logout(): void {
    localStorage.removeItem(this.ACCESS_TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    this.router.navigate(['/auth/login']);
  }

  /**
   * Persist both tokens in localStorage.
   * Called by NgRx effects after successful login/register.
   */
  storeTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(this.ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
  }

  /**
   * Read the current access token from localStorage.
   * Returns null if no token is stored.
   */
  getAccessToken(): string | null {
    return localStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  /**
   * Read the current refresh token from localStorage.
   * Returns null if no token is stored.
   */
  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  /**
   * Quick check whether a token exists in localStorage.
   * Does NOT validate the token signature or expiry -- that is the backend's job.
   */
  isAuthenticated(): boolean {
    return this.getAccessToken() !== null;
  }
}
