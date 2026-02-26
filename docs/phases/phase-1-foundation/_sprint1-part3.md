> **DEPRECATED** — This file is superseded by `sprint-1-security.md` (the single authoritative Sprint 1 document).
> Do NOT use this file for implementation. It contains outdated JJWT versions, wrong Liquibase formats, and deprecated API patterns.
> Kept for historical reference only.

---

### Story S1-006: Implement Angular Auth Module

**Points**: 8 | **Priority**: P0 | **Epic**: Frontend / Security

#### Context

The Angular frontend needs login and registration forms, a JWT interceptor that attaches tokens
to every API request, an auth guard that protects routes, and NgRx state management for auth
state. When a 401 response arrives, the interceptor automatically attempts a token refresh
before forcing logout.

Angular 17+ standalone components are used throughout -- there are no `NgModule` classes. The
test runner is Jest (not Karma/Jasmine). NgRx manages auth state so that any component can
observe authentication status reactively. Angular Material provides the form field components.

#### Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|---------------|---------------|
| 1 | Create auth models | `frontend/src/app/core/auth/models/auth.models.ts` | TypeScript interfaces | Compiles |
| 2 | Create AuthService | `frontend/src/app/core/auth/services/auth.service.ts` | HTTP service for auth endpoints | Compiles |
| 3 | Create JWT interceptor | `frontend/src/app/core/auth/interceptors/jwt.interceptor.ts` | HttpInterceptorFn | Compiles |
| 4 | Create auth guard | `frontend/src/app/core/auth/guards/auth.guard.ts` | CanActivateFn | Compiles |
| 5 | Create LoginComponent | `frontend/src/app/features/auth/login/login.component.ts` | Reactive form | `ng build` succeeds |
| 6 | Create RegisterComponent | `frontend/src/app/features/auth/register/register.component.ts` | Reactive form | `ng build` succeeds |
| 7 | Create NgRx auth state | `frontend/src/app/core/auth/store/` | Actions, reducer, effects, selectors | Compiles |
| 8 | Create auth routes | `frontend/src/app/features/auth/auth.routes.ts` | Lazy-loaded auth routes | Routes resolve |
| 9 | Create proxy config | `frontend/proxy.conf.json` | Dev proxy to gateway | Proxy works |
| 10 | Update app config | `frontend/src/app/app.config.ts` | Register interceptor + store | App bootstraps |

---

#### Task 1 Detail: Create Auth Models

**What**: TypeScript interfaces that define the shapes of all authentication-related objects:
the login request payload, the register request payload, the server's authentication response,
and the NgRx auth state slice. Every auth-related file imports types from this single file.

**Where**: `frontend/src/app/core/auth/models/auth.models.ts`

**Why**: Centralizing type definitions prevents duplication and ensures that the Angular
frontend agrees with the backend API contract. If the backend changes the auth response shape,
only this file needs to be updated.

**Content**:

```typescript
// frontend/src/app/core/auth/models/auth.models.ts

/**
 * Payload sent to POST /api/v1/auth/login.
 */
export interface LoginRequest {
  email: string;
  password: string;
}

/**
 * Payload sent to POST /api/v1/auth/register.
 * phone is optional -- the backend accepts null/undefined.
 */
export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  phone?: string;
}

/**
 * Response from POST /api/v1/auth/login, /register, and /refresh.
 * Maps directly to the Java AuthResponse record on the backend.
 */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

/**
 * Shape of the NgRx auth state slice.
 * Stored in the global store under the 'auth' feature key.
 */
export interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  loading: boolean;
  error: string | null;
}
```

**Verify**:

```bash
cd frontend && npx tsc --noEmit
# Expected: no compilation errors
```

---

#### Task 2 Detail: Create AuthService

**What**: An injectable Angular service that encapsulates all HTTP calls to the authentication
endpoints (`/api/v1/auth/login`, `/register`, `/refresh`) and manages token persistence in
`localStorage`. The service is `providedIn: 'root'` so it is a singleton shared across the
entire application.

**Where**: `frontend/src/app/core/auth/services/auth.service.ts`

**Why**: Centralizing HTTP calls and token management in one service prevents scattered
`localStorage` access and ensures a single source of truth for the current token state.
The NgRx effects and the JWT interceptor both delegate to this service.

**Content**:

```typescript
// frontend/src/app/core/auth/services/auth.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Router } from '@angular/router';
import {
  LoginRequest,
  RegisterRequest,
  AuthResponse,
} from '../models/auth.models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API_BASE = '/api/v1/auth';
  private readonly ACCESS_TOKEN_KEY = 'access_token';
  private readonly REFRESH_TOKEN_KEY = 'refresh_token';

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router
  ) {}

  /**
   * Authenticate user with email and password.
   * On success the tokens are stored in localStorage by the NgRx effect,
   * but this method also stores them as a fallback for non-NgRx consumers.
   */
  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_BASE}/login`, request);
  }

  /**
   * Register a new family account.
   */
  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API_BASE}/register`, request);
  }

  /**
   * Exchange a valid refresh token for a new token pair.
   * Called by the JWT interceptor when a 401 is received.
   */
  refreshToken(): Observable<AuthResponse> {
    const refreshToken = this.getRefreshToken();
    return this.http
      .post<AuthResponse>(`${this.API_BASE}/refresh`, { refreshToken })
      .pipe(
        tap((response) => {
          this.storeTokens(response.accessToken, response.refreshToken);
        })
      );
  }

  /**
   * Clear tokens from localStorage and redirect to login.
   */
  logout(): void {
    localStorage.removeItem(this.ACCESS_TOKEN_KEY);
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    this.router.navigate(['/auth/login']);
  }

  /**
   * Persist both tokens in localStorage.
   * Called by NgRx effects after successful login/register.
   */
  storeTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(this.ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
  }

  /**
   * Read the current access token from localStorage.
   * Returns null if no token is stored.
   */
  getAccessToken(): string | null {
    return localStorage.getItem(this.ACCESS_TOKEN_KEY);
  }

  /**
   * Read the current refresh token from localStorage.
   * Returns null if no token is stored.
   */
  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  /**
   * Quick check whether a token exists in localStorage.
   * Does NOT validate the token signature or expiry -- that is the backend's job.
   */
  isAuthenticated(): boolean {
    return this.getAccessToken() !== null;
  }
}
```

**Verify**:

```bash
cd frontend && npx tsc --noEmit
# Expected: no compilation errors
```

---

#### Task 3 Detail: Create JWT Interceptor

**What**: An Angular 17+ functional HTTP interceptor (`HttpInterceptorFn`) that attaches the
`Authorization: Bearer <token>` header to every outgoing API request, except for public paths
(login, register, refresh). When the server responds with 401 Unauthorized, the interceptor
attempts a token refresh. If the refresh succeeds, it retries the original request with the
new token. If the refresh fails, it forces a logout and redirects to the login page.

**Where**: `frontend/src/app/core/auth/interceptors/jwt.interceptor.ts`

**Why**: Without this interceptor, every component making HTTP calls would need to manually
attach the token. Centralizing token attachment and refresh logic here ensures that no
authenticated request is ever sent without a token, and that expired tokens are transparently
refreshed without disrupting the user experience.

The code below is based on the reference in `docs/architecture/05-security-architecture.md`
section 12.3, adapted for full production use.

**Content**:

