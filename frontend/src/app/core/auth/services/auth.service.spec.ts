// frontend/src/app/core/auth/services/auth.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { TokenStorageService } from './token-storage.service';
import { AuthResponse } from '../models/auth.models';

/**
 * Unit tests for AuthService.
 *
 * Story: S1-006 — Implement Angular Auth Scaffolding
 * Updated for: H-015 — TokenStorageService extraction
 * Tests: 5 test methods
 *
 * These tests verify:
 * 1. login() calls POST /auth/login via environment.apiBaseUrl and returns AuthResponse
 * 2. register() calls POST /auth/register via environment.apiBaseUrl and returns AuthResponse
 * 3. getAccessToken() delegates to TokenStorageService
 * 4. isAuthenticated() returns true when token exists
 * 5. logout() clears tokens via TokenStorageService and navigates to /auth/login
 *
 * Uses HttpClientTestingModule to intercept HTTP requests without a real server.
 */
describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerSpy: jest.Mocked<Router>;
  let tokenStorageSpy: jest.Mocked<TokenStorageService>;

  const mockAuthResponse: AuthResponse = {
    accessToken: 'test-access-token',
    refreshToken: 'test-refresh-token',
    tokenType: 'Bearer',
    expiresIn: 3600,
  };

  beforeEach(() => {
    routerSpy = {
      navigate: jest.fn(),
    } as unknown as jest.Mocked<Router>;

    tokenStorageSpy = {
      storeTokens: jest.fn(),
      getAccessToken: jest.fn(),
      getRefreshToken: jest.fn(),
      clearTokens: jest.fn(),
      hasAccessToken: jest.fn(),
    } as unknown as jest.Mocked<TokenStorageService>;

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        AuthService,
        { provide: Router, useValue: routerSpy },
        { provide: TokenStorageService, useValue: tokenStorageSpy },
      ],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('login_shouldCallApiAndReturnResponse', () => {
    service
      .login({ email: 'test@example.com', password: 'password123' })
      .subscribe((response) => {
        expect(response).toEqual(mockAuthResponse);
      });

    const req = httpMock.expectOne((r) => r.url.endsWith('/auth/login'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'test@example.com',
      password: 'password123',
    });
    req.flush(mockAuthResponse);
  });

  it('register_shouldCallApiAndReturnResponse', () => {
    const registerPayload = {
      email: 'new@example.com',
      password: 'password123',
      firstName: 'Jean',
      lastName: 'Dupont',
    };

    service.register(registerPayload).subscribe((response) => {
      expect(response).toEqual(mockAuthResponse);
    });

    const req = httpMock.expectOne((r) => r.url.endsWith('/auth/register'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(registerPayload);
    req.flush(mockAuthResponse);
  });

  it('getAccessToken_shouldDelegateToTokenStorageService', () => {
    tokenStorageSpy.getAccessToken.mockReturnValue(null);
    expect(service.getAccessToken()).toBeNull();

    tokenStorageSpy.getAccessToken.mockReturnValue('stored-token');
    expect(service.getAccessToken()).toBe('stored-token');
  });

  it('isAuthenticated_shouldDelegateToTokenStorageService', () => {
    tokenStorageSpy.hasAccessToken.mockReturnValue(false);
    expect(service.isAuthenticated()).toBe(false);

    tokenStorageSpy.hasAccessToken.mockReturnValue(true);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('logout_shouldClearTokensAndNavigateToLogin', () => {
    service.logout();

    expect(tokenStorageSpy.clearTokens).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login']);
  });
});
