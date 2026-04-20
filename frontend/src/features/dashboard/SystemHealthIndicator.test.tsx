import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { SystemHealthIndicator } from './SystemHealthIndicator';

import type { RootState } from '@/app/store';
import { renderWithProviders } from '@/tests/test-utils';

type SystemInfoHookResult = {
  data?: {
    applicationVersion: string;
    lastDeploymentDate: string;
    databaseStatus: string;
  };
  isError: boolean;
  isLoading: boolean;
  error?: unknown;
};

type RiskStatusHookResult = {
  data?: {
    currentDrawdown?: number;
    maxDrawdownLimit?: number;
    dailyLoss?: number;
    dailyLossLimit?: number;
    openRiskExposure?: number;
    positionCorrelation?: number;
    circuitBreakerActive: boolean;
    circuitBreakerReason: string;
  };
  isError: boolean;
  isLoading: boolean;
  error?: unknown;
};

const mockUseGetSystemInfoQuery = vi.fn<() => SystemInfoHookResult>();
const mockUseGetRiskStatusQuery = vi.fn<() => RiskStatusHookResult>();
const { devAuthState } = vi.hoisted(() => ({
  devAuthState: { enabled: false },
}));

vi.mock('@/features/settings/exchangeApi', () => ({
  useGetSystemInfoQuery: () => mockUseGetSystemInfoQuery(),
}));

vi.mock('@/features/risk/riskApi', () => ({
  useGetRiskStatusQuery: () => mockUseGetRiskStatusQuery(),
}));

vi.mock('@/features/auth/devAuth', () => ({
  get DEV_AUTH_BYPASS_ENABLED() {
    return devAuthState.enabled;
  },
}));

vi.mock('@/utils/formatters', () => ({
  formatDistanceToNow: vi.fn((date: Date) => {
    const now = new Date('2026-03-09T12:00:00Z');
    const diffMs = now.getTime() - date.getTime();
    const diffMinutes = Math.floor(diffMs / 1000 / 60);

    if (diffMinutes < 1) return 'just now';
    if (diffMinutes === 1) return '1 minute ago';
    if (diffMinutes < 60) return `${diffMinutes} minutes ago`;
    return `${Math.floor(diffMinutes / 60)} hours ago`;
  }),
}));

describe('SystemHealthIndicator', () => {
  const mockWebSocketState = {
    connected: false,
    connecting: false,
    error: null,
    lastReconnectAttempt: null,
    reconnectAttempts: 0,
    subscribedChannels: [],
    lastEventTime: null,
  };

  beforeEach(() => {
    vi.clearAllMocks();
    devAuthState.enabled = false;

    mockUseGetSystemInfoQuery.mockReturnValue({
      data: {
        applicationVersion: 'local-dev',
        lastDeploymentDate: '2026-03-09T11:00:00Z',
        databaseStatus: 'UP',
      },
      isError: false,
      isLoading: false,
      error: undefined,
    });

    mockUseGetRiskStatusQuery.mockReturnValue({
      data: {
        currentDrawdown: 0.5,
        maxDrawdownLimit: 25,
        dailyLoss: 0,
        dailyLossLimit: 5,
        openRiskExposure: 10,
        positionCorrelation: 35,
        circuitBreakerActive: false,
        circuitBreakerReason: 'Within configured limits',
      },
      isError: false,
      isLoading: false,
      error: undefined,
    });
  });

  it('renders all status sections', () => {
    const preloadedState: Partial<RootState> = {
      websocket: mockWebSocketState,
    };

    renderWithProviders(<SystemHealthIndicator />, { preloadedState });

    expect(screen.getByText('System Health')).toBeInTheDocument();
    expect(screen.getByText('Backend API:')).toBeInTheDocument();
    expect(screen.getByText('Database:')).toBeInTheDocument();
    expect(screen.getByText('WebSocket:')).toBeInTheDocument();
    expect(screen.getByText('Last Update:')).toBeInTheDocument();
    expect(screen.getByText('Circuit Breaker:')).toBeInTheDocument();
  });

  it('shows backend and database status from system info', () => {
    const preloadedState: Partial<RootState> = {
      websocket: mockWebSocketState,
    };

    renderWithProviders(<SystemHealthIndicator />, { preloadedState });

    expect(screen.getAllByText('Connected').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('UP')).toBeInTheDocument();
  });

  it('shows websocket connected status and last update age', () => {
    const preloadedState: Partial<RootState> = {
      websocket: {
        ...mockWebSocketState,
        connected: true,
        lastEventTime: '2026-03-09T11:55:00Z',
      },
    };

    renderWithProviders(<SystemHealthIndicator />, { preloadedState });

    expect(screen.getAllByText('Connected').length).toBeGreaterThanOrEqual(2);
    expect(screen.getByText('5 minutes ago')).toBeInTheDocument();
  });

  it('shows websocket error and reconnection attempts', () => {
    const preloadedState: Partial<RootState> = {
      websocket: {
        ...mockWebSocketState,
        error: 'Connection failed',
        reconnectAttempts: 3,
      },
    };

    renderWithProviders(<SystemHealthIndicator />, { preloadedState });

    expect(screen.getByText('Error')).toBeInTheDocument();
    expect(screen.getByText('Reconnection attempts: 3')).toBeInTheDocument();
  });

  it('shows polling fallback instead of disconnected websocket when local bypass is active', () => {
    devAuthState.enabled = true;
    const preloadedState: Partial<RootState> = {
      websocket: mockWebSocketState,
    };

    renderWithProviders(<SystemHealthIndicator />, { preloadedState });

    expect(screen.getByText('Polling fallback')).toBeInTheDocument();
    expect(
      screen.getByText(/Local debug auth bypass keeps telemetry on polling fallback/i)
    ).toBeInTheDocument();
  });

  it('shows active circuit breaker from risk status', () => {
    mockUseGetRiskStatusQuery.mockReturnValue({
      data: {
        circuitBreakerActive: true,
        circuitBreakerReason: 'Drawdown or daily loss limit reached',
      },
      isError: false,
      isLoading: false,
      error: undefined,
    });

    const preloadedState: Partial<RootState> = {
      websocket: mockWebSocketState,
    };

    renderWithProviders(<SystemHealthIndicator />, { preloadedState });

    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('shows unknown circuit breaker status when risk query fails', () => {
    mockUseGetRiskStatusQuery.mockReturnValue({
      data: undefined,
      isError: true,
      isLoading: false,
      error: { status: 500, data: { message: 'Risk status unavailable' } },
    });

    const preloadedState: Partial<RootState> = {
      websocket: mockWebSocketState,
    };

    renderWithProviders(<SystemHealthIndicator />, { preloadedState });

    expect(screen.getByText('Unknown')).toBeInTheDocument();
  });

  it('shows disconnected backend and unknown dependencies when system info fails', () => {
    mockUseGetSystemInfoQuery.mockReturnValue({
      data: undefined,
      isError: true,
      isLoading: false,
      error: { status: 503, data: { message: 'System info unavailable' } },
    });

    const preloadedState: Partial<RootState> = {
      websocket: mockWebSocketState,
    };

    renderWithProviders(<SystemHealthIndicator />, { preloadedState });

    expect(screen.getAllByText('Disconnected').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Unknown').length).toBeGreaterThanOrEqual(1);
  });
});
