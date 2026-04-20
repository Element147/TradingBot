import { describe, it, expect, beforeEach, vi } from 'vitest';

import authReducer, {
  setCredentials,
  logout,
  updateActivity,
  checkSessionTimeout,
  restoreSession,
  AuthState,
  User,
} from './authSlice';

describe('authSlice', () => {
  let initialState: AuthState;

  beforeEach(() => {
    initialState = {
      token: null,
      user: null,
      isAuthenticated: false,
      sessionTimeout: null,
      lastActivity: Date.now(),
    };

    // Clear sessionStorage before each test
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  describe('setCredentials', () => {
    it('should set token and user', () => {
      const user: User = { id: '1', username: 'testuser', email: '', role: 'trader' };
      const token = 'test-token-123';

      const state = authReducer(initialState, setCredentials({ token, user }));

      expect(state.token).toBe(token);
      expect(state.user).toEqual(user);
      expect(state.isAuthenticated).toBe(true);
      expect(state.sessionTimeout).toBeGreaterThan(Date.now());
    });

    it('should store token in sessionStorage', () => {
      const user: User = { id: '1', username: 'testuser', email: '', role: 'trader' };
      const token = 'test-token-123';

      authReducer(initialState, setCredentials({ token, user }));

      expect(sessionStorage.getItem('auth_token')).toBe(token);
      expect(sessionStorage.getItem('user')).toBe(JSON.stringify(user));
    });
  });

  describe('logout', () => {
    it('should clear token and user', () => {
      const authenticatedState: AuthState = {
        token: 'test-token',
        user: { id: '1', username: 'testuser', email: '', role: 'trader' },
        isAuthenticated: true,
        sessionTimeout: Date.now() + 1000000,
        lastActivity: Date.now(),
      };

      const state = authReducer(authenticatedState, logout());

      expect(state.token).toBeNull();
      expect(state.user).toBeNull();
      expect(state.isAuthenticated).toBe(false);
      expect(state.sessionTimeout).toBeNull();
    });

    it('should clear sessionStorage', () => {
      sessionStorage.setItem('auth_token', 'test-token');
      sessionStorage.setItem('user', JSON.stringify({ id: '1', username: 'test', role: 'trader' }));

      authReducer(initialState, logout());

      expect(sessionStorage.getItem('auth_token')).toBeNull();
      expect(sessionStorage.getItem('user')).toBeNull();
    });
  });

  describe('updateActivity', () => {
    it('should update lastActivity timestamp', () => {
      const oldTimestamp = Date.now() - 10000;
      const stateWithOldActivity: AuthState = {
        ...initialState,
        lastActivity: oldTimestamp,
      };

      const state = authReducer(stateWithOldActivity, updateActivity());

      expect(state.lastActivity).toBeGreaterThan(oldTimestamp);
    });

    it('should extend sessionTimeout for authenticated users', () => {
      const authenticatedState: AuthState = {
        token: 'test-token',
        user: { id: '1', username: 'testuser', email: '', role: 'trader' },
        isAuthenticated: true,
        sessionTimeout: Date.now() + 1000,
        lastActivity: Date.now() - 10000,
      };

      const state = authReducer(authenticatedState, updateActivity());

      expect(state.sessionTimeout).toBeGreaterThan(authenticatedState.sessionTimeout!);
    });
  });

  describe('checkSessionTimeout', () => {
    it('should logout if session has expired', () => {
      const expiredState: AuthState = {
        token: 'test-token',
        user: { id: '1', username: 'testuser', email: '', role: 'trader' },
        isAuthenticated: true,
        sessionTimeout: Date.now() - 1000, // Expired 1 second ago
        lastActivity: Date.now() - 100000,
      };

      const state = authReducer(expiredState, checkSessionTimeout());

      expect(state.isAuthenticated).toBe(false);
      expect(state.token).toBeNull();
      expect(state.user).toBeNull();
    });

    it('should not logout if session is still valid', () => {
      const validState: AuthState = {
        token: 'test-token',
        user: { id: '1', username: 'testuser', role: 'trader' },
        isAuthenticated: true,
        sessionTimeout: Date.now() + 1000000, // Valid for 1000 seconds
        lastActivity: Date.now(),
      };

      const state = authReducer(validState, checkSessionTimeout());

      expect(state.isAuthenticated).toBe(true);
      expect(state.token).toBe('test-token');
    });
  });

  describe('restoreSession', () => {
    it('should restore session from sessionStorage', () => {
      const user: User = { id: '1', username: 'testuser', email: '', role: 'trader' };
      const token = 'test-token-123';

      sessionStorage.setItem('auth_token', token);
      sessionStorage.setItem('user', JSON.stringify(user));

      const state = authReducer(initialState, restoreSession());

      expect(state.token).toBe(token);
      expect(state.user).toEqual(user);
      expect(state.isAuthenticated).toBe(true);
    });

    it('should not restore if sessionStorage is empty', () => {
      const state = authReducer(initialState, restoreSession());

      expect(state.token).toBeNull();
      expect(state.user).toBeNull();
      expect(state.isAuthenticated).toBe(false);
    });

    it('should handle invalid JSON in sessionStorage', () => {
      sessionStorage.setItem('auth_token', 'test-token');
      sessionStorage.setItem('user', 'invalid-json{');

      const state = authReducer(initialState, restoreSession());

      expect(state.isAuthenticated).toBe(false);
      expect(sessionStorage.getItem('auth_token')).toBeNull();
      expect(sessionStorage.getItem('user')).toBeNull();
    });
  });
});
