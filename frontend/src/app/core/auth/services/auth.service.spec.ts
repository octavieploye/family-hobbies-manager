// frontend/src/app/core/auth/services/auth.service.spec.ts
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
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
 * Updated for: M-023 — Replace deprecated HttpClientTestingModule
 * Tests: 5 test methods
 *
 * These tests verify:
 * 1. login() calls POST /auth/login via environment.apiBaseUrl and returns AuthResponse
 * 2. register() calls POST /auth/register via environment.apiBaseUrl and returns AuthResponse
 * 3. getAccessToken() delegates to TokenStorageService
 * 4. isAuthenticated() returns true when token exists
 * 5. logout() clears tokens via TokenStorageService and navigates to /auth/login
 *
 * Uses provideHttpClient() + provideHttpClientTesting() (Angular 17+ API).
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
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
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

  it('should call API and return response when login requested', () => {
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

  it('should call API and return response when register requested', () => {
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

  it('should delegate to TokenStorageService when getAccessToken called', () => {
    tokenStorageSpy.getAccessToken.mockReturnValue(null);
    expect(service.getAccessToken()).toBeNull();

    tokenStorageSpy.getAccessToken.mockReturnValue('stored-token');
    expect(service.getAccessToken()).toBe('stored-token');
  });

  it('should delegate to TokenStorageService when isAuthenticated called', () => {
    tokenStorageSpy.hasAccessToken.mockReturnValue(false);
    expect(service.isAuthenticated()).toBe(false);

    tokenStorageSpy.hasAccessToken.mockReturnValue(true);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('should clear tokens and navigate to login when logout called', () => {
    service.logout();

    expect(tokenStorageSpy.clearTokens).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login']);
  });

  it('should call refresh endpoint and return response when refreshToken called', () => {
    // given
    tokenStorageSpy.getRefreshToken.mockReturnValue('current-refresh-token');

    // when
    service.refreshToken().subscribe((response) => {
      // then
      expect(response).toEqual(mockAuthResponse);
      // storeTokens should be called via the tap() operator inside refreshToken()
      expect(tokenStorageSpy.storeTokens).toHaveBeenCalledWith(
        'test-access-token',
        'test-refresh-token'
      );
    });

    const req = httpMock.expectOne((r) => r.url.endsWith('/auth/refresh'));
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ refreshToken: 'current-refresh-token' });
    req.flush(mockAuthResponse);
  });

  it('should delegate to TokenStorageService when storeTokens called', () => {
    // when
    service.storeTokens('new-access', 'new-refresh');

    // then
    expect(tokenStorageSpy.storeTokens).toHaveBeenCalledWith('new-access', 'new-refresh');
  });
});
