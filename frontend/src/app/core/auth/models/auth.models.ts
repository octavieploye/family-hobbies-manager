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
