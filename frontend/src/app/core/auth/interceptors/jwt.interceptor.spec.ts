// frontend/src/app/core/auth/interceptors/jwt.interceptor.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from '../services/auth.service';
import { of, throwError } from 'rxjs';
import { AuthResponse } from '../models/auth.models';

/**
 * Unit tests for jwtInterceptor (Angular functional interceptor).
 *
 * Story: S1-006 — Implement Angular Auth Scaffolding
 * Tests: 4 test methods
 *
 * These tests verify:
 * 1. Bearer token is attached to requests for protected paths
 * 2. Auth header is skipped for public paths (/api/v1/auth/*)
 * 3. 401 response triggers token refresh and retry
 * 4. Failed refresh triggers logout
 *
 * Uses provideHttpClient(withInterceptors([jwtInterceptor])) for Angular 17+
 * functional interceptor testing.
 *
 * Review findings incorporated:
 * - F-13 (NOTE): shouldAttemptRefreshOn401 does not verify that
 *   authServiceSpy.storeTokens is called with the new tokens after a
 *   successful refresh. Consider adding:
 *   expect(authServiceSpy.storeTokens).toHaveBeenCalledWith(mockRefreshResponse)
 *   during implementation if storeTokens is part of the interceptor's refresh flow.
 */
describe('jwtInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jest.Mocked<AuthService>;
  let routerSpy: jest.Mocked<Router>;

  const mockRefreshResponse: AuthResponse = {
    accessToken: 'new-access-token',
    refreshToken: 'new-refresh-token',
    tokenType: 'Bearer',
    expiresIn: 3600,
  };

  beforeEach(() => {
    authServiceSpy = {
      getAccessToken: jest.fn(),
      getRefreshToken: jest.fn(),
      refreshToken: jest.fn(),
      logout: jest.fn(),
      storeTokens: jest.fn(),
      login: jest.fn(),
      register: jest.fn(),
      isAuthenticated: jest.fn(),
    } as unknown as jest.Mocked<AuthService>;

    routerSpy = {
      navigate: jest.fn(),
    } as unknown as jest.Mocked<Router>;

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('shouldAttachBearerTokenToRequest', () => {
    authServiceSpy.getAccessToken.mockReturnValue('my-jwt-token');

    httpClient.get('/api/v1/families').subscribe();

    const req = httpMock.expectOne('/api/v1/families');
    expect(req.request.headers.get('Authorization')).toBe(
      'Bearer my-jwt-token'
    );
    req.flush({});
  });

  it('shouldSkipAuthForPublicPaths', () => {
    authServiceSpy.getAccessToken.mockReturnValue('my-jwt-token');

    httpClient.post('/api/v1/auth/login', {}).subscribe();

    const req = httpMock.expectOne('/api/v1/auth/login');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('shouldAttemptRefreshOn401', () => {
    authServiceSpy.getAccessToken.mockReturnValue('expired-token');
    authServiceSpy.refreshToken.mockReturnValue(of(mockRefreshResponse));

    httpClient.get('/api/v1/families').subscribe();

    // First request returns 401
    const firstReq = httpMock.expectOne('/api/v1/families');
    firstReq.flush(
      { message: 'Token expired' },
      { status: 401, statusText: 'Unauthorized' }
    );

    // After refresh, the interceptor retries with the new token
    const retryReq = httpMock.expectOne('/api/v1/families');
    expect(retryReq.request.headers.get('Authorization')).toBe(
      'Bearer new-access-token'
    );
    retryReq.flush({});
  });

  it('shouldLogoutOnRefreshFailure', () => {
    authServiceSpy.getAccessToken.mockReturnValue('expired-token');
    authServiceSpy.refreshToken.mockReturnValue(
      throwError(() => ({ status: 401, message: 'Refresh failed' }))
    );

    httpClient.get('/api/v1/families').subscribe({
      error: () => {
        // Expected to error after refresh failure
      },
    });

    const req = httpMock.expectOne('/api/v1/families');
    req.flush(
      { message: 'Token expired' },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(authServiceSpy.logout).toHaveBeenCalled();
  });
});