```typescript
// frontend/src/app/core/auth/interceptors/jwt.interceptor.ts
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, switchMap, throwError } from 'rxjs';

/**
 * Functional HTTP interceptor (Angular 17+ style).
 *
 * Responsibilities:
 * 1. Attach Bearer token to every non-public request.
 * 2. On 401: attempt token refresh, then retry the original request.
 * 3. On refresh failure: force logout and redirect to /auth/login.
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getAccessToken();

  // Paths that must NOT carry an Authorization header.
  // These are public endpoints that the backend allows without JWT.
  const publicPaths = ['/auth/login', '/auth/register', '/auth/refresh'];
  const isPublicRequest = publicPaths.some((path) => req.url.includes(path));

  if (isPublicRequest) {
    return next(req);
  }

  // Clone the request and attach the Bearer token if available
  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Only attempt refresh if the error is 401 and this is NOT already a refresh request
      if (error.status === 401 && !req.url.includes('/auth/refresh')) {
        return authService.refreshToken().pipe(
          switchMap((response) => {
            // Retry the original request with the new access token
            const retryReq = req.clone({
              setHeaders: {
                Authorization: `Bearer ${response.accessToken}`,
              },
            });
            return next(retryReq);
          }),
          catchError((refreshError) => {
            // Refresh failed -- force logout and let the user re-authenticate
            authService.logout();
            return throwError(() => refreshError);
          })
        );
      }
      return throwError(() => error);
    })
  );
};
```

**Verify**:

```bash
cd frontend && npx tsc --noEmit
# Expected: no compilation errors
```

---

#### Task 4 Detail: Create Auth Guard

**What**: An Angular 17+ functional route guard (`CanActivateFn`) that checks whether the
user is authenticated before allowing navigation to a protected route. If the user is not
authenticated, the guard redirects to `/auth/login` and appends a `returnUrl` query parameter
so the login page can redirect back after successful authentication.

**Where**: `frontend/src/app/core/auth/guards/auth.guard.ts`

**Why**: Without this guard, unauthenticated users could navigate directly to protected pages
(e.g., `/dashboard`, `/families`) by typing the URL in the browser. The guard enforces the
authentication requirement at the routing level before the component even loads.

**Content**:

```typescript
// frontend/src/app/core/auth/guards/auth.guard.ts
import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * Functional route guard (Angular 17+ style).
 *
 * Usage in route config:
 *   { path: 'dashboard', canActivate: [authGuard], component: DashboardComponent }
 *
 * Behavior:
 * - Returns true if the user has a token in localStorage.
 * - Redirects to /auth/login with returnUrl query param if not authenticated.
 */
export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Preserve the attempted URL so the login page can redirect back after success
  return router.createUrlTree(['/auth/login'], {
    queryParams: { returnUrl: state.url },
  });
};
```

**Verify**:

```bash
cd frontend && npx tsc --noEmit
# Expected: no compilation errors
```

---

#### Task 5 Detail: Create LoginComponent

**What**: A standalone Angular component with a reactive form for user login. It dispatches
NgRx actions for login, observes loading and error state from the store, and displays
validation messages using Angular Material form fields. It links to the registration page
for new users.

**Where**: Three files:
- `frontend/src/app/features/auth/login/login.component.ts` (component class + metadata)
- `frontend/src/app/features/auth/login/login.component.html` (template)
- `frontend/src/app/features/auth/login/login.component.scss` (styles)

**Why**: The login form is the primary entry point for authenticated users. Using NgRx instead
of calling AuthService directly keeps the component thin and makes the auth flow testable
without HTTP dependencies.

**Content (component class)**:

```typescript
// frontend/src/app/features/auth/login/login.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';

// Angular Material imports
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';

import * as AuthActions from '../../../core/auth/store/auth.actions';
import {
  selectAuthLoading,
  selectAuthError,
} from '../../../core/auth/store/auth.selectors';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss'],
})
export class LoginComponent implements OnInit {
  loginForm!: FormGroup;
  loading$!: Observable<boolean>;
  error$!: Observable<string | null>;
  hidePassword = true;

  constructor(
    private readonly fb: FormBuilder,
    private readonly store: Store
  ) {}

  ngOnInit(): void {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]],
    });

    this.loading$ = this.store.select(selectAuthLoading);
    this.error$ = this.store.select(selectAuthError);
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    const { email, password } = this.loginForm.value;
    this.store.dispatch(AuthActions.login({ email, password }));
  }
}
```

**Content (template)**:

```html
<!-- frontend/src/app/features/auth/login/login.component.html -->
<div class="login-container">
  <mat-card class="login-card">
    <mat-card-header>
      <mat-card-title>Connexion</mat-card-title>
      <mat-card-subtitle>Connectez-vous pour acceder a votre espace famille</mat-card-subtitle>
    </mat-card-header>

    <mat-card-content>
      <!-- Error message from NgRx store -->
      @if (error$ | async; as errorMessage) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ errorMessage }}</span>
        </div>
      }

      <form [formGroup]="loginForm" (ngSubmit)="onSubmit()">
        <!-- Email field -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Adresse email</mat-label>
          <input
            matInput
            formControlName="email"
            type="email"
            placeholder="exemple@email.com"
            autocomplete="email"
          />
          @if (loginForm.get('email')?.hasError('required') && loginForm.get('email')?.touched) {
            <mat-error>L'adresse email est obligatoire</mat-error>
          }
          @if (loginForm.get('email')?.hasError('email') && loginForm.get('email')?.touched) {
            <mat-error>Format d'email invalide</mat-error>
          }
        </mat-form-field>

        <!-- Password field -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Mot de passe</mat-label>
          <input
            matInput
            formControlName="password"
            [type]="hidePassword ? 'password' : 'text'"
            autocomplete="current-password"
          />
          <button
            mat-icon-button
            matSuffix
            type="button"
            (click)="hidePassword = !hidePassword"
            [attr.aria-label]="hidePassword ? 'Afficher le mot de passe' : 'Masquer le mot de passe'"
          >
            <mat-icon>{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          @if (loginForm.get('password')?.hasError('required') && loginForm.get('password')?.touched) {
            <mat-error>Le mot de passe est obligatoire</mat-error>
          }
        </mat-form-field>

        <!-- Submit button -->
        <button
          mat-raised-button
          color="primary"
          type="submit"
          class="full-width submit-button"
          [disabled]="(loading$ | async)"
        >
          @if (loading$ | async) {
            <mat-spinner diameter="20"></mat-spinner>
          } @else {
            Se connecter
          }
        </button>
      </form>
    </mat-card-content>

    <mat-card-actions align="end">
      <a mat-button routerLink="/auth/register" color="accent">
        Pas encore de compte ? S'inscrire
      </a>
    </mat-card-actions>
  </mat-card>
</div>
```

**Content (styles)**:

```scss
// frontend/src/app/features/auth/login/login.component.scss
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  padding: 16px;
}

.login-card {
  max-width: 440px;
  width: 100%;
}

.full-width {
  width: 100%;
}

.submit-button {
  margin-top: 16px;
  height: 48px;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background-color: #fdecea;
  border-radius: 4px;
  color: #611a15;

  mat-icon {
    color: #f44336;
  }
}
```

**Verify**:

```bash
cd frontend && ng build
# Expected: BUILD SUCCESS -- no compilation errors
```

---

#### Task 6 Detail: Create RegisterComponent

**What**: A standalone Angular component with a reactive form for new user registration.
Fields: email, password, confirmPassword, firstName, lastName, phone (optional). The
component validates that password and confirmPassword match using a custom validator.
It dispatches NgRx actions for registration and shows loading/error state from the store.

**Where**: Three files:
- `frontend/src/app/features/auth/register/register.component.ts`
- `frontend/src/app/features/auth/register/register.component.html`
- `frontend/src/app/features/auth/register/register.component.scss`

**Why**: Registration is the first interaction a new user has with the platform. A clear,
validated form with immediate feedback prevents frustration and reduces invalid API calls.
The confirmPassword cross-field validator catches mistyped passwords before they reach
the server.

**Content (component class)**:

