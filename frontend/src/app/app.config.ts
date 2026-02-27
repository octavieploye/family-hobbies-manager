// frontend/src/app/app.config.ts
import { ApplicationConfig, ErrorHandler } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { GlobalErrorHandler } from './core/error-handling/handlers/global-error.handler';
import { jwtInterceptor } from './core/auth/interceptors/jwt.interceptor';
import { errorInterceptor } from './core/error-handling/interceptors/error.interceptor';
import { authReducer } from './core/auth/store/auth.reducer';
import * as authEffects from './core/auth/store/auth.effects';

/**
 * Root application configuration.
 *
 * Providers registered here are available application-wide:
 * - Router with lazy-loaded routes
 * - HttpClient with JWT and error interceptors
 * - NgRx store with auth state (root level)
 * - GlobalErrorHandler overriding Angular's default ErrorHandler
 * - Animations (async to avoid loading animation code eagerly)
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter([]),
    provideHttpClient(
      withInterceptors([jwtInterceptor, errorInterceptor])
    ),
    provideAnimationsAsync(),
    provideStore({ auth: authReducer }),
    provideEffects(authEffects),
    { provide: ErrorHandler, useClass: GlobalErrorHandler },
  ],
};
