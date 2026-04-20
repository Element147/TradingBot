import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

import {
  clearStoredAuth,
  getStoredAuthToken,
  getStoredRefreshToken,
  getStoredUser,
  setStoredAuthToken,
  setStoredRefreshToken,
  setStoredSession,
} from './authStorage';
import { DEV_AUTH_BYPASS_ENABLED, DEV_AUTH_BYPASS_USER } from './devAuth';

export interface User {
  id: string;
  username: string;
  email: string;
  role: 'admin' | 'trader';
}

export interface AuthState {
  token: string | null;
  refreshToken: string | null;
  user: User | null;
  isAuthenticated: boolean;
  sessionTimeout: number | null;
  lastActivity: number;
  loading: boolean;
  error: string | null;
}

const initialState: AuthState = {
  token: null,
  refreshToken: null,
  user: null,
  isAuthenticated: false,
  sessionTimeout: null,
  lastActivity: Date.now(),
  loading: false,
  error: null,
};

// Session timeout: 30 minutes in milliseconds
const SESSION_TIMEOUT_MS = 30 * 60 * 1000;

const parseStoredUser = (userJson: string): User | null => {
  const parsed = JSON.parse(userJson) as Partial<User>;
  if (!parsed.id || typeof parsed.username !== 'string' || parsed.username.trim() === '') {
    return null;
  }

  return {
    id: String(parsed.id),
    username: parsed.username,
    email: parsed.email ?? '',
    role: parsed.role === 'admin' ? 'admin' : 'trader',
  };
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    setCredentials: (
      state,
      action: PayloadAction<{ token: string; user: User; refreshToken?: string }>
    ) => {
      state.token = action.payload.token;
      state.user = action.payload.user;
      state.refreshToken = action.payload.refreshToken || null;
      state.isAuthenticated = true;
      state.lastActivity = Date.now();
      state.sessionTimeout = Date.now() + SESSION_TIMEOUT_MS;
      state.loading = false;
      state.error = null;

      setStoredSession(action.payload.token, action.payload.user);
      setStoredRefreshToken(action.payload.refreshToken ?? null);
    },

    setToken: (state, action: PayloadAction<string>) => {
      state.token = action.payload;
      state.lastActivity = Date.now();
      state.sessionTimeout = Date.now() + SESSION_TIMEOUT_MS;

      setStoredAuthToken(action.payload);
    },

    logout: (state) => {
      state.token = null;
      state.refreshToken = null;
      state.user = null;
      state.isAuthenticated = false;
      state.sessionTimeout = null;
      state.lastActivity = Date.now();
      state.loading = false;
      state.error = null;

      clearStoredAuth();
    },

    setLoading: (state, action: PayloadAction<boolean>) => {
      state.loading = action.payload;
    },

    setError: (state, action: PayloadAction<string | null>) => {
      state.error = action.payload;
      state.loading = false;
    },

    updateActivity: (state) => {
      state.lastActivity = Date.now();
      if (state.isAuthenticated) {
        state.sessionTimeout = Date.now() + SESSION_TIMEOUT_MS;
      }
    },

    checkSessionTimeout: (state) => {
      if (state.isAuthenticated && state.sessionTimeout) {
        if (Date.now() > state.sessionTimeout) {
          // Session expired
          state.token = null;
          state.refreshToken = null;
          state.user = null;
          state.isAuthenticated = false;
          state.sessionTimeout = null;

          clearStoredAuth();
        }
      }
    },

    restoreSession: (state) => {
      if (DEV_AUTH_BYPASS_ENABLED) {
        state.token = null;
        state.refreshToken = null;
        state.user = DEV_AUTH_BYPASS_USER;
        state.isAuthenticated = true;
        state.lastActivity = Date.now();
        state.sessionTimeout = null;
        state.loading = false;
        state.error = null;
        return;
      }

      const token = getStoredAuthToken();
      const userStr = getStoredUser();
      const refreshToken = getStoredRefreshToken();

      if (token && userStr) {
        try {
          const user = parseStoredUser(userStr);
          if (!user) {
            clearStoredAuth();
            return;
          }
          state.token = token;
          state.user = user;
          state.refreshToken = refreshToken;
          state.isAuthenticated = true;
          state.lastActivity = Date.now();
          state.sessionTimeout = Date.now() + SESSION_TIMEOUT_MS;
        } catch {
          clearStoredAuth();
        }
      }
    },

    hydrateDevBypassSession: (state) => {
      state.token = null;
      state.refreshToken = null;
      state.user = DEV_AUTH_BYPASS_USER;
      state.isAuthenticated = true;
      state.lastActivity = Date.now();
      state.sessionTimeout = null;
      state.loading = false;
      state.error = null;
    },
  },
});

export const {
  setCredentials,
  setToken,
  logout,
  setLoading,
  setError,
  updateActivity,
  checkSessionTimeout,
  restoreSession,
  hydrateDevBypassSession,
} = authSlice.actions;

// Selectors
export const selectUser = (state: { auth: AuthState }) => state.auth.user;
export const selectIsAuthenticated = (state: { auth: AuthState }) => state.auth.isAuthenticated;
export const selectToken = (state: { auth: AuthState }) => state.auth.token;
export const selectAuthToken = (state: { auth: AuthState }) => state.auth.token; // Alias for consistency
export const selectAuthLoading = (state: { auth: AuthState }) => state.auth.loading;
export const selectAuthError = (state: { auth: AuthState }) => state.auth.error;

export default authSlice.reducer;