```typescript
// frontend/src/app/features/auth/register/register.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule,
  FormBuilder,
  FormGroup,
  Validators,
  AbstractControl,
  ValidationErrors,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs';

// Angular Material imports
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';

import * as AuthActions from '../../../core/auth/store/auth.actions';
import {
  selectAuthLoading,
  selectAuthError,
} from '../../../core/auth/store/auth.selectors';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    MatIconModule,
  ],
  templateUrl: './register.component.html',
  styleUrls: ['./register.component.scss'],
})
export class RegisterComponent implements OnInit {
  registerForm!: FormGroup;
  loading$!: Observable<boolean>;
  error$!: Observable<string | null>;
  hidePassword = true;
  hideConfirmPassword = true;

  constructor(
    private readonly fb: FormBuilder,
    private readonly store: Store
  ) {}

  ngOnInit(): void {
    this.registerForm = this.fb.group(
      {
        email: ['', [Validators.required, Validators.email]],
        password: ['', [Validators.required, Validators.minLength(8)]],
        confirmPassword: ['', [Validators.required]],
        firstName: ['', [Validators.required]],
        lastName: ['', [Validators.required]],
        phone: [''],
      },
      {
        validators: [RegisterComponent.passwordMatchValidator],
      }
    );

    this.loading$ = this.store.select(selectAuthLoading);
    this.error$ = this.store.select(selectAuthError);
  }

  /**
   * Cross-field validator: confirmPassword must match password.
   * Applied at the FormGroup level, not on individual controls.
   */
  static passwordMatchValidator(control: AbstractControl): ValidationErrors | null {
    const password = control.get('password')?.value;
    const confirmPassword = control.get('confirmPassword')?.value;

    if (password && confirmPassword && password !== confirmPassword) {
      return { passwordMismatch: true };
    }
    return null;
  }

  onSubmit(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    const { email, password, firstName, lastName, phone } =
      this.registerForm.value;

    this.store.dispatch(
      AuthActions.register({
        email,
        password,
        firstName,
        lastName,
        phone: phone || undefined,
      })
    );
  }
}
```

**Content (template)**:

```html
<!-- frontend/src/app/features/auth/register/register.component.html -->
<div class="register-container">
  <mat-card class="register-card">
    <mat-card-header>
      <mat-card-title>Inscription</mat-card-title>
      <mat-card-subtitle>Creez votre compte famille</mat-card-subtitle>
    </mat-card-header>

    <mat-card-content>
      <!-- Error message from NgRx store -->
      @if (error$ | async; as errorMessage) {
        <div class="error-banner" role="alert">
          <mat-icon>error_outline</mat-icon>
          <span>{{ errorMessage }}</span>
        </div>
      }

      <form [formGroup]="registerForm" (ngSubmit)="onSubmit()">
        <!-- First name -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Prenom</mat-label>
          <input matInput formControlName="firstName" autocomplete="given-name" />
          @if (registerForm.get('firstName')?.hasError('required') && registerForm.get('firstName')?.touched) {
            <mat-error>Le prenom est obligatoire</mat-error>
          }
        </mat-form-field>

        <!-- Last name -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Nom de famille</mat-label>
          <input matInput formControlName="lastName" autocomplete="family-name" />
          @if (registerForm.get('lastName')?.hasError('required') && registerForm.get('lastName')?.touched) {
            <mat-error>Le nom est obligatoire</mat-error>
          }
        </mat-form-field>

        <!-- Email -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Adresse email</mat-label>
          <input
            matInput
            formControlName="email"
            type="email"
            placeholder="exemple@email.com"
            autocomplete="email"
          />
          @if (registerForm.get('email')?.hasError('required') && registerForm.get('email')?.touched) {
            <mat-error>L'adresse email est obligatoire</mat-error>
          }
          @if (registerForm.get('email')?.hasError('email') && registerForm.get('email')?.touched) {
            <mat-error>Format d'email invalide</mat-error>
          }
        </mat-form-field>

        <!-- Password -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Mot de passe</mat-label>
          <input
            matInput
            formControlName="password"
            [type]="hidePassword ? 'password' : 'text'"
            autocomplete="new-password"
          />
          <button
            mat-icon-button
            matSuffix
            type="button"
            (click)="hidePassword = !hidePassword"
            [attr.aria-label]="hidePassword ? 'Afficher le mot de passe' : 'Masquer le mot de passe'"
          >
            <mat-icon>{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          @if (registerForm.get('password')?.hasError('required') && registerForm.get('password')?.touched) {
            <mat-error>Le mot de passe est obligatoire</mat-error>
          }
          @if (registerForm.get('password')?.hasError('minlength') && registerForm.get('password')?.touched) {
            <mat-error>Le mot de passe doit contenir au moins 8 caracteres</mat-error>
          }
        </mat-form-field>

        <!-- Confirm password -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Confirmer le mot de passe</mat-label>
          <input
            matInput
            formControlName="confirmPassword"
            [type]="hideConfirmPassword ? 'password' : 'text'"
            autocomplete="new-password"
          />
          <button
            mat-icon-button
            matSuffix
            type="button"
            (click)="hideConfirmPassword = !hideConfirmPassword"
            [attr.aria-label]="hideConfirmPassword ? 'Afficher le mot de passe' : 'Masquer le mot de passe'"
          >
            <mat-icon>{{ hideConfirmPassword ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
          @if (registerForm.get('confirmPassword')?.hasError('required') && registerForm.get('confirmPassword')?.touched) {
            <mat-error>La confirmation est obligatoire</mat-error>
          }
          @if (registerForm.hasError('passwordMismatch') && registerForm.get('confirmPassword')?.touched) {
            <mat-error>Les mots de passe ne correspondent pas</mat-error>
          }
        </mat-form-field>

        <!-- Phone (optional) -->
        <mat-form-field appearance="outline" class="full-width">
          <mat-label>Telephone (optionnel)</mat-label>
          <input matInput formControlName="phone" type="tel" autocomplete="tel" />
        </mat-form-field>

        <!-- Submit button -->
        <button
          mat-raised-button
          color="primary"
          type="submit"
          class="full-width submit-button"
          [disabled]="(loading$ | async)"
        >
          @if (loading$ | async) {
            <mat-spinner diameter="20"></mat-spinner>
          } @else {
            Creer mon compte
          }
        </button>
      </form>
    </mat-card-content>

    <mat-card-actions align="end">
      <a mat-button routerLink="/auth/login" color="accent">
        Deja un compte ? Se connecter
      </a>
    </mat-card-actions>
  </mat-card>
</div>
```

**Content (styles)**:

```scss
// frontend/src/app/features/auth/register/register.component.scss
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 80vh;
  padding: 16px;
}

.register-card {
  max-width: 520px;
  width: 100%;
}

.full-width {
  width: 100%;
}

.submit-button {
  margin-top: 16px;
  height: 48px;
}

.error-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
  background-color: #fdecea;
  border-radius: 4px;
  color: #611a15;

  mat-icon {
    color: #f44336;
  }
}
```

**Verify**:

```bash
cd frontend && ng build
# Expected: BUILD SUCCESS -- no compilation errors
```

---

#### Task 7 Detail: Create NgRx Auth State

**What**: Four files that implement the NgRx state management for authentication: actions,
reducer, effects, and selectors. Together they manage the login/register/refresh/logout
lifecycle, persist tokens to localStorage, restore tokens on app start, and expose reactive
observables that components subscribe to.

**Where**: `frontend/src/app/core/auth/store/` (four files)

**Why**: NgRx provides a single source of truth for auth state. Components never call
AuthService directly -- they dispatch actions and select state. This makes the auth flow
predictable, testable, and debuggable (via Redux DevTools). Effects handle side effects
(HTTP calls, localStorage, navigation) outside the component layer.

---

**File 1: auth.actions.ts**

