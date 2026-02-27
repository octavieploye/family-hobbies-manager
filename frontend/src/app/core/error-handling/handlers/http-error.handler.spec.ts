import { HttpErrorResponse } from '@angular/common/http';
import { parseHttpError, ParsedError } from './http-error.handler';
import { ErrorCode } from '../models/error-code.enum';

/**
 * Unit tests for http-error.handler.ts (parseHttpError, French messages, isRetryable, correlationId).
 *
 * Tests: 12 test methods
 *
 * These tests verify:
 * 1. parseHttpError returns correct ErrorCode for 400, 401, 403, 404, 500 status codes
 * 2. French message mapping for each status code
 * 3. isRetryableError returns true for retryable statuses (0, 408, 429, 503)
 * 4. isRetryableError returns false for non-retryable statuses (400, 401, 403, 404)
 * 5. correlationId extraction from structured backend ApiError response
 * 6. Network error (status 0) returns NETWORK_ERROR code with French message
 * 7. Generic HTTP error (no structured body) maps status to correct ErrorCode
 */
describe('HttpErrorHandler', () => {
  describe('parseHttpError', () => {
    describe('status code mapping', () => {
      it('should return VALIDATION_FAILED error code when status is 400', () => {
        // given
        const error = new HttpErrorResponse({ status: 400, statusText: 'Bad Request' });

        // when
        const result = parseHttpError(error);

        // then
        expect(result.status).toBe(400);
        expect(result.errorCode).toBe(ErrorCode.VALIDATION_FAILED);
        expect(result.message).toBe('Les donn\u00e9es envoy\u00e9es sont invalides.');
      });

      it('should return UNAUTHORIZED error code when status is 401', () => {
        // given
        const error = new HttpErrorResponse({ status: 401, statusText: 'Unauthorized' });

        // when
        const result = parseHttpError(error);

        // then
        expect(result.status).toBe(401);
        expect(result.errorCode).toBe(ErrorCode.UNAUTHORIZED);
        expect(result.message).toBe('Votre session a expir\u00e9. Veuillez vous reconnecter.');
      });

      it('should return FORBIDDEN error code when status is 403', () => {
        // given
        const error = new HttpErrorResponse({ status: 403, statusText: 'Forbidden' });

        // when
        const result = parseHttpError(error);

        // then
        expect(result.status).toBe(403);
        expect(result.errorCode).toBe(ErrorCode.FORBIDDEN);
        expect(result.message).toBe("Vous n'avez pas les droits n\u00e9cessaires pour cette action.");
      });

      it('should return RESOURCE_NOT_FOUND error code when status is 404', () => {
        // given
        const error = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });

        // when
        const result = parseHttpError(error);

        // then
        expect(result.status).toBe(404);
        expect(result.errorCode).toBe(ErrorCode.RESOURCE_NOT_FOUND);
        expect(result.message).toBe('La ressource demand\u00e9e est introuvable.');
      });

      it('should return INTERNAL_SERVER_ERROR error code when status is 500', () => {
        // given
        const error = new HttpErrorResponse({ status: 500, statusText: 'Internal Server Error' });

        // when
        const result = parseHttpError(error);

        // then
        expect(result.status).toBe(500);
        expect(result.errorCode).toBe(ErrorCode.INTERNAL_SERVER_ERROR);
        expect(result.message).toBe('Une erreur interne est survenue. Veuillez r\u00e9essayer ult\u00e9rieurement.');
      });
    });

    describe('isRetryable', () => {
      it('should return true when status is retryable (408, 429, 503)', () => {
        // given — only idempotent-safe statuses are retryable (M-024)
        // 500/502/504 are NOT retryable: server errors may have side-effects
        const retryableStatuses = [408, 429, 503];

        retryableStatuses.forEach((status) => {
          // when
          const error = new HttpErrorResponse({ status, statusText: 'Error' });
          const result = parseHttpError(error);

          // then
          expect(result.isRetryable).toBe(true);
        });
      });

      it('should return false when status is not retryable (400, 401, 403, 404, 500, 502, 504)', () => {
        // given — 500/502/504 are deliberately non-retryable (M-024)
        const nonRetryableStatuses = [400, 401, 403, 404, 500, 502, 504];

        nonRetryableStatuses.forEach((status) => {
          // when
          const error = new HttpErrorResponse({ status, statusText: 'Error' });
          const result = parseHttpError(error);

          // then
          expect(result.isRetryable).toBe(false);
        });
      });
    });

    describe('structured ApiError response', () => {
      it('should extract correlationId when backend returns structured ApiError', () => {
        // given
        const apiError = {
          timestamp: '2026-02-27T10:00:00Z',
          status: 404,
          error: 'Not Found',
          message: 'User not found with id: 42',
          path: '/api/v1/users/42',
          correlationId: 'trace-abc-123',
          errorCode: ErrorCode.RESOURCE_NOT_FOUND,
        };
        const error = new HttpErrorResponse({
          status: 404,
          statusText: 'Not Found',
          error: apiError,
        });

        // when
        const result = parseHttpError(error);

        // then
        expect(result.correlationId).toBe('trace-abc-123');
        expect(result.status).toBe(404);
        expect(result.errorCode).toBe(ErrorCode.RESOURCE_NOT_FOUND);
      });

      it('should extract details when backend returns field errors', () => {
        // given
        const apiError = {
          timestamp: '2026-02-27T10:00:00Z',
          status: 400,
          error: 'Bad Request',
          message: 'Validation failed',
          path: '/api/v1/auth/register',
          errorCode: ErrorCode.VALIDATION_FAILED,
          details: [
            { field: 'email', message: 'must not be blank' },
            { field: 'password', message: 'size must be between 8 and 100' },
          ],
        };
        const error = new HttpErrorResponse({
          status: 400,
          statusText: 'Bad Request',
          error: apiError,
        });

        // when
        const result = parseHttpError(error);

        // then
        expect(result.details).toBeDefined();
        expect(result.details!.length).toBe(2);
        expect(result.details![0].field).toBe('email');
        expect(result.details![1].field).toBe('password');
      });
    });

    describe('network error', () => {
      it('should return NETWORK_ERROR with French message when status is 0', () => {
        // given
        const error = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });

        // when
        const result = parseHttpError(error);

        // then
        expect(result.status).toBe(0);
        expect(result.errorCode).toBe(ErrorCode.NETWORK_ERROR);
        expect(result.message).toBe('Connexion au serveur impossible. V\u00e9rifiez votre connexion internet.');
        expect(result.isRetryable).toBe(true);
      });
    });

    describe('French message mapping', () => {
      it('should return French message for each known status code', () => {
        // given
        const expectedMessages: Record<number, string> = {
          400: 'Les donn\u00e9es envoy\u00e9es sont invalides.',
          401: 'Votre session a expir\u00e9. Veuillez vous reconnecter.',
          403: "Vous n'avez pas les droits n\u00e9cessaires pour cette action.",
          404: 'La ressource demand\u00e9e est introuvable.',
          500: 'Une erreur interne est survenue. Veuillez r\u00e9essayer ult\u00e9rieurement.',
          502: 'Le service est temporairement indisponible.',
          503: 'Le service est en maintenance. Veuillez r\u00e9essayer dans quelques instants.',
          504: 'Le serveur a mis trop de temps \u00e0 r\u00e9pondre.',
        };

        Object.entries(expectedMessages).forEach(([statusStr, expectedMessage]) => {
          const status = Number(statusStr);
          // when
          const error = new HttpErrorResponse({ status, statusText: 'Error' });
          const result = parseHttpError(error);

          // then
          expect(result.message).toBe(expectedMessage);
        });
      });
    });
  });
});
