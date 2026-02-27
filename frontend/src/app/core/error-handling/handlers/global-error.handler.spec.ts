import { HttpErrorResponse } from '@angular/common/http';
import { GlobalErrorHandler } from './global-error.handler';

/**
 * Unit tests for GlobalErrorHandler.
 *
 * Tests: 4 test methods
 *
 * These tests verify:
 * 1. handleError logs to console.error with [GlobalErrorHandler] prefix
 * 2. handleError logs structured info for Error instances (name, message, stack)
 * 3. handleError handles non-Error objects without throwing
 * 4. HttpErrorResponse is handled (logged) without crashing
 */
describe('GlobalErrorHandler', () => {
  let handler: GlobalErrorHandler;
  let consoleErrorSpy: jest.SpyInstance;

  beforeEach(() => {
    handler = new GlobalErrorHandler();
    consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation();
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it('should log error to console with GlobalErrorHandler prefix when handleError called', () => {
    // given
    const error = new Error('Test error');

    // when
    handler.handleError(error);

    // then
    expect(consoleErrorSpy).toHaveBeenCalledWith('[GlobalErrorHandler]', error);
  });

  it('should log structured error info when error is an Error instance', () => {
    // given
    const error = new Error('Something went wrong');

    // when
    handler.handleError(error);

    // then
    // First call: the prefix log
    expect(consoleErrorSpy).toHaveBeenCalledWith('[GlobalErrorHandler]', error);
    // Second call: structured info with name, message, stack, timestamp
    expect(consoleErrorSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Error',
        message: 'Something went wrong',
        timestamp: expect.any(String),
      })
    );
  });

  it('should handle non-Error objects without throwing', () => {
    // given
    const error = 'string error message';

    // when & then -- should not throw
    expect(() => handler.handleError(error)).not.toThrow();
    expect(consoleErrorSpy).toHaveBeenCalledWith('[GlobalErrorHandler]', error);
  });

  it('should handle HttpErrorResponse without crashing', () => {
    // given
    const httpError = new HttpErrorResponse({
      status: 500,
      statusText: 'Internal Server Error',
    });

    // when & then -- should not throw
    expect(() => handler.handleError(httpError)).not.toThrow();
    expect(consoleErrorSpy).toHaveBeenCalledWith('[GlobalErrorHandler]', httpError);
  });
});
