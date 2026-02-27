import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { LiveAnnouncer } from '@angular/cdk/a11y';
import { errorInterceptor } from './error.interceptor';
import { AuthService } from '../../auth/services/auth.service';
import { of, throwError } from 'rxjs';
import { AuthResponse } from '../../auth/models/auth.models';

/**
 * Unit tests for errorInterceptor (Angular functional interceptor).
 *
 * Tests: 7 test methods
 *
 * These tests verify:
 * 1. 401 triggers token refresh then retries the original request
 * 2. 401 on refresh endpoint does NOT retry (prevents infinite loop)
 * 3. 403 navigates to /forbidden
 * 4. 500 shows snackbar with error class
 * 5. snackbar shows French messages
 * 6. 401 on refresh failure forces logout
 * 7. LiveAnnouncer is called for RGAA compliance
 */
describe('errorInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let routerSpy: jest.Mocked<Router>;
  let snackBarSpy: jest.Mocked<MatSnackBar>;
  let liveAnnouncerSpy: jest.Mocked<LiveAnnouncer>;
  let authServiceSpy: jest.Mocked<AuthService>;

  const mockRefreshResponse: AuthResponse = {
    accessToken: 'new-access-token',
    refreshToken: 'new-refresh-token',
    tokenType: 'Bearer',
    expiresIn: 3600,
  };

  beforeEach(() => {
    routerSpy = {
      navigate: jest.fn(),
    } as unknown as jest.Mocked<Router>;

    snackBarSpy = {
      open: jest.fn(),
    } as unknown as jest.Mocked<MatSnackBar>;

    liveAnnouncerSpy = {
      announce: jest.fn().mockResolvedValue(undefined),
    } as unknown as jest.Mocked<LiveAnnouncer>;

    authServiceSpy = {
      refreshToken: jest.fn(),
      logout: jest.fn(),
      login: jest.fn(),
      register: jest.fn(),
      storeTokens: jest.fn(),
      getAccessToken: jest.fn(),
      getRefreshToken: jest.fn(),
      isAuthenticated: jest.fn(),
    } as unknown as jest.Mocked<AuthService>;

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerSpy },
        { provide: MatSnackBar, useValue: snackBarSpy },
        { provide: LiveAnnouncer, useValue: liveAnnouncerSpy },
        { provide: AuthService, useValue: authServiceSpy },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should trigger token refresh and retry request when 401 received on non-refresh endpoint', () => {
    // given
    authServiceSpy.refreshToken.mockReturnValue(of(mockRefreshResponse));

    httpClient.get('/api/v1/families').subscribe({
      next: (response) => {
        // The retry should succeed
        expect(response).toEqual({ id: 1, name: 'Famille Dupont' });
      },
    });

    // First request returns 401
    const firstReq = httpMock.expectOne('/api/v1/families');
    firstReq.flush(
      { message: 'Token expired' },
      { status: 401, statusText: 'Unauthorized' }
    );

    // After refresh, interceptor retries the request
    expect(authServiceSpy.refreshToken).toHaveBeenCalled();

    // The retried request should have the new token
    const retryReq = httpMock.expectOne('/api/v1/families');
    expect(retryReq.request.headers.get('Authorization')).toBe('Bearer new-access-token');
    retryReq.flush({ id: 1, name: 'Famille Dupont' });
  });

  it('should NOT retry when 401 received on refresh endpoint to prevent infinite loop', () => {
    // given
    let errorCaught: HttpErrorResponse | undefined;

    httpClient.post('/api/v1/auth/refresh', {}).subscribe({
      error: (err) => {
        errorCaught = err;
      },
    });

    // Request to refresh endpoint returns 401
    const req = httpMock.expectOne('/api/v1/auth/refresh');
    req.flush(
      { message: 'Refresh token invalid' },
      { status: 401, statusText: 'Unauthorized' }
    );

    // then -- should NOT attempt to refresh (would cause infinite loop)
    expect(authServiceSpy.refreshToken).not.toHaveBeenCalled();
    expect(errorCaught).toBeDefined();
    expect(errorCaught!.status).toBe(401);
  });

  it('should navigate to /forbidden when 403 received', () => {
    // given
    httpClient.get('/api/v1/admin/users').subscribe({
      error: () => {
        // expected
      },
    });

    // when
    const req = httpMock.expectOne('/api/v1/admin/users');
    req.flush(
      { message: 'Forbidden' },
      { status: 403, statusText: 'Forbidden' }
    );

    // then
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/forbidden']);
  });

  it('should show snackbar with error class when 500 received', () => {
    // given
    httpClient.get('/api/v1/families').subscribe({
      error: () => {
        // expected
      },
    });

    // when
    const req = httpMock.expectOne('/api/v1/families');
    req.flush(
      { message: 'Internal Server Error' },
      { status: 500, statusText: 'Internal Server Error' }
    );

    // then
    expect(snackBarSpy.open).toHaveBeenCalledWith(
      expect.any(String),
      'Fermer',
      expect.objectContaining({
        panelClass: 'snackbar-error',
      })
    );
  });

  it('should show French messages in snackbar when error occurs', () => {
    // given
    httpClient.get('/api/v1/families/99').subscribe({
      error: () => {
        // expected
      },
    });

    // when
    const req = httpMock.expectOne('/api/v1/families/99');
    req.flush(
      { message: 'Not Found' },
      { status: 404, statusText: 'Not Found' }
    );

    // then
    expect(snackBarSpy.open).toHaveBeenCalledWith(
      'La ressource demand\u00e9e est introuvable.',
      'Fermer',
      expect.objectContaining({
        panelClass: 'snackbar-warning',
      })
    );
  });

  it('should force logout when refresh fails after 401', () => {
    // given
    authServiceSpy.refreshToken.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' }))
    );

    httpClient.get('/api/v1/families').subscribe({
      error: () => {
        // expected
      },
    });

    // First request returns 401
    const req = httpMock.expectOne('/api/v1/families');
    req.flush(
      { message: 'Token expired' },
      { status: 401, statusText: 'Unauthorized' }
    );

    // then -- refresh was attempted and failed, so logout is called
    expect(authServiceSpy.refreshToken).toHaveBeenCalled();
    expect(authServiceSpy.logout).toHaveBeenCalled();
  });

  it('should announce error via LiveAnnouncer for RGAA compliance when non-401 error occurs', () => {
    // given
    httpClient.get('/api/v1/families').subscribe({
      error: () => {
        // expected
      },
    });

    // when
    const req = httpMock.expectOne('/api/v1/families');
    req.flush(
      { message: 'Internal Server Error' },
      { status: 500, statusText: 'Internal Server Error' }
    );

    // then
    expect(liveAnnouncerSpy.announce).toHaveBeenCalledWith(
      expect.stringContaining('Erreur:'),
      'assertive'
    );
  });
});
