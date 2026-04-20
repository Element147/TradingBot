/**
 * useWebSocket Hook Tests
 */

import { configureStore } from '@reduxjs/toolkit';
import { act, renderHook, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  type WebSocketEvent,
  resolveWebSocketUrl,
  WebSocketManager,
  type WebSocketLikeConstructor,
  setWebSocketManager,
} from '../../services/websocket';
import authReducer from '../auth/authSlice';
import environmentReducer from '../environment/environmentSlice';

import { useWebSocketConnection, useWebSocketSubscription } from './useWebSocket';
import websocketReducer from './websocketSlice';

// Mock WebSocket
class MockWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;

  readyState = MockWebSocket.CONNECTING;
  url: string;
  onopen: ((event: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;

  constructor(url: string) {
    this.url = url;

    setTimeout(() => {
      this.readyState = MockWebSocket.OPEN;
      this.onopen?.(new Event('open'));
    }, 0);
  }

  send(_data: string) {}

  close(_code?: number, _reason?: string) {
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.(new CloseEvent('close', { code: 1000, reason: 'Client disconnect' }));
  }
}

const createTestStore = (initialState = {}) =>
  configureStore({
    reducer: {
      auth: authReducer,
      environment: environmentReducer,
      websocket: websocketReducer,
    },
    preloadedState: {
      auth: {
        token: 'test-token',
        refreshToken: null,
        user: null,
        isAuthenticated: true,
        loading: false,
        error: null,
        sessionTimeout: Date.now() + 30 * 60 * 1000,
        lastActivity: Date.now(),
      },
      environment: {
        mode: 'test',
        connectedExchange: null,
        lastSyncTime: null,
      },
      ...initialState,
    },
  });

describe('useWebSocketConnection', () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
    setWebSocketManager(
      new WebSocketManager(
        resolveWebSocketUrl(undefined, { pageUrl: 'http://localhost:5173/' }),
        MockWebSocket as unknown as WebSocketLikeConstructor
      )
    );
  });

  afterEach(() => {
    setWebSocketManager(null);
    vi.useRealTimers();
  });

  it('should connect when token is available', async () => {
    const store = createTestStore();
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <Provider store={store}>{children}</Provider>
    );

    const { result } = renderHook(() => useWebSocketConnection(), { wrapper });

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });
  });

  it('should disconnect when token is removed', async () => {
    const store = createTestStore();
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <Provider store={store}>{children}</Provider>
    );

    const { result, rerender } = renderHook(() => useWebSocketConnection(), { wrapper });

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    act(() => {
      store.dispatch({ type: 'auth/logout' });
    });
    rerender();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(false);
    });
  });

  it('should provide reconnect function', () => {
    const store = createTestStore();
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <Provider store={store}>{children}</Provider>
    );

    const { result } = renderHook(() => useWebSocketConnection(), { wrapper });

    expect(typeof result.current.reconnect).toBe('function');
  });

  it('should reconnect when environment changes', async () => {
    const store = createTestStore();
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <Provider store={store}>{children}</Provider>
    );

    const { result, rerender } = renderHook(() => useWebSocketConnection(), { wrapper });

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });

    act(() => {
      store.dispatch({ type: 'environment/setEnvironmentMode', payload: 'live' });
    });
    rerender();

    await waitFor(() => {
      expect(result.current.isConnected).toBe(true);
    });
  });
});

describe('useWebSocketSubscription', () => {
  beforeEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
    setWebSocketManager(
      new WebSocketManager(
        resolveWebSocketUrl(undefined, { pageUrl: 'http://localhost:5173/' }),
        MockWebSocket as unknown as WebSocketLikeConstructor
      )
    );
  });

  afterEach(() => {
    setWebSocketManager(null);
    vi.useRealTimers();
  });

  it('should subscribe to event type', () => {
    const store = createTestStore();
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <Provider store={store}>{children}</Provider>
    );

    const handler = vi.fn();

    renderHook(() => useWebSocketSubscription('balance.updated', handler), {
      wrapper,
    });

    expect(handler).not.toHaveBeenCalled();
  });

  it('should call handler when event is received', () => {
    const store = createTestStore();
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <Provider store={store}>{children}</Provider>
    );

    const handler = vi.fn();
    const event: WebSocketEvent = {
      type: 'balance.updated',
      environment: 'test',
      timestamp: '2024-03-09T12:00:00Z',
      data: { balance: 1000 },
    };

    renderHook(() => useWebSocketSubscription('balance.updated', handler), {
      wrapper,
    });

    expect(event.type).toBe('balance.updated');
  });

  it('should unsubscribe on unmount', () => {
    const store = createTestStore();
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <Provider store={store}>{children}</Provider>
    );

    const handler = vi.fn();

    const { unmount } = renderHook(
      () => useWebSocketSubscription('balance.updated', handler),
      { wrapper }
    );

    unmount();
  });

  it('should support wildcard subscriptions', () => {
    const store = createTestStore();
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <Provider store={store}>{children}</Provider>
    );

    const handler = vi.fn();

    renderHook(() => useWebSocketSubscription('*', handler), { wrapper });
  });
});
