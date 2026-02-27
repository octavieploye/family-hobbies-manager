import * as AuthActions from './auth.actions';

/**
 * Unit tests for auth NgRx actions.
 *
 * Tests: 11 test methods
 *
 * These tests verify each action creator produces the correct type string
 * and carries the expected payload/props.
 */
describe('Auth Actions', () => {
  describe('login', () => {
    it('should create login action with correct type and payload', () => {
      // when
      const action = AuthActions.login({ email: 'test@example.com', password: 'password123' });

      // then
      expect(action.type).toBe('[Auth] Login');
      expect(action.email).toBe('test@example.com');
      expect(action.password).toBe('password123');
    });

    it('should create loginSuccess action with correct type and response payload', () => {
      // given
      const response = {
        accessToken: 'jwt-token',
        refreshToken: 'refresh-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
      };

      // when
      const action = AuthActions.loginSuccess({ response });

      // then
      expect(action.type).toBe('[Auth] Login Success');
      expect(action.response).toEqual(response);
    });

    it('should create loginFailure action with correct type and error payload', () => {
      // when
      const action = AuthActions.loginFailure({ error: 'Invalid credentials' });

      // then
      expect(action.type).toBe('[Auth] Login Failure');
      expect(action.error).toBe('Invalid credentials');
    });
  });

  describe('register', () => {
    it('should create register action with correct type and all fields', () => {
      // when
      const action = AuthActions.register({
        email: 'new@example.com',
        password: 'password123',
        firstName: 'Jean',
        lastName: 'Dupont',
        phone: '+33612345678',
      });

      // then
      expect(action.type).toBe('[Auth] Register');
      expect(action.email).toBe('new@example.com');
      expect(action.password).toBe('password123');
      expect(action.firstName).toBe('Jean');
      expect(action.lastName).toBe('Dupont');
      expect(action.phone).toBe('+33612345678');
    });

    it('should create registerSuccess action with correct type and response payload', () => {
      // given
      const response = {
        accessToken: 'jwt-token',
        refreshToken: 'refresh-token',
        tokenType: 'Bearer',
        expiresIn: 3600,
      };

      // when
      const action = AuthActions.registerSuccess({ response });

      // then
      expect(action.type).toBe('[Auth] Register Success');
      expect(action.response).toEqual(response);
    });

    it('should create registerFailure action with correct type and error payload', () => {
      // when
      const action = AuthActions.registerFailure({ error: 'Email already exists' });

      // then
      expect(action.type).toBe('[Auth] Register Failure');
      expect(action.error).toBe('Email already exists');
    });
  });

  describe('refresh', () => {
    it('should create refresh action with correct type and no payload', () => {
      // when
      const action = AuthActions.refresh();

      // then
      expect(action.type).toBe('[Auth] Refresh');
    });

    it('should create refreshSuccess action with correct type and response payload', () => {
      // given
      const response = {
        accessToken: 'new-jwt',
        refreshToken: 'new-refresh',
        tokenType: 'Bearer',
        expiresIn: 3600,
      };

      // when
      const action = AuthActions.refreshSuccess({ response });

      // then
      expect(action.type).toBe('[Auth] Refresh Success');
      expect(action.response).toEqual(response);
    });

    it('should create refreshFailure action with correct type and error payload', () => {
      // when
      const action = AuthActions.refreshFailure({ error: 'Token expired' });

      // then
      expect(action.type).toBe('[Auth] Refresh Failure');
      expect(action.error).toBe('Token expired');
    });
  });

  describe('logout', () => {
    it('should create logout action with correct type and no payload', () => {
      // when
      const action = AuthActions.logout();

      // then
      expect(action.type).toBe('[Auth] Logout');
    });
  });

  describe('initAuth', () => {
    it('should create initAuth action with correct type and token payload', () => {
      // when
      const action = AuthActions.initAuth({
        accessToken: 'stored-token',
        refreshToken: 'stored-refresh',
      });

      // then
      expect(action.type).toBe('[Auth] Init');
      expect(action.accessToken).toBe('stored-token');
      expect(action.refreshToken).toBe('stored-refresh');
    });
  });
});