```typescript
// frontend/src/app/core/auth/store/auth.actions.ts
import { createAction, props } from '@ngrx/store';
import { AuthResponse } from '../models/auth.models';

// --- Login ---
export const login = createAction(
  '[Auth] Login',
  props<{ email: string; password: string }>()
);

export const loginSuccess = createAction(
  '[Auth] Login Success',
  props<{ response: AuthResponse }>()
);

export const loginFailure = createAction(
  '[Auth] Login Failure',
  props<{ error: string }>()
);

// --- Register ---
export const register = createAction(
  '[Auth] Register',
  props<{
    email: string;
    password: string;
    firstName: string;
    lastName: string;
    phone?: string;
  }>()
);

export const registerSuccess = createAction(
  '[Auth] Register Success',
  props<{ response: AuthResponse }>()
);

export const registerFailure = createAction(
  '[Auth] Register Failure',
  props<{ error: string }>()
);

// --- Refresh ---
export const refresh = createAction('[Auth] Refresh');

export const refreshSuccess = createAction(
  '[Auth] Refresh Success',
  props<{ response: AuthResponse }>()
);

export const refreshFailure = createAction(
  '[Auth] Refresh Failure',
  props<{ error: string }>()
);

// --- Logout ---
export const logout = createAction('[Auth] Logout');

// --- Init (restore tokens from localStorage on app start) ---
export const initAuth = createAction(
  '[Auth] Init',
  props<{ accessToken: string | null; refreshToken: string | null }>()
);
```

---

**File 2: auth.reducer.ts**

```typescript
// frontend/src/app/core/auth/store/auth.reducer.ts
import { createReducer, on } from '@ngrx/store';
import { AuthState } from '../models/auth.models';
import * as AuthActions from './auth.actions';

export const initialState: AuthState = {
  accessToken: null,
  refreshToken: null,
  isAuthenticated: false,
  loading: false,
  error: null,
};

export const authReducer = createReducer(
  initialState,

  // --- Login ---
  on(AuthActions.login, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AuthActions.loginSuccess, (state, { response }) => ({
    ...state,
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    isAuthenticated: true,
    loading: false,
    error: null,
  })),

  on(AuthActions.loginFailure, (state, { error }) => ({
    ...state,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    loading: false,
    error,
  })),

  // --- Register ---
  on(AuthActions.register, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(AuthActions.registerSuccess, (state, { response }) => ({
    ...state,
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    isAuthenticated: true,
    loading: false,
    error: null,
  })),

  on(AuthActions.registerFailure, (state, { error }) => ({
    ...state,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    loading: false,
    error,
  })),

  // --- Refresh ---
  on(AuthActions.refreshSuccess, (state, { response }) => ({
    ...state,
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    isAuthenticated: true,
    error: null,
  })),

  on(AuthActions.refreshFailure, (state, { error }) => ({
    ...state,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    error,
  })),

  // --- Logout ---
  on(AuthActions.logout, () => ({
    ...initialState,
  })),

  // --- Init (restore from localStorage) ---
  on(AuthActions.initAuth, (state, { accessToken, refreshToken }) => ({
    ...state,
    accessToken,
    refreshToken,
    isAuthenticated: accessToken !== null,
  }))
);
```

---

**File 3: auth.effects.ts**

```typescript
// frontend/src/app/core/auth/store/auth.effects.ts
import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { Actions, createEffect, ofType, OnInitEffects } from '@ngrx/effects';
import { Action } from '@ngrx/store';
import { of } from 'rxjs';
import { catchError, exhaustMap, map, tap } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import * as AuthActions from './auth.actions';

@Injectable()
export class AuthEffects implements OnInitEffects {
  constructor(
    private readonly actions$: Actions,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  /**
   * On app start, restore tokens from localStorage into the store.
   * This runs once when the effects are initialized.
   */
  ngrxOnInitEffects(): Action {
    const accessToken = this.authService.getAccessToken();
    const refreshToken = this.authService.getRefreshToken();
    return AuthActions.initAuth({ accessToken, refreshToken });
  }

  /**
   * Login effect: call AuthService.login(), store tokens, navigate to dashboard.
   * Uses exhaustMap to ignore duplicate login dispatches while one is in flight.
   */
  login$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.login),
      exhaustMap((action) =>
        this.authService.login({ email: action.email, password: action.password }).pipe(
          map((response) => AuthActions.loginSuccess({ response })),
          catchError((error) =>
            of(
              AuthActions.loginFailure({
                error:
                  error.error?.message ||
                  'Identifiants incorrects. Veuillez reessayer.',
              })
            )
          )
        )
      )
    )
  );

  /**
   * On login success: store tokens in localStorage and navigate to dashboard.
   */
  loginSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.loginSuccess),
        tap(({ response }) => {
          this.authService.storeTokens(
            response.accessToken,
            response.refreshToken
          );
          this.router.navigate(['/dashboard']);
        })
      ),
    { dispatch: false }
  );

  /**
   * Register effect: call AuthService.register(), store tokens, navigate to dashboard.
   */
  register$ = createEffect(() =>
    this.actions$.pipe(
      ofType(AuthActions.register),
      exhaustMap((action) =>
        this.authService
          .register({
            email: action.email,
            password: action.password,
            firstName: action.firstName,
            lastName: action.lastName,
            phone: action.phone,
          })
          .pipe(
            map((response) => AuthActions.registerSuccess({ response })),
            catchError((error) =>
              of(
                AuthActions.registerFailure({
                  error:
                    error.error?.message ||
                    'Erreur lors de l\'inscription. Veuillez reessayer.',
                })
              )
            )
          )
      )
    )
  );

  /**
   * On register success: store tokens in localStorage and navigate to dashboard.
   */
  registerSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.registerSuccess),
        tap(({ response }) => {
          this.authService.storeTokens(
            response.accessToken,
            response.refreshToken
          );
          this.router.navigate(['/dashboard']);
        })
      ),
    { dispatch: false }
  );

  /**
   * Logout effect: clear localStorage and redirect to login.
   */
  logout$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(AuthActions.logout),
        tap(() => {
          this.authService.logout();
        })
      ),
    { dispatch: false }
  );
}
```

---

**File 4: auth.selectors.ts**

```typescript
// frontend/src/app/core/auth/store/auth.selectors.ts
import { createFeatureSelector, createSelector } from '@ngrx/store';
import { AuthState } from '../models/auth.models';

/**
 * Feature selector for the 'auth' state slice.
 * The feature key 'auth' must match the key used when registering
 * the reducer in the app config (provideStore({ auth: authReducer })).
 */
export const selectAuthState = createFeatureSelector<AuthState>('auth');

/**
 * Select whether the user is currently authenticated.
 * Used by guards, nav components, and conditional UI elements.
 */
export const selectIsAuthenticated = createSelector(
  selectAuthState,
  (state) => state.isAuthenticated
);

/**
 * Select the current access token.
 * Used by the JWT interceptor as a fallback (normally reads from localStorage).
 */
export const selectAccessToken = createSelector(
  selectAuthState,
  (state) => state.accessToken
);

/**
 * Select the loading flag.
 * Used by login/register components to show a spinner.
 */
export const selectAuthLoading = createSelector(
  selectAuthState,
  (state) => state.loading
);

/**
 * Select the error message.
 * Used by login/register components to display error banners.
 */
export const selectAuthError = createSelector(
  selectAuthState,
  (state) => state.error
);
```

**Verify**:

```bash
cd frontend && npx tsc --noEmit
# Expected: no compilation errors for any of the 4 store files
```

---

#### Task 8 Detail: Create Auth Routes

**What**: A route configuration file that defines the lazy-loaded routes for the auth feature.
Login and Register components are loaded on-demand using dynamic `import()` so they are not
included in the main bundle.

**Where**: `frontend/src/app/features/auth/auth.routes.ts`

**Why**: Lazy loading keeps the initial bundle small. Users who are already authenticated never
download the login/register code. The routes file is referenced from the main `app.routes.ts`
via `loadChildren`.

**Content**:

```typescript
// frontend/src/app/features/auth/auth.routes.ts
import { Routes } from '@angular/router';

export const AUTH_ROUTES: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./register/register.component').then(
        (m) => m.RegisterComponent
      ),
  },
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full',
  },
];
```

**Verify**:

```bash
cd frontend && ng build
# Expected: BUILD SUCCESS -- routes resolve, lazy chunks generated
```

---

#### Task 9 Detail: Create Proxy Config

