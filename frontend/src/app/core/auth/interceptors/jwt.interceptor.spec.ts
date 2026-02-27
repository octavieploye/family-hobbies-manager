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
import { jwtInterceptor } from './jwt.interceptor';
import { AuthService } from '../services/auth.service';

/**
 * Unit tests for jwtInterceptor (Angular functional interceptor).
 *
 * Story: S1-006 — Implement Angular Auth Scaffolding
 * Tests: 4 test methods
 *
 * After C-007 consolidation, this interceptor's ONLY responsibility is
 * attaching the Bearer token to outgoing requests. All 401 response
 * handling (token refresh, logout, redirect) lives in errorInterceptor.
 *
 * These tests verify:
 * 1. Bearer token is attached to requests for protected paths
 * 2. Auth header is skipped for public paths (/api/v1/auth/*)
 * 3. Requests without a stored token are sent without Authorization header
 * 4. 401 responses pass through without interception (delegated to errorInterceptor)
 */
describe('jwtInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let authServiceSpy: jest.Mocked<AuthService>;

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

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([jwtInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authServiceSpy },
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

  it('shouldNotAttachHeaderWhenNoTokenStored', () => {
    authServiceSpy.getAccessToken.mockReturnValue(null);

    httpClient.get('/api/v1/families').subscribe();

    const req = httpMock.expectOne('/api/v1/families');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('shouldPassThrough401WithoutIntercepting', () => {
    authServiceSpy.getAccessToken.mockReturnValue('expired-token');

    httpClient.get('/api/v1/families').subscribe({
      error: (err) => {
        // 401 should pass through to the subscriber (handled by errorInterceptor)
        expect(err.status).toBe(401);
      },
    });

    const req = httpMock.expectOne('/api/v1/families');
    req.flush(
      { message: 'Token expired' },
      { status: 401, statusText: 'Unauthorized' }
    );

    // jwt interceptor should NOT attempt refresh or logout
    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
    expect(authServiceSpy.logout).not.toHaveBeenCalled();
  });
});
