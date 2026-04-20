/**
 * WebSocket Integration Tests
 * 
 * Tests WebSocket communication with Redux integration:
 * - Connection establishment with auth token
 * - balance.updated event updates Redux state
 * - trade.executed event updates trade history
 * - position.updated event updates position display
 * - Reconnection after connection loss
 * 
 * Requirements: 30.8
 */

import { configureStore } from '@reduxjs/toolkit';
import { waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, afterEach, vi, beforeAll, afterAll } from 'vitest';

import {
  resolveWebSocketUrl,
  WebSocketManager,
  type WebSocketEvent,
  type WebSocketLikeConstructor,
  setWebSocketManager,
} from '../../services/websocket';
import { server } from '../../tests/mocks/server';
import { accountApi } from '../account/accountApi';
import authReducer from '../auth/authSlice';
import environmentReducer from '../environment/environmentSlice';
import { tradesApi } from '../trades/tradesApi';

import { websocketMiddleware } from './websocketMiddleware';
import websocketReducer from './websocketSlice';

/**
 * Mock WebSocket implementation for testing
 */
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
  
  private messageQueue: string[] = [];

  constructor(url: string) {
    this.url = url;
  }

  /**
   * Simulate opening the connection
   */
  simulateOpen() {
    this.readyState = MockWebSocket.OPEN;
    if (this.onopen) {
      this.onopen(new Event('open'));
    }
  }

  /**
   * Simulate receiving a message
   */
  simulateMessage(data: string) {
    if (this.onmessage) {
      const event = new MessageEvent('message', { data });
      this.onmessage(event);
    }
  }

  /**
   * Simulate connection close
   */
  simulateClose(code = 1000, reason = 'Normal closure') {
    this.readyState = MockWebSocket.CLOSED;
    if (this.onclose) {
      this.onclose(new CloseEvent('close', { code, reason }));
    }
  }

  /**
   * Simulate connection error
   */
  simulateError() {
    if (this.onerror) {
      this.onerror(new Event('error'));
    }
  }

  send(data: string) {
    this.messageQueue.push(data);
  }

  close(code?: number, reason?: string) {
    this.simulateClose(code, reason);
  }

  /**
   * Get messages sent through this WebSocket
   */
  getSentMessages(): string[] {
    return [...this.messageQueue];
  }
}

// Store reference to created WebSocket instances
let mockWebSocketInstance: MockWebSocket | null = null;
const MockWebSocketConstructor = class {
  constructor(url: string) {
    mockWebSocketInstance = new MockWebSocket(url);
    return mockWebSocketInstance;
  }
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;
};



