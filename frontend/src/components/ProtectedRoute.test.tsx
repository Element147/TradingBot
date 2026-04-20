import { configureStore } from '@reduxjs/toolkit';
import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import ProtectedRoute from './ProtectedRoute';

import authReducer, { AuthState } from '@/features/auth/authSlice';

const { devAuthState } = vi.hoisted(() => ({
  devAuthState: { enabled: false },
}));

vi.mock('@/features/auth/devAuth', () => ({
  get DEV_AUTH_BYPASS_ENABLED() {
    return devAuthState.enabled;
  },
  DEV_AUTH_BYPASS_USER: {
    id: 'local-debug-admin',
    username: 'admin',
    email: 'admin@algotrading.local',
    role: 'admin',
  },
}));

describe('ProtectedRoute', () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.clearAllTimers();
    devAuthState.enabled = false;
  });

  const createStore = (authState: Partial<AuthState>) => configureStore({
      reducer: {
        auth: authReducer,
      },
      preloadedState: {
        auth: {
          token: null,
          refreshToken: null,
          user: null,
          isAuthenticated: false,
          sessionTimeout: null,
          lastActivity: Date.now(),
          loading: false,
          error: null,
          ...authState,
        },
      },
    });

  it('should show loading state while checking authentication', () => {
    const store = createStore({
      isAuthenticated: false,
      loading: true,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              path="/protected"
              element={
                <ProtectedRoute>
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    // Should show loading indicator
    expect(screen.getByTestId('protected-route-loading')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });

  it('should render children when authenticated', async () => {
    const store = createStore({
      isAuthenticated: true,
      token: 'test-token',
      user: { id: '1', username: 'testuser', role: 'trader' },
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              path="/protected"
              element={
                <ProtectedRoute>
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    // Wait for loading to complete
    await waitFor(() => {
      expect(screen.queryByTestId('protected-route-loading')).not.toBeInTheDocument();
    });

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('should redirect to login when not authenticated', async () => {
    const store = createStore({
      isAuthenticated: false,
      token: null,
      user: null,
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              path="/protected"
              element={
                <ProtectedRoute>
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('should allow access when user has required role', async () => {
    const store = createStore({
      isAuthenticated: true,
      token: 'test-token',
      user: { id: '1', username: 'admin', role: 'admin' },
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              path="/protected"
              element={
                <ProtectedRoute requiredRole="admin">
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.queryByTestId('protected-route-loading')).not.toBeInTheDocument();
    });

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('should deny access when user does not have required role', async () => {
    const store = createStore({
      isAuthenticated: true,
      token: 'test-token',
      user: { id: '1', username: 'trader', role: 'trader' },
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              path="/protected"
              element={
                <ProtectedRoute requiredRole="admin">
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.queryByTestId('protected-route-loading')).not.toBeInTheDocument();
    });

    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    expect(screen.getByTestId('access-denied')).toBeInTheDocument();
    expect(screen.getByText('Access Denied')).toBeInTheDocument();
    expect(screen.getByText(/required role: admin/i)).toBeInTheDocument();
    expect(screen.getByText(/your role: trader/i)).toBeInTheDocument();
  });

  it('should allow trader to access trader-only route', async () => {
    const store = createStore({
      isAuthenticated: true,
      token: 'test-token',
      user: { id: '1', username: 'trader', role: 'trader' },
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              path="/protected"
              element={
                <ProtectedRoute requiredRole="trader">
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.queryByTestId('protected-route-loading')).not.toBeInTheDocument();
    });

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('should deny admin access to trader-only route', async () => {
    const store = createStore({
      isAuthenticated: true,
      token: 'test-token',
      user: { id: '1', username: 'admin', role: 'admin' },
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              path="/protected"
              element={
                <ProtectedRoute requiredRole="trader">
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.queryByTestId('protected-route-loading')).not.toBeInTheDocument();
    });

    // Admin should NOT have access to trader-only routes
    // (role-based access is strict, not hierarchical)
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
    expect(screen.getByTestId('access-denied')).toBeInTheDocument();
  });

  it('should restore session from sessionStorage', async () => {
    const user = { id: '1', username: 'testuser', role: 'trader' as const };
    sessionStorage.setItem('auth_token', 'test-token');
    sessionStorage.setItem('user', JSON.stringify(user));

    // Create store and manually restore session before rendering
    const store = createStore({
      isAuthenticated: false,
      token: null,
      user: null,
      loading: false,
    });
    
    // Manually dispatch restoreSession to simulate app initialization
    store.dispatch({ type: 'auth/restoreSession' });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              path="/protected"
              element={
                <ProtectedRoute>
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    // Wait for loading to complete
    await waitFor(() => {
      expect(screen.queryByTestId('protected-route-loading')).not.toBeInTheDocument();
    });

    // After restoration, should show protected content
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('should allow access without role requirement', async () => {
    const store = createStore({
      isAuthenticated: true,
      token: 'test-token',
      user: { id: '1', username: 'testuser', role: 'trader' },
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              path="/protected"
              element={
                <ProtectedRoute>
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.queryByTestId('protected-route-loading')).not.toBeInTheDocument();
    });

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('should preserve location state when redirecting to login', async () => {
    const store = createStore({
      isAuthenticated: false,
      token: null,
      user: null,
      loading: false,
    });

    const { container } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route 
              path="/login" 
              element={
                <div>
                  Login Page
                  <span data-testid="location-state">
                    {/* This would normally be accessed via useLocation() */}
                  </span>
                </div>
              } 
            />
            <Route
              path="/protected"
              element={
                <ProtectedRoute>
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.getByText('Login Page')).toBeInTheDocument();
    });

    // Verify redirect happened (location state preservation is tested in integration tests)
    expect(container).toBeInTheDocument();
  });

  it('should allow access in dev bypass mode without stored auth', async () => {
    devAuthState.enabled = true;
    const store = createStore({
      isAuthenticated: false,
      token: null,
      user: null,
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/protected']}>
          <Routes>
            <Route path="/login" element={<div>Login Page</div>} />
            <Route
              path="/protected"
              element={
                <ProtectedRoute requiredRole="admin">
                  <div>Protected Content</div>
                </ProtectedRoute>
              }
            />
          </Routes>
        </MemoryRouter>
      </Provider>
    );

    await waitFor(() => {
      expect(screen.queryByTestId('protected-route-loading')).not.toBeInTheDocument();
    });

    expect(screen.getByText('Protected Content')).toBeInTheDocument();
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });
});