**What**: Angular CLI proxy configuration file that redirects all `/api` requests from the
Angular dev server (port 4200) to the API Gateway (port 8080) during local development.

**Where**: `frontend/proxy.conf.json`

**Why**: During development, the Angular CLI dev server runs on `http://localhost:4200` and the
API Gateway runs on `http://localhost:8080`. Without the proxy, browser Same-Origin Policy
blocks cross-origin requests. The proxy transparently forwards `/api` requests from 4200 to
8080, making the frontend behave as if the API is served from the same origin. In production,
Nginx handles this routing instead.

**Content**:

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false,
    "changeOrigin": true
  }
}
```

The `angular.json` (or `project.json`) `serve` target must reference this file:

```json
"serve": {
  "options": {
    "proxyConfig": "proxy.conf.json"
  }
}
```

**Verify**:

```bash
cd frontend && ng serve --proxy-config proxy.conf.json
# Expected: Angular dev server starts with proxy to localhost:8080
# All /api/* requests are forwarded to the API Gateway
# Press Ctrl+C to stop
```

---

#### Task 10 Detail: Update App Config

**What**: Update the Angular application configuration to register the JWT interceptor in the
HTTP client pipeline and set up the NgRx store with the auth reducer and effects.

**Where**: `frontend/src/app/app.config.ts`

**Why**: Angular 17+ standalone applications use `ApplicationConfig` instead of `AppModule`.
All providers must be registered here. Without `provideHttpClient(withInterceptors(...))`, the
JWT interceptor does not execute. Without `provideStore(...)` and `provideEffects(...)`, NgRx
is not initialized and dispatched actions go nowhere.

**Content**:

```typescript
// frontend/src/app/app.config.ts
import { ApplicationConfig } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideStore } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';

import { routes } from './app.routes';
import { jwtInterceptor } from './core/auth/interceptors/jwt.interceptor';
import { authReducer } from './core/auth/store/auth.reducer';
import { AuthEffects } from './core/auth/store/auth.effects';

export const appConfig: ApplicationConfig = {
  providers: [
    // Routing
    provideRouter(routes),

    // HTTP client with JWT interceptor
    provideHttpClient(withInterceptors([jwtInterceptor])),

    // Animations (required by Angular Material)
    provideAnimationsAsync(),

    // NgRx Store -- auth feature registered at root level
    provideStore({ auth: authReducer }),

    // NgRx Effects -- auth effects handle login/register/logout side effects
    provideEffects([AuthEffects]),
  ],
};
```

**Verify**:

```bash
cd frontend && ng build
# Expected: BUILD SUCCESS -- app bootstraps with interceptor and NgRx store
```

---

#### Failing Tests (TDD Contract)

All tests use **Jest** (not Karma/Jasmine). The `describe`/`it`/`expect` API is Jest-native.

---

**Test File 1**: `frontend/src/app/core/auth/services/auth.service.spec.ts`

**What**: Five unit tests for AuthService that verify HTTP calls are made to the correct
endpoints and that localStorage operations work correctly.

**Why**: AuthService is the foundation of the auth system -- every test that follows depends
on it working correctly. These tests use `HttpClientTestingModule` to intercept HTTP requests
without a real server.

```typescript
// frontend/src/app/core/auth/services/auth.service.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { AuthResponse } from '../models/auth.models';

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
```

**Run**:

```bash
cd frontend && npx jest --testPathPattern="auth.service.spec"
# Expected before implementation: 5 tests FAIL (import errors — AuthService does not exist yet)
# Expected after implementation: 5 tests PASS
```

---

**Test File 2**: `frontend/src/app/core/auth/interceptors/jwt.interceptor.spec.ts`

**What**: Four unit tests for the JWT interceptor that verify it attaches tokens to requests,
skips public paths, attempts refresh on 401, and forces logout when refresh fails.

**Why**: The interceptor is the gatekeeper for all HTTP traffic. A bug here could leak tokens
to public endpoints, fail to refresh expired tokens, or leave users stuck in a broken auth
state. These tests use `HttpClientTestingModule` to simulate server responses.

```typescript
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
```

**Run**:

```bash
cd frontend && npx jest --testPathPattern="jwt.interceptor.spec"
# Expected before implementation: 4 tests FAIL (import errors — interceptor does not exist yet)
# Expected after implementation: 4 tests PASS
```

---

**Test File 3**: `frontend/src/app/core/auth/guards/auth.guard.spec.ts`

**What**: Two unit tests for the auth guard that verify it allows authenticated users and
redirects unauthenticated users to the login page with a returnUrl.

**Why**: The guard is the last line of defense before a protected component renders. These
tests verify both the happy path (authenticated user passes through) and the redirect path
(unauthenticated user is sent to login).

```typescript
// frontend/src/app/core/auth/guards/auth.guard.spec.ts
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let authServiceSpy: jest.Mocked<AuthService>;
  let routerSpy: jest.Mocked<Router>;

  const mockRoute = {} as ActivatedRouteSnapshot;

  beforeEach(() => {
    authServiceSpy = {
      isAuthenticated: jest.fn(),
      getAccessToken: jest.fn(),
      getRefreshToken: jest.fn(),
      login: jest.fn(),
      register: jest.fn(),
      refreshToken: jest.fn(),
      logout: jest.fn(),
      storeTokens: jest.fn(),
    } as unknown as jest.Mocked<AuthService>;

    routerSpy = {
      navigate: jest.fn(),
      createUrlTree: jest.fn(),
    } as unknown as jest.Mocked<Router>;

    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    });
  });

  it('shouldAllowAuthenticatedUser', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(true);

    const mockState = { url: '/dashboard' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRoute, mockState)
    );

    expect(result).toBe(true);
  });

  it('shouldRedirectUnauthenticatedUser', () => {
    authServiceSpy.isAuthenticated.mockReturnValue(false);

    const expectedUrlTree = {} as UrlTree;
    routerSpy.createUrlTree.mockReturnValue(expectedUrlTree);

    const mockState = { url: '/dashboard' } as RouterStateSnapshot;

    const result = TestBed.runInInjectionContext(() =>
      authGuard(mockRoute, mockState)
    );

    expect(routerSpy.createUrlTree).toHaveBeenCalledWith(['/auth/login'], {
      queryParams: { returnUrl: '/dashboard' },
    });
    expect(result).toBe(expectedUrlTree);
  });
});
```

**Run**:

```bash
cd frontend && npx jest --testPathPattern="auth.guard.spec"
# Expected before implementation: 2 tests FAIL (import errors — guard does not exist yet)
# Expected after implementation: 2 tests PASS
```

---

**Test File 4**: `frontend/src/app/core/auth/store/auth.reducer.spec.ts`

**What**: Five unit tests for the auth reducer that verify it produces the correct state
for each action: initial state, login success, login failure, logout, and init auth.

**Why**: The reducer is a pure function -- given a state and an action, it returns a new state.
These tests verify the state transitions that the entire UI depends on. If the reducer is
wrong, every component observing auth state will display incorrect information.

```typescript
// frontend/src/app/core/auth/store/auth.reducer.spec.ts
import { authReducer, initialState } from './auth.reducer';
import * as AuthActions from './auth.actions';
import { AuthState } from '../models/auth.models';

