import { ErrorHandler, Injectable } from '@angular/core';

@Injectable()
export class GlobalErrorHandler implements ErrorHandler {
  handleError(error: unknown): void {
    // Log to console in development
    console.error('[GlobalErrorHandler]', error);

    // In production, this would send to a monitoring service (e.g., Sentry, Graylog)
    // For now, just log structured error info
    if (error instanceof Error) {
      console.error({
        name: error.name,
        message: error.message,
        stack: error.stack,
        timestamp: new Date().toISOString(),
      });
    }
  }
}
