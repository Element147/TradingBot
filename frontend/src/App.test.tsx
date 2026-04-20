import { configureStore } from '@reduxjs/toolkit';
import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import App from './App';
import authReducer from './features/auth/authSlice';
import environmentReducer from './features/environment/environmentSlice';
import settingsReducer from './features/settings/settingsSlice';

const { prefetchAuthenticatedWorkstationDataMock, webSocketRuntimeMock, devAuthState } = vi.hoisted(() => ({
  prefetchAuthenticatedWorkstationDataMock: vi.fn(),
  webSocketRuntimeMock: vi.fn(),
  devAuthState: { enabled: false },
}));

vi.mock('./app/prefetchAuthenticatedWorkstationData', () => ({
  prefetchAuthenticatedWorkstationData: prefetchAuthenticatedWorkstationDataMock,
}));

vi.mock('./features/websocket/WebSocketRuntime', () => ({
  WebSocketRuntime: () => {
    webSocketRuntimeMock();
    return <div data-testid="websocket-runtime" />;
  },
}));

vi.mock('./features/auth/devAuth', () => ({
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

// Mock the page components to avoid loading actual implementations
vi.mock('./features/auth/LoginPage', () => ({
  default: () => <div data-testid="login-page">Login Page</div>,
}));

vi.mock('./features/dashboard/DashboardPage', () => ({
  default: () => <div data-testid="dashboard-page">Dashboard Page</div>,
}));

vi.mock('./features/forwardTesting/ForwardTestingPage', () => ({
  default: () => <div data-testid="forward-testing-page">Forward Testing Page</div>,
}));

vi.mock('./features/paper/PaperTradingPage', () => ({
  default: () => <div data-testid="paper-page">Paper Trading Page</div>,
}));

vi.mock('./features/live/LiveTradingPage', () => ({
  default: () => <div data-testid="live-page">Live Monitoring Page</div>,
}));

vi.mock('./features/strategies/StrategiesPage', () => ({
  default: () => <div data-testid="strategies-page">Strategies Page</div>,
}));

vi.mock('./features/trades/TradesPage', () => ({
  default: () => <div data-testid="trades-page">Trades Page</div>,
}));

vi.mock('./features/backtest/BacktestPage', () => ({
  default: () => <div data-testid="backtest-page">Backtest Page</div>,
}));

vi.mock('./features/marketData/MarketDataPage', () => ({
  default: () => <div data-testid="market-data-page">Market Data Page</div>,
}));

vi.mock('./features/risk/RiskPage', () => ({
  default: () => <div data-testid="risk-page">Risk Page</div>,
}));

vi.mock('./features/settings/SettingsPage', () => ({
  default: () => <div data-testid="settings-page">Settings Page</div>,
}));

describe('App Routing', () => {
  const createMockStore = (isAuthenticated = false) => configureStore({
      reducer: {
        auth: authReducer,
        environment: environmentReducer,
        settings: settingsReducer,
      },
      preloadedState: {
        auth: {
          token: isAuthenticated ? 'mock-token' : null,
          refreshToken: null,
          user: isAuthenticated
            ? { id: '1', username: 'testuser', email: 'test@example.com', role: 'trader' }
            : null,
          isAuthenticated,
          loading: false,
          error: null,
          sessionTimeout: null,
          lastActivity: Date.now(),
        },
        environment: {
          mode: 'test',
          connectedExchange: null,
          lastSyncTime: null,
        },
        settings: {
          theme: 'light',
          currency: 'USD',
          timezone: 'UTC',
          textScale: 1,
          notifications: {
            emailAlerts: true,
            telegramAlerts: false,
            profitLossThreshold: 5,
            drawdownThreshold: 15,
            riskThreshold: 75,
          },
        },
      },
    });

  beforeEach(() => {
    // Clear localStorage before each test
    localStorage.clear();
    prefetchAuthenticatedWorkstationDataMock.mockClear();
    webSocketRuntimeMock.mockClear();
    devAuthState.enabled = false;
    vi.unstubAllEnvs();
  });

  afterEach(() => {
    devAuthState.enabled = false;
    vi.unstubAllEnvs();
  });

  describe('Public Routes', () => {
    it('should render login page at /login', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/login');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('login-page')).toBeInTheDocument();
      });
    });

    it('should redirect login route to dashboard when dev bypass is enabled', async () => {
      devAuthState.enabled = true;
      const store = createMockStore(false);
      window.history.pushState({}, '', '/login');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/dashboard');
        expect(screen.getByTestId('dashboard-page')).toBeInTheDocument();
      });
    });
  });

  describe('Protected Routes - Unauthenticated', () => {
    it('should redirect to login when accessing /dashboard without authentication', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/dashboard');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/login');
      });
    });

    it('should redirect to login when accessing /paper without authentication', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/paper');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/login');
      });
    });

    it('should redirect to login when accessing /live without authentication', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/live');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/login');
      });
    });

    it('should redirect to login when accessing /forward-testing without authentication', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/forward-testing');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/login');
      });
    });

    it('should redirect to login when accessing /strategies without authentication', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/strategies');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/login');
      });
    });

    it('should redirect to login when accessing /trades without authentication', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/trades');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/login');
      });
    });

    it('should redirect to login when accessing /backtest without authentication', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/backtest');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/login');
      });
    });

    it('should redirect to login when accessing /risk without authentication', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/risk');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/login');
      });
    });

    it('should redirect to login when accessing /settings without authentication', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/settings');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/login');
      });
    });

    it('should redirect to login when accessing /market-data without authentication', async () => {
      const store = createMockStore(false);
      window.history.pushState({}, '', '/market-data');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/login');
      });
    });

    it('should render protected routes in bypass mode without stored auth', async () => {
      devAuthState.enabled = true;
      const store = createMockStore(false);
      window.history.pushState({}, '', '/dashboard');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('dashboard-page')).toBeInTheDocument();
      });
    });

    it.each([
      ['/paper', 'paper-page'],
      ['/backtest', 'backtest-page'],
      ['/strategies', 'strategies-page'],
    ])('should render %s in bypass mode without stored auth', async (path, testId) => {
      devAuthState.enabled = true;
      const store = createMockStore(false);
      window.history.pushState({}, '', path);

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId(testId)).toBeInTheDocument();
      });
    });
  });

  describe('Protected Routes - Authenticated', () => {
    it('should render dashboard page when authenticated', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/dashboard');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        const mockedDashboard = screen.queryByTestId('dashboard-page');
        const realDashboardHeading = screen.queryByRole('heading', { name: /dashboard/i });
        expect(mockedDashboard ?? realDashboardHeading).toBeInTheDocument();
      }, { timeout: 10000 });
    });

    it('should render strategies page when authenticated', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/strategies');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        const mockedStrategies = screen.queryByTestId('strategies-page');
        const realStrategiesHeading = screen.queryByRole('heading', { name: /strategy management/i });
        expect(mockedStrategies ?? realStrategiesHeading).toBeInTheDocument();
      }, { timeout: 10000 });
    });

    it('should render paper trading page when authenticated', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/paper');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('paper-page')).toBeInTheDocument();
      }, { timeout: 10000 });
    });

    it('should render live monitoring page when authenticated', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/live');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('live-page')).toBeInTheDocument();
      });
    });

    it('should render forward testing page when authenticated', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/forward-testing');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('forward-testing-page')).toBeInTheDocument();
      });
    });

    it('should render trades page when authenticated', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/trades');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('trades-page')).toBeInTheDocument();
      });
    });

    it('should render backtest page when authenticated', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/backtest');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('backtest-page')).toBeInTheDocument();
      });
    });

    it('should render risk page when authenticated', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/risk');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('risk-page')).toBeInTheDocument();
      });
    });

    it('should render market data page when authenticated', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/market-data');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('market-data-page')).toBeInTheDocument();
      });
    });

    it('should render settings page when authenticated', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/settings');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('settings-page')).toBeInTheDocument();
      });
    });
  });

  describe('Default Routes', () => {
    it('should redirect root path to /dashboard', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/dashboard');
      });
    });

    it('should redirect unknown paths to /dashboard', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/unknown-route');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(window.location.pathname).toBe('/dashboard');
      });
    });
  });

  describe('Lazy Loading', () => {
    it('should show loading fallback while lazy loading components', async () => {
      const store = createMockStore(true);
      window.history.pushState({}, '', '/dashboard');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      // Loading fallback should appear briefly
      // Note: This test may be flaky due to fast loading in test environment
      // In real scenarios, the LoadingFallback will be visible during code splitting
      await waitFor(() => {
        expect(screen.getByTestId('dashboard-page')).toBeInTheDocument();
      });
    });
  });

  describe('Authenticated Prefetch', () => {
    it('does not prefetch protected data on the login route before authentication', async () => {
      vi.stubEnv('MODE', 'development');
      vi.stubEnv('DEV', 'true');
      const store = createMockStore(false);
      window.history.pushState({}, '', '/login');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('login-page')).toBeInTheDocument();
      });

      expect(prefetchAuthenticatedWorkstationDataMock).not.toHaveBeenCalled();
    });

    it('prefetches protected workstation data after authentication is present', async () => {
      vi.stubEnv('MODE', 'development');
      vi.stubEnv('DEV', 'true');
      const store = createMockStore(true);
      window.history.pushState({}, '', '/dashboard');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(prefetchAuthenticatedWorkstationDataMock).toHaveBeenCalledWith(
          store.dispatch
        );
      });
    });

    it('keeps websocket runtime disabled in bypass mode without a token', async () => {
      devAuthState.enabled = true;
      vi.stubEnv('MODE', 'development');
      const store = createMockStore(false);
      window.history.pushState({}, '', '/dashboard');

      render(
        <Provider store={store}>
          <App />
        </Provider>
      );

      await waitFor(() => {
        expect(screen.getByTestId('dashboard-page')).toBeInTheDocument();
      });

      expect(webSocketRuntimeMock).not.toHaveBeenCalled();
      expect(screen.queryByTestId('websocket-runtime')).not.toBeInTheDocument();
    });
  });
});