describe('WebSocket Integration Tests', () => {
  let store: ReturnType<typeof configureStore>;
  let wsManager: WebSocketManager;

  // Disable MSW server for WebSocket tests
  beforeAll(() => {
    server.close();
  });

  afterAll(() => {
    server.listen();
  });

  beforeEach(() => {
    vi.useRealTimers();
    // Reset mock WebSocket instance
    mockWebSocketInstance = null;

    // Create WebSocket manager
    wsManager = new WebSocketManager(
      resolveWebSocketUrl(undefined, { pageUrl: 'http://localhost:5173/' }),
      MockWebSocketConstructor as unknown as WebSocketLikeConstructor
    );
    
    // Set as singleton for middleware to use
    setWebSocketManager(wsManager);

    // Create Redux store with WebSocket middleware
    store = configureStore({
      reducer: {
        websocket: websocketReducer,
        auth: authReducer,
        environment: environmentReducer,
        [accountApi.reducerPath]: accountApi.reducer,
        [tradesApi.reducerPath]: tradesApi.reducer,
      },
      middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware().concat(accountApi.middleware, tradesApi.middleware, websocketMiddleware),
    });

    // Clear all timers
    vi.clearAllTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllTimers();
    // Disconnect WebSocket
    if (wsManager) {
      wsManager.disconnect();
    }

    // Clear singleton
    setWebSocketManager(null);

    // Clear mock instance
    mockWebSocketInstance = null;
  });

  describe('Connection Establishment', () => {
    it('should establish WebSocket connection with auth token', async () => {
      // Start connection
      const connectPromise = wsManager.connect('test-token-123', 'test');

      // Wait for WebSocket to be created
      await waitFor(() => {
        expect(mockWebSocketInstance).not.toBeNull();
      });

      // Verify URL includes token and environment
      expect(mockWebSocketInstance!.url).toContain('token=test-token-123');
      expect(mockWebSocketInstance!.url).toContain('env=test');

      // Simulate connection open
      mockWebSocketInstance!.simulateOpen();

      // Wait for connection to complete
      await connectPromise;

      // Verify connection state
      expect(wsManager.isConnected()).toBe(true);
      expect(wsManager.getState()).toBe('open');
    });

    it('should subscribe to default test and live channels on connect', async () => {
      // Start connection
      const connectPromise = wsManager.connect('test-token', 'live');

      // Wait for WebSocket to be created
      await waitFor(() => {
        expect(mockWebSocketInstance).not.toBeNull();
      });

      // Simulate connection open
      mockWebSocketInstance!.simulateOpen();

      // Wait for connection to complete
      await connectPromise;

      // Check sent messages for subscription
      const sentMessages = mockWebSocketInstance!.getSentMessages();
      expect(sentMessages.length).toBeGreaterThan(0);

      const subscribeMessage = JSON.parse(sentMessages[0]);
      expect(subscribeMessage.type).toBe('subscribe');
      expect(subscribeMessage.channels).toContain('test.balance');
      expect(subscribeMessage.channels).toContain('test.backtests');
      expect(subscribeMessage.channels).toContain('live.balance');
      expect(subscribeMessage.channels).toContain('live.trades');
      expect(subscribeMessage.channels).toContain('live.positions');
      expect(subscribeMessage.channels).toContain('live.backtests');
      expect(subscribeMessage.channels).toContain('live.marketData');
    });

    it('should include auth token in connection URL', async () => {
      const token = 'secure-jwt-token-xyz';
      const connectPromise = wsManager.connect(token, 'test');

      await waitFor(() => {
        expect(mockWebSocketInstance).not.toBeNull();
      });

      // Verify token is URL-encoded in the connection URL
      expect(mockWebSocketInstance!.url).toContain(`token=${encodeURIComponent(token)}`);

      mockWebSocketInstance!.simulateOpen();
      await connectPromise;
    });
  });

  describe('balance.updated Event', () => {
    it('should invalidate balance cache when balance.updated event is received', async () => {
      // Connect WebSocket
      const connectPromise = wsManager.connect('test-token', 'test');
      
      await waitFor(() => {
        expect(mockWebSocketInstance).not.toBeNull();
      });

      mockWebSocketInstance!.simulateOpen();
      await connectPromise;

      // Subscribe to balance.updated events
      const handler = vi.fn();
      wsManager.subscribe('balance.updated', handler);

      // Create balance.updated event
      const balanceEvent: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: new Date().toISOString(),
        data: {
          total: '1500.00',
          available: '1200.00',
          locked: '300.00',
        },
      };

      // Simulate receiving the event
      mockWebSocketInstance!.simulateMessage(JSON.stringify(balanceEvent));

      // Wait for handler to be called
      await waitFor(() => {
        expect(handler).toHaveBeenCalledWith(balanceEvent);
      });

      // Verify Redux state was updated
      const state = store.getState();
      expect(state.websocket.lastEventTime).toBeTruthy();
    });

    it('should trigger balance refetch after balance.updated event', async () => {
      // Connect WebSocket
      const connectPromise = wsManager.connect('test-token', 'test');
      
      await waitFor(() => {
        expect(mockWebSocketInstance).not.toBeNull();
      });

      mockWebSocketInstance!.simulateOpen();
      await connectPromise;

      // Track cache invalidation
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      // Create balance.updated event
      const balanceEvent: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: new Date().toISOString(),
        data: {
          total: '2000.00',
          available: '1800.00',
          locked: '200.00',
        },
      };

      // Simulate receiving the event
      mockWebSocketInstance!.simulateMessage(JSON.stringify(balanceEvent));

      // Wait for cache invalidation
      await waitFor(() => {
        expect(invalidateSpy).toHaveBeenCalledWith(['Balance']);
      });

      invalidateSpy.mockRestore();
    });
  });

  describe('trade.executed Event', () => {
    it('should invalidate trade history cache when trade.executed event is received', async () => {
      // Connect WebSocket
      const connectPromise = wsManager.connect('test-token', 'test');
      
      await waitFor(() => {
        expect(mockWebSocketInstance).not.toBeNull();
      });

      mockWebSocketInstance!.simulateOpen();
      await connectPromise;

      // Subscribe to trade.executed events
      const handler = vi.fn();
      wsManager.subscribe('trade.executed', handler);

      // Create trade.executed event
      const tradeEvent: WebSocketEvent = {
        type: 'trade.executed',
        environment: 'test',
        timestamp: new Date().toISOString(),
        data: {
          id: 'trade-123',
          symbol: 'BTC/USDT',
          side: 'BUY',
          entryPrice: '45000.00',
          exitPrice: '46000.00',
          quantity: '0.1',
          profitLoss: '100.00',
          profitLossPercentage: '2.22',
        },
      };

      // Simulate receiving the event
      mockWebSocketInstance!.simulateMessage(JSON.stringify(tradeEvent));

      // Wait for handler to be called
      await waitFor(() => {
        expect(handler).toHaveBeenCalledWith(tradeEvent);
      });

      // Verify Redux state was updated
      const state = store.getState();
      expect(state.websocket.lastEventTime).toBeTruthy();
    });

    it('should invalidate both balance and performance caches after trade.executed', async () => {
      // Connect WebSocket
      const connectPromise = wsManager.connect('test-token', 'test');
      
      await waitFor(() => {
        expect(mockWebSocketInstance).not.toBeNull();
      });

      mockWebSocketInstance!.simulateOpen();
      await connectPromise;

      // Track cache invalidation
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      // Create trade.executed event
      const tradeEvent: WebSocketEvent = {
        type: 'trade.executed',
        environment: 'test',
        timestamp: new Date().toISOString(),
        data: {
          id: 'trade-456',
          symbol: 'ETH/USDT',
          side: 'SELL',
          profitLoss: '50.00',
        },
      };

      // Simulate receiving the event
      mockWebSocketInstance!.simulateMessage(JSON.stringify(tradeEvent));

      // Wait for cache invalidation
      await waitFor(() => {
        expect(invalidateSpy).toHaveBeenCalledWith(['Balance', 'Performance']);
      });

      invalidateSpy.mockRestore();
    });
  });

  describe('position.updated Event', () => {
    it('should update position display when position.updated event is received', async () => {
      // Connect WebSocket
      const connectPromise = wsManager.connect('test-token', 'test');
      
      await waitFor(() => {
        expect(mockWebSocketInstance).not.toBeNull();
      });

      mockWebSocketInstance!.simulateOpen();
      await connectPromise;

      // Subscribe to position.updated events
      const handler = vi.fn();
      wsManager.subscribe('position.updated', handler);

      // Create position.updated event
      const positionEvent: WebSocketEvent = {
        type: 'position.updated',
        environment: 'test',
        timestamp: new Date().toISOString(),
        data: {
          positionId: 'pos-789',
          symbol: 'BTC/USDT',
          currentPrice: '47000.00',
          unrealizedPnL: '200.00',
          unrealizedPnLPercentage: '4.44',
        },
      };

      // Simulate receiving the event
      mockWebSocketInstance!.simulateMessage(JSON.stringify(positionEvent));

      // Wait for handler to be called
      await waitFor(() => {
        expect(handler).toHaveBeenCalledWith(positionEvent);
      });

      // Verify Redux state was updated
      const state = store.getState();
      expect(state.websocket.lastEventTime).toBeTruthy();
    });

    it('should invalidate balance cache after position.updated', async () => {
      // Connect WebSocket
      const connectPromise = wsManager.connect('test-token', 'test');
      
      await waitFor(() => {
        expect(mockWebSocketInstance).not.toBeNull();
      });

      mockWebSocketInstance!.simulateOpen();
      await connectPromise;

      // Track cache invalidation
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      // Create position.updated event
      const positionEvent: WebSocketEvent = {
        type: 'position.updated',
        environment: 'test',
        timestamp: new Date().toISOString(),
        data: {
          positionId: 'pos-101',
          currentPrice: '48000.00',
          unrealizedPnL: '300.00',
        },
      };

      // Simulate receiving the event
      mockWebSocketInstance!.simulateMessage(JSON.stringify(positionEvent));

      // Wait for cache invalidation
      await waitFor(() => {
        expect(invalidateSpy).toHaveBeenCalledWith(['Balance']);
      });

      invalidateSpy.mockRestore();
    });
  });

  describe('Reconnection Logic', () => {
    it('should attempt reconnection after connection loss', async () => {
      vi.useFakeTimers();

      try {
        // Connect WebSocket
        const connectPromise = wsManager.connect('test-token', 'test');
        expect(mockWebSocketInstance).not.toBeNull();

        mockWebSocketInstance!.simulateOpen();
        await connectPromise;
        expect(wsManager.isConnected()).toBe(true);

        // Simulate connection loss
        const firstWs = mockWebSocketInstance;
        firstWs!.simulateClose(1006, 'Connection lost');
        expect(wsManager.isConnected()).toBe(false);

        // Fast-forward time to trigger reconnection (5 seconds)
        vi.advanceTimersByTime(5000);

        // A new WebSocket should be created for reconnect
        expect(mockWebSocketInstance).not.toBeNull();
        expect(mockWebSocketInstance).not.toBe(firstWs);

        // Simulate successful reconnection
        mockWebSocketInstance!.simulateOpen();
        expect(wsManager.isConnected()).toBe(true);
      } finally {
        vi.useRealTimers();
      }
    });

    it('should limit reconnection attempts to 3', async () => {
      vi.useFakeTimers();

      try {
        // Connect WebSocket
        const connectPromise = wsManager.connect('test-token', 'test');
        expect(mockWebSocketInstance).not.toBeNull();

        mockWebSocketInstance!.simulateOpen();
        await connectPromise;

        // Simulate connection loss and failed reconnection attempts
        for (let i = 0; i < 3; i++) {
          mockWebSocketInstance!.simulateClose(1006, 'Connection lost');

          // Fast-forward to trigger reconnection
          vi.advanceTimersByTime(5000);
          expect(mockWebSocketInstance).not.toBeNull();

          // Simulate immediate failure
          mockWebSocketInstance!.simulateClose(1006, 'Connection failed');
        }

        // After 3 attempts, should not try again
        const lastWs = mockWebSocketInstance;
        vi.advanceTimersByTime(10000);

        // Should not create new WebSocket
        expect(mockWebSocketInstance).toBe(lastWs);
      } finally {
        vi.useRealTimers();
      }
    });

    it('should reset reconnection attempts after successful connection', async () => {
      vi.useFakeTimers();

      try {
        // Connect WebSocket
        const connectPromise = wsManager.connect('test-token', 'test');
        expect(mockWebSocketInstance).not.toBeNull();

        mockWebSocketInstance!.simulateOpen();
        await connectPromise;

        // Simulate connection loss
        mockWebSocketInstance!.simulateClose(1006, 'Connection lost');

        // Fast-forward to trigger reconnection
        vi.advanceTimersByTime(5000);
        expect(mockWebSocketInstance).not.toBeNull();

        // Simulate successful reconnection
        mockWebSocketInstance!.simulateOpen();
        expect(wsManager.isConnected()).toBe(true);

        // Lose connection again and verify reconnect still happens
        const reconnectedWs = mockWebSocketInstance;
        reconnectedWs!.simulateClose(1006, 'Connection lost again');

        vi.advanceTimersByTime(5000);
        expect(mockWebSocketInstance).not.toBeNull();
        expect(mockWebSocketInstance).not.toBe(reconnectedWs);
      } finally {
        vi.useRealTimers();
      }
    });

    it('should resubscribe to channels after reconnection', async () => {
      vi.useFakeTimers();

      try {
        // Connect WebSocket
        const connectPromise = wsManager.connect('test-token', 'live');
        expect(mockWebSocketInstance).not.toBeNull();

        mockWebSocketInstance!.simulateOpen();
        await connectPromise;

        // Simulate connection loss
        const firstWs = mockWebSocketInstance;
        firstWs!.simulateClose(1006, 'Connection lost');

        // Fast-forward to trigger reconnection
        vi.advanceTimersByTime(5000);
        expect(mockWebSocketInstance).not.toBeNull();
        expect(mockWebSocketInstance).not.toBe(firstWs);

        // Simulate successful reconnection
        mockWebSocketInstance!.simulateOpen();
        expect(wsManager.isConnected()).toBe(true);

        // Check that subscription message was sent again
        const sentMessages = mockWebSocketInstance!.getSentMessages();
        expect(sentMessages.length).toBeGreaterThan(0);

        const subscribeMessage = JSON.parse(sentMessages[0]);
        expect(subscribeMessage.type).toBe('subscribe');
        expect(subscribeMessage.channels).toContain('test.balance');
        expect(subscribeMessage.channels).toContain('test.backtests');
        expect(subscribeMessage.channels).toContain('live.balance');
        expect(subscribeMessage.channels).toContain('live.backtests');
        expect(subscribeMessage.channels).toContain('live.marketData');
      } finally {
        vi.useRealTimers();
      }
    });
  });
  describe('Event Throttling', () => {
    it('should throttle events to max 1 per second per type', async () => {
      vi.useFakeTimers();

      try {
        // Connect WebSocket
        const connectPromise = wsManager.connect('test-token', 'test');
        expect(mockWebSocketInstance).not.toBeNull();

        mockWebSocketInstance!.simulateOpen();
        await connectPromise;

        // Track cache invalidation
        const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

        // Send multiple balance.updated events rapidly
        for (let i = 0; i < 5; i++) {
          const balanceEvent: WebSocketEvent = {
            type: 'balance.updated',
            environment: 'test',
            timestamp: new Date().toISOString(),
            data: { total: `${1000 + i * 100}.00` },
          };

          mockWebSocketInstance!.simulateMessage(JSON.stringify(balanceEvent));
        }

        // Should process first event immediately
        expect(invalidateSpy).toHaveBeenCalledTimes(1);

        // Fast-forward 1 second
        vi.advanceTimersByTime(1000);

        // Should process pending event after throttle interval
        expect(invalidateSpy).toHaveBeenCalledTimes(2);

        invalidateSpy.mockRestore();
      } finally {
        vi.useRealTimers();
      }
    });

    it('should throttle different event types independently', async () => {
      vi.useFakeTimers();

      try {
        // Connect WebSocket
        const connectPromise = wsManager.connect('test-token', 'test');
        expect(mockWebSocketInstance).not.toBeNull();

        mockWebSocketInstance!.simulateOpen();
        await connectPromise;

        // Track cache invalidation
        const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

        // Send balance.updated event
        const balanceEvent: WebSocketEvent = {
          type: 'balance.updated',
          environment: 'test',
          timestamp: new Date().toISOString(),
          data: { total: '1000.00' },
        };
        mockWebSocketInstance!.simulateMessage(JSON.stringify(balanceEvent));

        // Send trade.executed event immediately after
        const tradeEvent: WebSocketEvent = {
          type: 'trade.executed',
          environment: 'test',
          timestamp: new Date().toISOString(),
          data: { id: 'trade-1' },
        };
        mockWebSocketInstance!.simulateMessage(JSON.stringify(tradeEvent));

        // Both should be processed immediately (different event types)
        expect(invalidateSpy).toHaveBeenCalledTimes(2);

        invalidateSpy.mockRestore();
      } finally {
        vi.useRealTimers();
      }
    });
  });
  describe('Tab Visibility', () => {
    it('should pause event processing when tab becomes inactive', async () => {
      // Connect WebSocket
      const connectPromise = wsManager.connect('test-token', 'test');
      
      await waitFor(() => {
        expect(mockWebSocketInstance).not.toBeNull();
      });

      mockWebSocketInstance!.simulateOpen();
      await connectPromise;

      // Track cache invalidation
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      // Simulate tab becoming inactive
      Object.defineProperty(document, 'hidden', {
        configurable: true,
        get: () => true,
      });
      document.dispatchEvent(new Event('visibilitychange'));

      // Send balance.updated event while tab is inactive
      const balanceEvent: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: new Date().toISOString(),
        data: { total: '1000.00' },
      };
      mockWebSocketInstance!.simulateMessage(JSON.stringify(balanceEvent));

      // Event should not be processed immediately
      await new Promise((resolve) => setTimeout(resolve, 100));
      expect(invalidateSpy).not.toHaveBeenCalled();

      // Simulate tab becoming active again
      Object.defineProperty(document, 'hidden', {
        configurable: true,
        get: () => false,
      });
      document.dispatchEvent(new Event('visibilitychange'));

      // Event should now be processed
      await waitFor(() => {
        expect(invalidateSpy).toHaveBeenCalled();
      });

      invalidateSpy.mockRestore();
    });
  });
});








