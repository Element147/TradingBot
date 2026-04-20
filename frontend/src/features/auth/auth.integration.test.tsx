import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { authApi } from './authApi';
import { logout } from './authSlice';
import LoginPage from './LoginPage';

import { server } from '@/tests/mocks/server';
import { renderWithProviders } from '@/tests/test-utils';

vi.mock('@/components/ui/FieldTooltip', () => ({
  FieldTooltip: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

// Mock useNavigate at module level
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

/**
 * Integration Tests for Authentication Flow
 * 
 * Tests the complete authentication flow including:
 * - Login with valid/invalid credentials
 * - Token storage and session management
 * - Token refresh on 401 errors
 * - Logout and session cleanup
 * 
 * Uses MSW to mock backend API responses
 * 
 * Requirements: 30.3
 */
describe('Authentication Flow Integration Tests', { timeout: 15000 }, () => {
  beforeEach(() => {
    // Clear storage before each test
    localStorage.clear();
    sessionStorage.clear();
    
    // Clear navigation mock
    mockNavigate.mockClear();
  });

  describe('Login Flow', () => {
    it('should login with valid credentials, store token, and redirect to dashboard', async () => {
      const user = userEvent.setup();

      const { store } = renderWithProviders(<LoginPage />);

      // Fill in login form with valid credentials
      const usernameInput = screen.getByLabelText(/username/i);
      const passwordInput = screen.getByLabelText(/password/i);
      const submitButton = screen.getByRole('button', { name: /sign in/i });

      await user.clear(usernameInput);
      await user.clear(passwordInput);
      await user.type(usernameInput, 'testuser');
      await user.type(passwordInput, 'password123');

      await waitFor(() => {
        expect(usernameInput).toHaveValue('testuser');
        expect(passwordInput).toHaveValue('password123');
      });

      await user.click(submitButton);

      // Wait for login to complete
      await waitFor(() => {
        const state = store.getState();
        expect(state.auth.isAuthenticated).toBe(true);
      });

      // Verify token is stored in Redux state
      const state = store.getState();
      expect(state.auth.token).toBe('mock-jwt-token-12345');
      expect(state.auth.user).toEqual(expect.objectContaining({
        id: 'user-123',
        username: 'testuser',
        role: 'trader',
      }));

      // Verify token is stored in sessionStorage
      expect(sessionStorage.getItem('auth_token')).toBe('mock-jwt-token-12345');
      const sessionUser = JSON.parse(sessionStorage.getItem('user') ?? '{}') as {
        id?: string;
        username?: string;
        role?: string;
      };
      expect(sessionUser).toEqual(expect.objectContaining({
        id: 'user-123',
        username: 'testuser',
        role: 'trader',
      }));

      // Verify no refresh token in localStorage (remember me not checked)
      expect(localStorage.getItem('refresh_token')).toBeNull();

      // Verify navigation to dashboard was called
      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
      });
    });

    it('should store refresh token when "remember me" is checked', async () => {
      const user = userEvent.setup();

      const { store } = renderWithProviders(<LoginPage />);

      // Fill in login form with valid credentials
      const usernameInput = screen.getByLabelText(/username/i);
      const passwordInput = screen.getByLabelText(/password/i);
      const rememberMeCheckbox = screen.getByRole('checkbox', { name: /remember me/i });
      const submitButton = screen.getByRole('button', { name: /sign in/i });

      await user.clear(usernameInput);
      await user.clear(passwordInput);
      await user.type(usernameInput, 'testuser');
      await user.type(passwordInput, 'password123');

      await waitFor(() => {
        expect(usernameInput).toHaveValue('testuser');
        expect(passwordInput).toHaveValue('password123');
      });

      await user.click(rememberMeCheckbox);

      // Ensure checkbox state is committed before submit to avoid timing flakes
      await waitFor(() => {
        expect(rememberMeCheckbox).toBeChecked();
      });

      await user.click(submitButton);

      // Wait for login to complete
      await waitFor(() => {
        const state = store.getState();
        expect(state.auth.isAuthenticated).toBe(true);
      });

      // Verify refresh token is stored in localStorage
      await waitFor(() => {
        expect(localStorage.getItem('refresh_token')).toBe('mock-refresh-token-67890');
      });

      // Verify navigation to dashboard was called
      expect(mockNavigate).toHaveBeenCalledWith('/dashboard');
    });

    it('should show error message when login fails with invalid credentials', async () => {
      const user = userEvent.setup();

      renderWithProviders(<LoginPage />);

      // Fill in login form with invalid credentials
      const usernameInput = screen.getByLabelText(/username/i);
      const passwordInput = screen.getByLabelText(/password/i);
      const submitButton = screen.getByRole('button', { name: /sign in/i });

      await user.type(usernameInput, 'wronguser');
      await user.type(passwordInput, 'wrongpassword');
      await user.click(submitButton);

      // Wait for error message to appear
      await waitFor(() => {
        expect(screen.getByText(/invalid username or password/i)).toBeInTheDocument();
      });

      // Verify no token is stored
      expect(sessionStorage.getItem('auth_token')).toBeNull();
      expect(localStorage.getItem('refresh_token')).toBeNull();
    });

    it('should display loading state during login', async () => {
      const user = userEvent.setup();

      // Add delay to login endpoint to capture loading state
      server.use(
        http.post('http://localhost:8080/api/auth/login', async () => {
          // Delay response to capture loading state
          await new Promise(resolve => setTimeout(resolve, 100));
          return HttpResponse.json({
            token: 'mock-jwt-token-12345',
            user: {
              id: 'user-123',
              username: 'testuser',
              role: 'trader',
            },
            expiresIn: 3600,
          });
        })
      );

      renderWithProviders(<LoginPage />);

      const usernameInput = screen.getByLabelText(/username/i);
      const passwordInput = screen.getByLabelText(/password/i);
      const submitButton = screen.getByRole('button', { name: /sign in/i });

      await user.type(usernameInput, 'testuser');
      await user.type(passwordInput, 'password123');
      
      // Click submit and immediately check for loading state
      await user.click(submitButton);

      // Check for loading state (button should show "Signing in..." and be disabled)
      await waitFor(() => {
        expect(screen.getByText(/signing in/i)).toBeInTheDocument();
      });
      expect(submitButton).toBeDisabled();
    });
  });

  describe('Token Refresh on 401 Error', () => {
    it('should automatically refresh token when API returns 401 and retry request', async () => {
      // Set up initial authenticated state with refresh token
      const preloadedState = {
        auth: {
          token: 'mock-jwt-token-expired',
          refreshToken: 'mock-refresh-token-67890',
          user: {
            id: 'user-123',
            username: 'testuser',
            role: 'trader' as const,
          },
          isAuthenticated: true,
          sessionTimeout: Date.now() + 30 * 60 * 1000,
          lastActivity: Date.now(),
          loading: false,
          error: null,
        },
      };

      const { store } = renderWithProviders(<div>Test Component</div>, {
        preloadedState,
      });

      // Store refresh token in localStorage
      localStorage.setItem('refresh_token', 'mock-refresh-token-67890');

      // Make a request to protected endpoint that will return 401
      const result = await store.dispatch(
        authApi.endpoints.getMe.initiate()
      );

      // Wait for token refresh and retry
      await waitFor(() => {
        const state = store.getState();
        // Token should be updated to the refreshed token
        expect(state.auth.token).toBe('mock-jwt-token-refreshed');
      });

      // Verify the request eventually succeeded after refresh
      expect(result.data).toBeDefined();
    });

    it('should logout user when token refresh fails', async () => {
      // Mock window.location.href
      delete (window as any).location;
      window.location = { href: '' } as any;

      // Set up initial authenticated state with invalid refresh token
      const preloadedState = {
        auth: {
          token: 'mock-jwt-token-expired',
          refreshToken: 'invalid-refresh-token',
          user: {
            id: 'user-123',
            username: 'testuser',
            role: 'trader' as const,
          },
          isAuthenticated: true,
          sessionTimeout: Date.now() + 30 * 60 * 1000,
          lastActivity: Date.now(),
          loading: false,
          error: null,
        },
      };

      const { store } = renderWithProviders(<div>Test Component</div>, {
        preloadedState,
      });

      // Store invalid refresh token in localStorage and sessionStorage
      localStorage.setItem('refresh_token', 'invalid-refresh-token');
      sessionStorage.setItem('auth_token', 'mock-jwt-token-expired');

      // Make a request to protected endpoint that will return 401
      await store.dispatch(authApi.endpoints.getMe.initiate());

      // Wait for logout to occur and storage to be cleared
      await waitFor(() => {
        const state = store.getState();
        expect(state.auth.isAuthenticated).toBe(false);
        expect(state.auth.token).toBeNull();
        expect(sessionStorage.getItem('auth_token')).toBeNull();
        expect(localStorage.getItem('refresh_token')).toBeNull();
      }, { timeout: 3000 });

      // Verify redirect to login page
      expect(window.location.href).toBe('/login');
    });

    it('should logout user when no refresh token is available', async () => {
      // Mock window.location.href
      delete (window as any).location;
      window.location = { href: '' } as any;

      // Set up initial authenticated state without refresh token
      const preloadedState = {
        auth: {
          token: 'mock-jwt-token-expired',
          refreshToken: null,
          user: {
            id: 'user-123',
            username: 'testuser',
            role: 'trader' as const,
          },
          isAuthenticated: true,
          sessionTimeout: Date.now() + 30 * 60 * 1000,
          lastActivity: Date.now(),
          loading: false,
          error: null,
        },
      };

      const { store } = renderWithProviders(<div>Test Component</div>, {
        preloadedState,
      });

      // Make a request to protected endpoint that will return 401
      await store.dispatch(authApi.endpoints.getMe.initiate());

      // Wait for logout to occur
      await waitFor(() => {
        const state = store.getState();
        expect(state.auth.isAuthenticated).toBe(false);
      });

      // Verify redirect to login page
      expect(window.location.href).toBe('/login');
    });
  });

  describe('Logout Flow', () => {
    it('should clear session and redirect to login on logout', () => {
      // Mock window.location.href
      delete (window as any).location;
      window.location = { href: '' } as any;

      // Set up initial authenticated state
      const preloadedState = {
        auth: {
          token: 'mock-jwt-token-12345',
          refreshToken: 'mock-refresh-token-67890',
          user: {
            id: 'user-123',
            username: 'testuser',
            role: 'trader' as const,
          },
          isAuthenticated: true,
          sessionTimeout: Date.now() + 30 * 60 * 1000,
          lastActivity: Date.now(),
          loading: false,
          error: null,
        },
      };

      const { store } = renderWithProviders(<div>Test Component</div>, {
        preloadedState,
      });

      // Store tokens in storage
      sessionStorage.setItem('auth_token', 'mock-jwt-token-12345');
      sessionStorage.setItem('user', JSON.stringify({ id: 'user-123', username: 'testuser', role: 'trader' }));
      localStorage.setItem('refresh_token', 'mock-refresh-token-67890');

      // Dispatch logout action
      store.dispatch(logout());

      // Verify Redux state is cleared
      const state = store.getState();
      expect(state.auth.isAuthenticated).toBe(false);
      expect(state.auth.token).toBeNull();
      expect(state.auth.refreshToken).toBeNull();
      expect(state.auth.user).toBeNull();

      // Verify storage is cleared
      expect(sessionStorage.getItem('auth_token')).toBeNull();
      expect(sessionStorage.getItem('user')).toBeNull();
      expect(localStorage.getItem('refresh_token')).toBeNull();
    });

    it('should call logout API endpoint and clear session', async () => {
      // Set up initial authenticated state
      const preloadedState = {
        auth: {
          token: 'mock-jwt-token-12345',
          refreshToken: null,
          user: {
            id: 'user-123',
            username: 'testuser',
            role: 'trader' as const,
          },
          isAuthenticated: true,
          sessionTimeout: Date.now() + 30 * 60 * 1000,
          lastActivity: Date.now(),
          loading: false,
          error: null,
        },
      };

      const { store } = renderWithProviders(<div>Test Component</div>, {
        preloadedState,
      });

      // Call logout API
      const result = await store.dispatch(
        authApi.endpoints.logout.initiate()
      );

      // Verify API call succeeded
      expect(result.data).toBeDefined();

      // Dispatch logout action to clear state
      store.dispatch(logout());

      // Verify state is cleared
      const state = store.getState();
      expect(state.auth.isAuthenticated).toBe(false);
      expect(state.auth.token).toBeNull();
    });
  });

  describe('Session Management', () => {
    it('should restore session from sessionStorage on app load', async () => {
      // Set up session storage with valid token
      sessionStorage.setItem('auth_token', 'mock-jwt-token-12345');
      sessionStorage.setItem('user', JSON.stringify({
        id: 'user-123',
        username: 'testuser',
        role: 'trader',
      }));
      localStorage.setItem('refresh_token', 'mock-refresh-token-67890');

      const { store } = renderWithProviders(<div>Test Component</div>);

      // Manually trigger session restoration (normally done in App.tsx)
      const { restoreSession } = await import('./authSlice');
      store.dispatch(restoreSession());

      // Verify session is restored
      const state = store.getState();
      expect(state.auth.isAuthenticated).toBe(true);
      expect(state.auth.token).toBe('mock-jwt-token-12345');
      expect(state.auth.user).toEqual({
        id: 'user-123',
        username: 'testuser',
        email: '',
        role: 'trader',
      });
      expect(state.auth.refreshToken).toBe('mock-refresh-token-67890');
    });

    it('should not restore session with invalid session data', async () => {
      // Set up session storage with invalid JSON
      sessionStorage.setItem('auth_token', 'mock-jwt-token-12345');
      sessionStorage.setItem('user', 'invalid-json{');

      const { store } = renderWithProviders(<div>Test Component</div>);

      // Manually trigger session restoration
      const { restoreSession } = await import('./authSlice');
      store.dispatch(restoreSession());

      // Verify session is not restored
      const state = store.getState();
      expect(state.auth.isAuthenticated).toBe(false);
      expect(state.auth.token).toBeNull();

      // Verify invalid data is cleared
      expect(sessionStorage.getItem('auth_token')).toBeNull();
      expect(sessionStorage.getItem('user')).toBeNull();
    });
  });

  describe('Error Handling', () => {
    it('should handle network errors gracefully', async () => {
      const user = userEvent.setup();

      // Override handler to simulate network error
      server.use(
        http.post('http://localhost:8080/api/auth/login', () => HttpResponse.error())
      );

      renderWithProviders(<LoginPage />);

      const usernameInput = screen.getByLabelText(/username/i);
      const passwordInput = screen.getByLabelText(/password/i);
      const submitButton = screen.getByRole('button', { name: /sign in/i });

      await user.type(usernameInput, 'testuser');
      await user.type(passwordInput, 'password123');
      await user.click(submitButton);

      // Wait for error message (generic error for network failures)
      // Network errors may take longer due to retries
      await waitFor(() => {
        const errorElement = screen.queryByRole('alert');
        expect(errorElement).toBeInTheDocument();
      }, { timeout: 5000 });

      // Verify the error message appears
      const errorElement = screen.getByRole('alert');
      expect(errorElement.textContent).toMatch(/login failed/i);
    });

    it('should handle server errors (500) gracefully', async () => {
      const user = userEvent.setup();

      // Override handler to simulate server error
      server.use(
        http.post('http://localhost:8080/api/auth/login', () => HttpResponse.json(
            { message: 'Internal server error' },
            { status: 500 }
          ))
      );

      renderWithProviders(<LoginPage />);

      const usernameInput = screen.getByLabelText(/username/i);
      const passwordInput = screen.getByLabelText(/password/i);
      const submitButton = screen.getByRole('button', { name: /sign in/i });

      await user.type(usernameInput, 'testuser');
      await user.type(passwordInput, 'password123');
      await user.click(submitButton);

      // Wait for error message (RTK Query may retry, so give it more time)
      await waitFor(() => {
        // Check that an error alert appears (either specific message or generic)
        const errorElement = screen.queryByRole('alert');
        expect(errorElement).toBeInTheDocument();
      }, { timeout: 5000 });

      // Verify the error message contains relevant text
      const errorElement = screen.getByRole('alert');
      expect(errorElement.textContent).toMatch(/(internal server error|login failed)/i);
    });
  });
});

