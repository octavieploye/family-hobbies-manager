// frontend/src/app/core/auth/index.ts
// Barrel export for the auth module

// Models
export {
  LoginRequest,
  RegisterRequest,
  AuthResponse,
  AuthState,
} from './models/auth.models';

// Services
export { AuthService } from './services/auth.service';
export { TokenStorageService } from './services/token-storage.service';

// Guards
export { authGuard } from './guards/auth.guard';

// Interceptors
export { jwtInterceptor } from './interceptors/jwt.interceptor';

// Store — Actions
export * as AuthActions from './store/auth.actions';

// Store — Reducer
export { authReducer, initialState as authInitialState } from './store/auth.reducer';

// Store — Selectors
export {
  selectAuthState,
  selectIsAuthenticated,
  selectAccessToken,
  selectAuthLoading,
  selectAuthError,
} from './store/auth.selectors';

// Store — Effects
export * as AuthEffects from './store/auth.effects';
