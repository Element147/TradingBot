import { configureStore } from '@reduxjs/toolkit';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import authReducer from '../../features/auth/authSlice';
import environmentReducer from '../../features/environment/environmentSlice';
import settingsReducer from '../../features/settings/settingsSlice';
import websocketReducer from '../../features/websocket/websocketSlice';

import { Header } from './Header';


const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

vi.mock('../../features/settings/exchangeApi', () => ({
  useGetSavedExchangeConnectionsQuery: () => ({
    data: {
      activeConnectionId: 'binance-paper',
      connections: [
        {
          id: 'binance-paper',
          name: 'Binance Paper',
          exchange: 'binance',
          apiKey: '',
          apiSecret: '',
          testnet: true,
          active: true,
        },
      ],
    },
  }),
}));

vi.mock('../../features/risk/riskApi', () => ({
  useGetRiskStatusQuery: () => ({
    data: {
      circuitBreakerActive: false,
      circuitBreakerReason: '',
    },
  }),
}));

const createMockStore = (user = { id: '1', username: 'testuser', email: 'test@example.com', role: 'trader' as const }) => configureStore({
    reducer: {
      auth: authReducer,
      environment: environmentReducer,
      settings: settingsReducer,
      websocket: websocketReducer,
    },
    preloadedState: {
      auth: {
        token: 'mock-token',
        refreshToken: null,
        user,
        isAuthenticated: true,
        loading: false,
        error: null,
        sessionTimeout: null,
        lastActivity: Date.now(),
      },
      environment: {
        mode: 'test' as const,
        connectedExchange: 'binance',
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
      websocket: {
        connected: true,
        connecting: false,
        error: null,
        lastReconnectAttempt: null,
        reconnectAttempts: 0,
        subscribedChannels: ['test.backtests'],
        lastEventTime: '2026-03-20T09:00:00',
        lastEventByType: {},
      },
    },
  });

describe('Header', () => {
  const defaultProps = {
    onMenuClick: vi.fn(),
  };

  const renderHeader = (props = defaultProps, store = createMockStore()) => render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/dashboard']}>
          <Header {...props} />
        </MemoryRouter>
      </Provider>
    );

  beforeEach(() => {
    mockNavigate.mockClear();
    defaultProps.onMenuClick.mockClear();
  });

  it('renders menu button', () => {
    renderHeader();

    const menuButton = screen.getByLabelText('Toggle navigation menu');
    expect(menuButton).toBeInTheDocument();
  });

  it('calls onMenuClick when menu button is clicked', () => {
    const onMenuClick = vi.fn();
    renderHeader({ onMenuClick });

    const menuButton = screen.getByLabelText('Toggle navigation menu');
    fireEvent.click(menuButton);

    expect(onMenuClick).toHaveBeenCalled();
  });

  it('renders notifications button with badge', () => {
    renderHeader();

    const notificationsButton = screen.getByLabelText('Open notifications');
    expect(notificationsButton).toBeInTheDocument();
  });

  it('renders route context and operator status blocks', () => {
    renderHeader();

    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Review workstation posture, paper state, and the next safe action without chasing multiple competing panels.'
      )
    ).toBeInTheDocument();
    expect(screen.getByText('Operations: TEST')).toBeInTheDocument();
    expect(screen.getByText('Exchange: BINANCE TESTNET')).toBeInTheDocument();
    expect(screen.getByText('Telemetry: Live stream')).toBeInTheDocument();
    expect(screen.getByText('Risk: Guarded')).toBeInTheDocument();
    expect(screen.getByLabelText('Role: TRADER')).toBeInTheDocument();
  });

  it('renders live route metadata when opened on /live', () => {
    const store = createMockStore();

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/live']}>
          <Header {...defaultProps} />
        </MemoryRouter>
      </Provider>
    );

    expect(screen.getByText('Live Monitoring')).toBeInTheDocument();
    expect(
      screen.getByText(
        'Review explicit live-context posture, exchange health, and strategy evidence while the route remains fail-closed until approved capabilities exist.'
      )
    ).toBeInTheDocument();
    expect(screen.getByText('Context: Live')).toBeInTheDocument();
  });

  it('renders user avatar with first letter of username', () => {
    renderHeader();

    const avatar = screen.getByText('T'); // First letter of 'testuser'
    expect(avatar).toBeInTheDocument();
  });

  it('opens user menu when avatar is clicked', async () => {
    renderHeader();

    const accountButton = screen.getByLabelText('Open account menu');
    fireEvent.click(accountButton);

    await waitFor(() => {
      expect(screen.getByText('testuser')).toBeInTheDocument();
      expect(screen.getByText('test@example.com')).toBeInTheDocument();
    });
  });

  it('displays settings and logout options in user menu', async () => {
    renderHeader();

    const accountButton = screen.getByLabelText('Open account menu');
    fireEvent.click(accountButton);

    await waitFor(() => {
      expect(screen.getByText('Settings')).toBeInTheDocument();
      expect(screen.getByText('Logout')).toBeInTheDocument();
    });
  });

  it('navigates to settings when settings menu item is clicked', async () => {
    renderHeader();

    const accountButton = screen.getByLabelText('Open account menu');
    fireEvent.click(accountButton);

    await waitFor(() => {
      const settingsMenuItem = screen.getByText('Settings');
      fireEvent.click(settingsMenuItem);
    });

    expect(mockNavigate).toHaveBeenCalledWith('/settings');
  });

  it('dispatches logout and navigates to login when logout is clicked', async () => {
    const store = createMockStore();
    renderHeader(defaultProps, store);

    const accountButton = screen.getByLabelText('Open account menu');
    fireEvent.click(accountButton);

    await waitFor(() => {
      const logoutMenuItem = screen.getByText('Logout');
      fireEvent.click(logoutMenuItem);
    });

    expect(mockNavigate).toHaveBeenCalledWith('/login');
    
    // Check that user is logged out
    const state = store.getState();
    expect(state.auth.isAuthenticated).toBe(false);
    expect(state.auth.token).toBeNull();
  });

  it('opens notifications menu when notifications button is clicked', async () => {
    renderHeader();

    const notificationsButton = screen.getByLabelText('Open notifications');
    fireEvent.click(notificationsButton);

    await waitFor(() => {
      expect(screen.getByText('Notifications')).toBeInTheDocument();
      expect(screen.getByText(/no new notifications/i)).toBeInTheDocument();
    });
  });

  it('displays default avatar when user has no username', () => {
    const store = createMockStore({ id: '1', username: '', email: 'test@example.com', role: 'trader' });
    renderHeader(defaultProps, store);

    const avatar = screen.getByText('U'); // Default 'U'
    expect(avatar).toBeInTheDocument();
  });

  it('renders theme toggle button', () => {
    renderHeader();

    const themeToggle = screen.getByRole('button', { name: /switch to dark mode/i });
    expect(themeToggle).toBeInTheDocument();
  });

  it('toggles theme when theme toggle is clicked', async () => {
    const store = createMockStore();
    renderHeader(defaultProps, store);

    const themeToggle = screen.getByRole('button', { name: /switch to dark mode/i });
    fireEvent.click(themeToggle);

    await waitFor(() => {
      const state = store.getState();
      expect(state.settings.theme).toBe('dark');
    });
  });
});