describe('authReducer', () => {
  it('shouldReturnInitialState', () => {
    const state = authReducer(undefined, { type: 'UNKNOWN' });

    expect(state).toEqual(initialState);
    expect(state.isAuthenticated).toBe(false);
    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('loginSuccess_shouldSetTokenAndAuthenticated', () => {
    const response = {
      accessToken: 'jwt-token',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
      expiresIn: 3600,
    };

    const state = authReducer(
      initialState,
      AuthActions.loginSuccess({ response })
    );

    expect(state.accessToken).toBe('jwt-token');
    expect(state.refreshToken).toBe('refresh-token');
    expect(state.isAuthenticated).toBe(true);
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('loginFailure_shouldSetError', () => {
    // First set loading to true via login action
    const loadingState = authReducer(initialState, AuthActions.login({ email: 'a@b.com', password: 'p' }));
    expect(loadingState.loading).toBe(true);

    // Then simulate failure
    const state = authReducer(
      loadingState,
      AuthActions.loginFailure({ error: 'Invalid credentials' })
    );

    expect(state.isAuthenticated).toBe(false);
    expect(state.accessToken).toBeNull();
    expect(state.loading).toBe(false);
    expect(state.error).toBe('Invalid credentials');
  });

  it('logout_shouldClearState', () => {
    // Start from an authenticated state
    const authenticatedState: AuthState = {
      accessToken: 'jwt-token',
      refreshToken: 'refresh-token',
      isAuthenticated: true,
      loading: false,
      error: null,
    };

    const state = authReducer(authenticatedState, AuthActions.logout());

    expect(state.accessToken).toBeNull();
    expect(state.refreshToken).toBeNull();
    expect(state.isAuthenticated).toBe(false);
    expect(state.loading).toBe(false);
    expect(state.error).toBeNull();
  });

  it('initAuth_shouldRestoreFromPayload', () => {
    const state = authReducer(
      initialState,
      AuthActions.initAuth({
        accessToken: 'stored-token',
        refreshToken: 'stored-refresh',
      })
    );

    expect(state.accessToken).toBe('stored-token');
    expect(state.refreshToken).toBe('stored-refresh');
    expect(state.isAuthenticated).toBe(true);
  });
});
```

**Run**:

```bash
cd frontend && npx jest --testPathPattern="auth.reducer.spec"
# Expected before implementation: 5 tests FAIL (import errors — reducer does not exist yet)
# Expected after implementation: 5 tests PASS
```

---

### Story S1-007: Implement Kafka Event Classes

**Points**: 3 | **Priority**: P1 | **Epic**: Shared Library

#### Context

Event classes define the Kafka message contracts between services. Sprint 1 creates the event
POJOs in the common library. The actual Kafka infrastructure (broker, topics, producer/consumer
config) is added in Sprint 5. For now, these are just serializable Java classes that can be
converted to JSON by Jackson.

The `DomainEvent` base class carries metadata common to all events: a unique event ID, the event
type string, the timestamp, and a schema version. Concrete event classes extend `DomainEvent`
and add domain-specific fields.

#### Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|---------------|---------------|
| 1 | Create DomainEvent base | `backend/common/src/main/java/com/familyhobbies/common/event/DomainEvent.java` | Abstract event base | Compiles |
| 2 | Create UserRegisteredEvent | `backend/common/src/main/java/com/familyhobbies/common/event/UserRegisteredEvent.java` | Registration event | Compiles |
| 3 | Create UserDeletedEvent | `backend/common/src/main/java/com/familyhobbies/common/event/UserDeletedEvent.java` | Deletion event | Compiles |

---

#### Task 1 Detail: Create DomainEvent Base Class

**What**: Abstract base class for all Kafka domain events. Carries four metadata fields:
`eventId` (UUID, auto-generated on construction), `eventType` (String, set by subclasses),
`occurredAt` (Instant, auto-set to `Instant.now()`), and `version` (int, defaults to 1).
Uses Lombok annotations for boilerplate reduction.

**Where**: `backend/common/src/main/java/com/familyhobbies/common/event/DomainEvent.java`

**Why**: Every event published to Kafka must carry unique identification (`eventId`) for
deduplication, a timestamp for ordering and debugging, and a version number for schema
evolution. Putting these in a base class ensures every event gets them automatically and
prevents subclasses from forgetting required fields.

**Content**:

```java
package com.familyhobbies.common.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Abstract base class for all domain events published to Kafka.
 *
 * Every event carries:
 * - eventId: unique identifier for deduplication
 * - eventType: human-readable type string (e.g., "USER_REGISTERED")
 * - occurredAt: timestamp when the event was created
 * - version: schema version for forward/backward compatibility
 *
 * Subclasses set eventType in their constructor.
 * Jackson serializes these to JSON for Kafka messages.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public abstract class DomainEvent {

    private UUID eventId;
    private String eventType;
    private Instant occurredAt;
    private int version;

    /**
     * Constructor for subclasses. Auto-generates eventId and occurredAt.
     * Subclasses pass only the eventType string.
     *
     * @param eventType human-readable event type (e.g., "USER_REGISTERED")
     */
    protected DomainEvent(String eventType) {
        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.occurredAt = Instant.now();
        this.version = 1;
    }
}
```

**Verify**:

```bash
cd backend && mvn compile -pl common -q
# Expected: compiles without error
```

---

#### Task 2 Detail: Create UserRegisteredEvent

**What**: Concrete event class published when a new user registers. Extends `DomainEvent`
and adds fields: `userId` (Long), `email` (String), `firstName` (String), `lastName` (String).
The constructor sets `eventType` to `"USER_REGISTERED"`.

**Where**: `backend/common/src/main/java/com/familyhobbies/common/event/UserRegisteredEvent.java`

**Why**: When a user registers in user-service, notification-service needs to know so it can
send a welcome email. The event decouples user-service from notification-service -- user-service
publishes the event to Kafka without knowing who consumes it.

**Content**:

```java
package com.familyhobbies.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Published to Kafka when a new user registers.
 *
 * Consumers:
 * - notification-service: sends welcome email
 * - (future) analytics: tracks registration funnel
 *
 * Topic: user-events
 * Key: userId (for partition ordering)
 */
@Getter
@Setter
@NoArgsConstructor
public class UserRegisteredEvent extends DomainEvent {

    private Long userId;
    private String email;
    private String firstName;
    private String lastName;

    /**
     * Construct a UserRegisteredEvent with all required fields.
     * eventType is automatically set to "USER_REGISTERED".
     * eventId and occurredAt are auto-generated by DomainEvent.
     */
    public UserRegisteredEvent(Long userId, String email,
                                String firstName, String lastName) {
        super("USER_REGISTERED");
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
```

**Verify**:

```bash
cd backend && mvn compile -pl common -q
# Expected: compiles without error
```

---

#### Task 3 Detail: Create UserDeletedEvent

**What**: Concrete event class published when a user requests account deletion (RGPD right
to be forgotten). Extends `DomainEvent` and adds fields: `userId` (Long) and `deletionType`
(String -- either `"SOFT"` for anonymization or `"HARD"` for full deletion). The constructor
sets `eventType` to `"USER_DELETED"`.

**Where**: `backend/common/src/main/java/com/familyhobbies/common/event/UserDeletedEvent.java`

**Why**: When a user requests deletion, all downstream services must clean up their data.
The event carries only `userId` and `deletionType` -- each service decides its own retention
policy (payment-service anonymizes but retains records for 10 years; notification-service
deletes immediately).

**Content**:

```java
package com.familyhobbies.common.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Published to Kafka when a user requests account deletion (RGPD).
 *
 * Consumers:
 * - association-service: anonymizes subscriptions, attendance records
 * - payment-service: anonymizes payment records (retains 10 years for tax compliance)
 * - notification-service: deletes notification log and preferences
 *
 * Topic: user-events
 * Key: userId (for partition ordering)
 */
@Getter
@Setter
@NoArgsConstructor
public class UserDeletedEvent extends DomainEvent {

    private Long userId;

    /**
     * Deletion type:
     * - "SOFT": user record is anonymized (PII hashed), but the row remains.
     * - "HARD": user record is permanently deleted from the database.
     */
    private String deletionType;

    /**
     * Construct a UserDeletedEvent with all required fields.
     * eventType is automatically set to "USER_DELETED".
     * eventId and occurredAt are auto-generated by DomainEvent.
     */
    public UserDeletedEvent(Long userId, String deletionType) {
        super("USER_DELETED");
        this.userId = userId;
        this.deletionType = deletionType;
    }
}
```

**Verify**:

```bash
cd backend && mvn compile -pl common -q
# Expected: compiles without error
```

---

#### Failing Tests (TDD Contract)

**Test File**: `backend/common/src/test/java/com/familyhobbies/common/event/DomainEventTest.java`

**What**: Five tests that verify the event classes carry correct metadata, auto-generate IDs
and timestamps, and serialize to JSON via Jackson's `ObjectMapper`.

**Why**: Events are the inter-service communication contract. If serialization breaks,
services cannot communicate. If `eventId` is not auto-generated, deduplication in consumers
fails. These tests lock down the contract before any Kafka infrastructure is built.

```java
package com.familyhobbies.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void userRegisteredEvent_shouldHaveCorrectEventType() {
        UserRegisteredEvent event = new UserRegisteredEvent(
            1L, "jean@example.com", "Jean", "Dupont");

        assertEquals("USER_REGISTERED", event.getEventType());
        assertEquals(1L, event.getUserId());
        assertEquals("jean@example.com", event.getEmail());
        assertEquals("Jean", event.getFirstName());
        assertEquals("Dupont", event.getLastName());
    }

    @Test
    void userRegisteredEvent_shouldAutoGenerateEventId() {
        UserRegisteredEvent event = new UserRegisteredEvent(
            1L, "jean@example.com", "Jean", "Dupont");

        assertNotNull(event.getEventId(),
            "eventId should be auto-generated by DomainEvent constructor");
    }

    @Test
    void userRegisteredEvent_shouldAutoSetTimestamp() {
        UserRegisteredEvent event = new UserRegisteredEvent(
            1L, "jean@example.com", "Jean", "Dupont");

        assertNotNull(event.getOccurredAt(),
            "occurredAt should be auto-set to Instant.now() by DomainEvent constructor");
        assertEquals(1, event.getVersion(),
            "version should default to 1");
    }

    @Test
    void userDeletedEvent_shouldHaveCorrectEventType() {
        UserDeletedEvent event = new UserDeletedEvent(42L, "SOFT");

        assertEquals("USER_DELETED", event.getEventType());
        assertEquals(42L, event.getUserId());
        assertEquals("SOFT", event.getDeletionType());
    }

    @Test
    void eventsShouldSerializeToJson() throws Exception {
        UserRegisteredEvent event = new UserRegisteredEvent(
            1L, "jean@example.com", "Jean", "Dupont");

        String json = objectMapper.writeValueAsString(event);

        assertNotNull(json, "Serialized JSON should not be null");
        assertTrue(json.contains("\"eventType\":\"USER_REGISTERED\""),
            "JSON should contain the eventType field");
        assertTrue(json.contains("\"userId\":1"),
            "JSON should contain the userId field");
        assertTrue(json.contains("\"email\":\"jean@example.com\""),
            "JSON should contain the email field");
        assertTrue(json.contains("\"eventId\""),
            "JSON should contain the eventId field");
        assertTrue(json.contains("\"occurredAt\""),
            "JSON should contain the occurredAt field");

        // Verify deserialization round-trip
        UserRegisteredEvent deserialized = objectMapper.readValue(
            json, UserRegisteredEvent.class);
        assertEquals(event.getEventId(), deserialized.getEventId());
        assertEquals(event.getUserId(), deserialized.getUserId());
        assertEquals(event.getEmail(), deserialized.getEmail());
    }
}
```

**Run**:

```bash
cd backend && mvn test -pl common -Dtest=DomainEventTest
# Expected before implementation: 5 tests FAIL (compile errors — event classes do not exist yet)
# Expected after implementation: 5 tests PASS
```

---

### Story S1-008: Implement CORS Configuration

**Points**: 2 | **Priority**: P1 | **Epic**: Security / Infrastructure

#### Context

The Angular development server runs on `http://localhost:4200`, while the API Gateway runs
on `http://localhost:8080`. Without CORS configuration, the browser blocks cross-origin API
calls with a preflight failure. CORS is configured **only at the gateway level** -- downstream
services never serve browser requests directly.

In Story S1-004, the `SecurityConfig` already calls `corsConfigurationSource()` with hardcoded
values. This story externalizes those values to `application.yml` via `@ConfigurationProperties`
so that different environments (development, production) can have different allowed origins
without changing code.

#### Tasks

| # | Task | File Path | What To Create | How To Verify |
|---|------|-----------|---------------|---------------|
| 1 | Create CorsProperties | `backend/api-gateway/src/main/java/com/familyhobbies/apigateway/config/CorsProperties.java` | @ConfigurationProperties for CORS | Compiles |
| 2 | Update SecurityConfig | `backend/api-gateway/src/main/java/com/familyhobbies/apigateway/config/SecurityConfig.java` | Inject CorsProperties into corsConfigurationSource() | Compiles |
| 3 | Update gateway application.yml | `backend/api-gateway/src/main/resources/application.yml` | Add cors config section | Gateway starts |

---

#### Task 1 Detail: Create CorsProperties

**What**: A Spring `@ConfigurationProperties` class that binds CORS configuration from
`application.yml` to a type-safe Java object. Fields: `allowedOrigins` (List of Strings),
`allowedMethods` (List of Strings), `allowedHeaders` (List of Strings), `exposedHeaders`
(List of Strings), `allowCredentials` (boolean), `maxAge` (long).

**Where**: `backend/api-gateway/src/main/java/com/familyhobbies/apigateway/config/CorsProperties.java`

**Why**: Hardcoding CORS values in `SecurityConfig` means changing allowed origins requires
a code change and redeploy. With `@ConfigurationProperties`, the values come from
`application.yml` (or environment variables) and can differ per environment without code
changes.

**Content**:

```java
package com.familyhobbies.apigateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Binds CORS configuration from application.yml.
 *
 * Example YAML:
 *   cors:
 *     allowed-origins:
 *       - http://localhost:4200
 *     allowed-methods:
 *       - GET
 *       - POST
 *     ...
 *
 * Spring Boot automatically converts kebab-case YAML keys to camelCase fields.
 */
@Configuration
@ConfigurationProperties(prefix = "cors")
@Getter
@Setter
public class CorsProperties {

    /**
     * Allowed origins for cross-origin requests.
     * Development: http://localhost:4200
     * Production: https://familyhobbies.fr
     */
    private List<String> allowedOrigins;

    /**
     * Allowed HTTP methods.
     * Typically: GET, POST, PUT, PATCH, DELETE, OPTIONS.
     */
    private List<String> allowedMethods;

    /**
     * Headers that the browser is allowed to send in the actual request.
     */
    private List<String> allowedHeaders;

    /**
     * Headers that the browser is allowed to read from the response.
     */
    private List<String> exposedHeaders;

    /**
     * Whether the browser should send credentials (cookies, Authorization header)
     * with cross-origin requests.
     */
    private boolean allowCredentials;

    /**
     * How long (in seconds) the browser should cache the preflight response.
     * 3600 = 1 hour. Reduces preflight OPTIONS requests.
     */
    private long maxAge;
}
```

**Verify**:

```bash
cd backend && mvn compile -pl api-gateway -q
# Expected: compiles without error
```

---

#### Task 2 Detail: Update SecurityConfig to Use CorsProperties

**What**: Modify the existing `SecurityConfig.corsConfigurationSource()` method to read values
from the injected `CorsProperties` bean instead of hardcoded lists. This makes the CORS
configuration driven entirely by `application.yml`.

**Where**: `backend/api-gateway/src/main/java/com/familyhobbies/apigateway/config/SecurityConfig.java`

**Why**: The `SecurityConfig` created in S1-004 hardcodes allowed origins and methods. This
task replaces those hardcoded values with `CorsProperties` fields, completing the externalization.

**Content**:

```java
package com.familyhobbies.apigateway.config;

import com.familyhobbies.apigateway.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            // Disable CSRF -- stateless JWT-based API, no session cookies
            .csrf(ServerHttpSecurity.CsrfSpec::disable)

            // CORS -- values from application.yml via CorsProperties
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Disable form login and HTTP basic -- JWT only
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)

            // Authorization rules
            .authorizeExchange(exchanges -> exchanges
                // Public endpoints -- no authentication required
                .pathMatchers("/api/v1/auth/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/associations/**").permitAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/activities/**").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/v1/associations/search").permitAll()
                .pathMatchers("/api/v1/payments/webhook/**").permitAll()
                .pathMatchers("/actuator/health", "/actuator/info").permitAll()

                // Admin-only endpoints
                .pathMatchers("/api/v1/users/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.POST, "/api/v1/associations/sync").hasRole("ADMIN")
                .pathMatchers("/api/v1/notifications/templates/**").hasRole("ADMIN")
                .pathMatchers("/actuator/metrics", "/actuator/prometheus").hasRole("ADMIN")
                .pathMatchers("/api/v1/rgpd/audit-log").hasRole("ADMIN")

                // Association manager endpoints
                .pathMatchers("/api/v1/associations/*/subscribers").hasAnyRole("ASSOCIATION", "ADMIN")
                .pathMatchers("/api/v1/attendance/report").hasAnyRole("ASSOCIATION", "ADMIN")
                .pathMatchers("/api/v1/payments/association/**").hasAnyRole("ASSOCIATION", "ADMIN")

                // All other endpoints require authentication (any role)
                .anyExchange().authenticated()
            )

            // Add JWT filter before the authentication filter
            .addFilterBefore(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(corsProperties.getAllowedMethods());
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setExposedHeaders(corsProperties.getExposedHeaders());
        config.setAllowCredentials(corsProperties.isAllowCredentials());
        config.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

**Verify**:

```bash
cd backend && mvn compile -pl api-gateway -q
# Expected: compiles without error
```

---

#### Task 3 Detail: Update Gateway application.yml with CORS Section

**What**: Add the `cors` configuration section to the existing API Gateway `application.yml`.
The values match the requirements from `docs/architecture/05-security-architecture.md` section 7.

**Where**: `backend/api-gateway/src/main/resources/application.yml`

**Why**: `CorsProperties` reads from the `cors` prefix in application.yml. Without this
section, all `CorsProperties` fields would be `null`, and the `CorsConfigurationSource` bean
would have no allowed origins -- blocking all cross-origin requests.

**Content**:

Add the following block to the **existing** gateway `application.yml` (below the `jwt` section):

```yaml
cors:
  allowed-origins:
    - http://localhost:4200
  allowed-methods:
    - GET
    - POST
    - PUT
    - PATCH
    - DELETE
    - OPTIONS
  allowed-headers:
    - Authorization
    - Content-Type
    - X-Requested-With
  exposed-headers:
    - X-Total-Count
    - X-Correlation-Id
  allow-credentials: true
  max-age: 3600
```

The full `application.yml` after this change:

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        # --- user-service routes (Sprint 0) ---
        - id: auth-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/auth/**

        - id: user-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**

        - id: family-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/families/**

        - id: rgpd-routes
          uri: lb://user-service
          predicates:
            - Path=/api/v1/rgpd/**

        # --- association-service routes added in Sprint 2 ---
        # --- payment-service routes added in Sprint 5 ---
        # --- notification-service routes added in Sprint 6 ---

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

jwt:
  secret: ${JWT_SECRET:default-dev-secret-that-is-at-least-256-bits-long-for-hmac-sha256}

cors:
  allowed-origins:
    - http://localhost:4200
  allowed-methods:
    - GET
    - POST
    - PUT
    - PATCH
    - DELETE
    - OPTIONS
  allowed-headers:
    - Authorization
    - Content-Type
    - X-Requested-With
  exposed-headers:
    - X-Total-Count
    - X-Correlation-Id
  allow-credentials: true
  max-age: 3600
```

**Verify**:

```bash
cd backend/api-gateway && mvn spring-boot:run
# Expected: starts on :8080 without errors
# CORS headers are applied to /api/** requests
# Press Ctrl+C to stop
```

---

#### Failing Tests (TDD Contract)

**Test File**: `backend/api-gateway/src/test/java/com/familyhobbies/apigateway/config/CorsConfigTest.java`

**What**: Two integration tests that verify CORS headers are present on responses: one for
preflight OPTIONS requests and one for actual cross-origin GET requests.

**Why**: CORS misconfiguration silently breaks the Angular frontend. Browsers send a preflight
OPTIONS request before any cross-origin POST/PUT/DELETE. If the OPTIONS response does not
include the correct `Access-Control-Allow-*` headers, the browser aborts the actual request.
These tests catch misconfigurations before they reach the browser.

```java
package com.familyhobbies.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "cors.allowed-origins=http://localhost:4200",
        "cors.allowed-methods=GET,POST,PUT,PATCH,DELETE,OPTIONS",
        "cors.allowed-headers=Authorization,Content-Type,X-Requested-With",
        "cors.exposed-headers=X-Total-Count,X-Correlation-Id",
        "cors.allow-credentials=true",
        "cors.max-age=3600",
        "eureka.client.enabled=false"
    }
)
@AutoConfigureWebTestClient
class CorsConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void preflightOptions_shouldReturnCorsHeaders() {
        webTestClient.options()
            .uri("/api/v1/auth/login")
            .header(HttpHeaders.ORIGIN, "http://localhost:4200")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type")
            .exchange()
            .expectStatus().isOk()
            .expectHeader().valueEquals(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
            .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)
            .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)
            .expectHeader().valueEquals(
                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
            .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_MAX_AGE);
    }

    @Test
    void crossOriginGet_shouldIncludeCorsHeaders() {
        webTestClient.get()
            .uri("/api/v1/auth/login")
            .header(HttpHeaders.ORIGIN, "http://localhost:4200")
            .exchange()
            // The actual status depends on the route handler (405 for GET on a POST-only endpoint),
            // but CORS headers should still be present regardless of the response status.
            .expectHeader().valueEquals(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200")
            .expectHeader().valueEquals(
                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    }
}
```

**Run**:

```bash
cd backend && mvn test -pl api-gateway -Dtest=CorsConfigTest
# Expected before implementation: 2 tests FAIL (CorsProperties does not exist yet)
# Expected after implementation: 2 tests PASS
```

---

## Sprint Verification Checklist

Run these commands in order to verify the entire sprint is complete:

```bash
# Prerequisites: Sprint 0 must be passing
cd docker && docker compose up -d
cd backend && mvn clean package -DskipTests
cd backend/error-handling && mvn test  # 44 tests pass

# 1. User-service migrations run (creates t_user, t_refresh_token)
cd backend/user-service && mvn spring-boot:run &
sleep 15
docker exec fhm-postgres psql -U fhm_admin -d familyhobbies_users \
  -c "SELECT tablename FROM pg_tables WHERE schemaname='public'" | grep t_user
# Expected: t_user and t_refresh_token listed

# 2. Register a new user
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecureP@ss1","firstName":"Jean","lastName":"Dupont"}' \
  | jq .accessToken
# Expected: JWT token string returned

# 3. Login with the registered user
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecureP@ss1"}' \
  | jq .accessToken
# Expected: JWT token string returned

# 4. Access protected endpoint with token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecureP@ss1"}' | jq -r .accessToken)

curl -s -X GET http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer $TOKEN"
# Expected: 200 OK with user profile (or appropriate response)

# 5. Verify 401 without token
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/families
# Expected: 401

# 6. Verify public path works without token
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/auth/login
# Expected: 405 (Method Not Allowed — POST only, but NOT 401)

# 7. Run all backend tests
cd backend && mvn test
# Expected: All tests pass (error-handling: 44, user-service: ~24, api-gateway: ~5, common: ~10)

# 8. Angular builds successfully
cd frontend && npm install && ng build
# Expected: BUILD SUCCESS

# 9. Angular tests pass
cd frontend && npm test
# Expected: All Jest tests pass

# 10. Cleanup
kill %1 %2 %3
cd docker && docker compose down
```
