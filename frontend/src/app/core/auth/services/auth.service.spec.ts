// frontend/src/app/core/auth/services/auth.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthResponse } from '../models/auth.models';

/**
 * Unit tests for AuthService.
 *
 * Story: S1-006 — Implement Angular Auth Scaffolding
 * Tests: 5 test methods
 *
 * These tests verify:
 * 1. login() calls POST /api/v1/auth/login and returns AuthResponse
 * 2. register() calls POST /api/v1/auth/register and returns AuthResponse
 * 3. getAccessToken() reads from localStorage
 * 4. isAuthenticated() returns true when token exists
 * 5. logout() clears localStorage and navigates to /auth/login
 *
 * Uses HttpClientTestingModule to intercept HTTP requests without a real server.
 *
 * Review findings incorporated:
 * - F-12 (NOTE): logout test does not verify a backend POST /api/v1/auth/logout
 *   call. Current design is client-side only (clear localStorage). If the frontend
 *   should also call the backend to revoke refresh tokens, add an httpMock.expectOne
 *   assertion. This is a design decision to be confirmed during implementation.
 */
describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerSpy: jest.Mocked<Router>;

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

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AuthService, { provide: Router, useValue: routerSpy }],
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);

    // Clear localStorage before each test
    localStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('login_shouldCallApiAndReturnResponse', () => {
    service
      .login({ email: 'test@example.com', password: 'password123' })
      .subscribe((response) => {
        expect(response).toEqual(mockAuthResponse);
      });

    const req = httpMock.expectOne('/api/v1/auth/login');
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

    const req = httpMock.expectOne('/api/v1/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(registerPayload);
    req.flush(mockAuthResponse);
  });

  it('getAccessToken_shouldReturnFromLocalStorage', () => {
    expect(service.getAccessToken()).toBeNull();

    localStorage.setItem('access_token', 'stored-token');
    expect(service.getAccessToken()).toBe('stored-token');
  });

  it('isAuthenticated_shouldReturnTrueWhenTokenExists', () => {
    expect(service.isAuthenticated()).toBe(false);

    localStorage.setItem('access_token', 'some-token');
    expect(service.isAuthenticated()).toBe(true);
  });

  it('logout_shouldClearLocalStorage', () => {
    localStorage.setItem('access_token', 'token-a');
    localStorage.setItem('refresh_token', 'token-r');

    service.logout();

    expect(localStorage.getItem('access_token')).toBeNull();
    expect(localStorage.getItem('refresh_token')).toBeNull();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/auth/login']);
  });
});
