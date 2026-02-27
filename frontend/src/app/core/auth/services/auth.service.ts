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
import { TokenStorageService } from './token-storage.service';
import { environment } from '../../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API_BASE = `${environment.apiBaseUrl}/auth`;

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
    private readonly tokenStorage: TokenStorageService
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
   * Clear tokens and redirect to login.
   */
  logout(): void {
    this.tokenStorage.clearTokens();
    this.router.navigate(['/auth/login']);
  }

  /**
   * Persist both tokens.
   * Called by NgRx effects after successful login/register.
   */
  storeTokens(accessToken: string, refreshToken: string): void {
    this.tokenStorage.storeTokens(accessToken, refreshToken);
  }

  /**
   * Read the current access token.
   * Returns null if no token is stored.
   */
  getAccessToken(): string | null {
    return this.tokenStorage.getAccessToken();
  }

  /**
   * Read the current refresh token.
   * Returns null if no token is stored.
   */
  getRefreshToken(): string | null {
    return this.tokenStorage.getRefreshToken();
  }

  /**
   * Quick check whether a token exists.
   * Does NOT validate the token signature or expiry -- that is the backend's job.
   */
  isAuthenticated(): boolean {
    return this.tokenStorage.hasAccessToken();
  }
}
