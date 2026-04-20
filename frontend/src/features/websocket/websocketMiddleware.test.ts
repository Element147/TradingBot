/**
 * WebSocket Middleware Tests
 * 
 * Tests for WebSocket Redux middleware functionality:
 * - Event subscription and handling
 * - Redux action dispatching
 * - Event throttling (max 1 update per second per type)
 * - Tab visibility handling (pause when inactive)
 */

import { configureStore } from '@reduxjs/toolkit';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';


import { getWebSocketManager, WebSocketEvent } from '../../services/websocket';
import { accountApi } from '../account/accountApi';
import { backtestApi } from '../backtest/backtestApi';
import { marketDataApi } from '../marketData/marketDataApi';
import { tradesApi } from '../trades/tradesApi';

import { websocketMiddleware } from './websocketMiddleware';
import websocketReducer from './websocketSlice';

// Mock the WebSocket manager
const handlers = new Map<string, Set<(event: WebSocketEvent) => void>>();
const subscribeMock = vi.fn((eventType: string, handler: (event: WebSocketEvent) => void) => {
  if (!handlers.has(eventType)) {
    handlers.set(eventType, new Set());
  }
  handlers.get(eventType)!.add(handler);

  return () => {
    handlers.get(eventType)?.delete(handler);
  };
});

vi.mock('../../services/websocket', () => ({
    getWebSocketManager: vi.fn(() => ({
      subscribe: subscribeMock,
      // Helper to trigger events in tests
      __triggerEvent: (event: WebSocketEvent) => {
        const typeHandlers = handlers.get(event.type);
        if (typeHandlers) {
          typeHandlers.forEach((handler) => handler(event));
        }
      },
    })),
  }));

describe('websocketMiddleware', () => {
  let store: ReturnType<typeof configureStore>;
  let wsManager: ReturnType<typeof getWebSocketManager>;

  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    
    // Clear handlers between tests
    handlers.clear();
    subscribeMock.mockClear();

    // Create store with middleware
    store = configureStore({
      reducer: {
        websocket: websocketReducer,
        [accountApi.reducerPath]: accountApi.reducer,
        [backtestApi.reducerPath]: backtestApi.reducer,
        [marketDataApi.reducerPath]: marketDataApi.reducer,
        [tradesApi.reducerPath]: tradesApi.reducer,
      },
      middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware().concat(
          accountApi.middleware,
          backtestApi.middleware,
          marketDataApi.middleware,
          tradesApi.middleware,
          websocketMiddleware
        ),
    });

    wsManager = getWebSocketManager();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('Event Subscription', () => {
    it('should subscribe to WebSocket events on initialization', () => {
      expect(subscribeMock).toHaveBeenCalledWith('balance.updated', expect.any(Function));
      expect(subscribeMock).toHaveBeenCalledWith('trade.executed', expect.any(Function));
      expect(subscribeMock).toHaveBeenCalledWith('position.updated', expect.any(Function));
      expect(subscribeMock).toHaveBeenCalledWith('strategy.status', expect.any(Function));
      expect(subscribeMock).toHaveBeenCalledWith('risk.alert', expect.any(Function));
      expect(subscribeMock).toHaveBeenCalledWith('system.error', expect.any(Function));
      expect(subscribeMock).toHaveBeenCalledWith('backtest.progress', expect.any(Function));
      expect(subscribeMock).toHaveBeenCalledWith('marketData.import.progress', expect.any(Function));
    });
  });

  describe('Event Handling', () => {
    it('should dispatch eventReceived action when balance.updated event is received', () => {
      const event: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { total: '1000.00' },
      };

      // Trigger event
      (wsManager as any).__triggerEvent(event);

      // Check that eventReceived was dispatched
      const state = store.getState();
      expect(state.websocket.lastEventTime).toBe('2024-03-09T12:00:00Z');
    });

    it('should invalidate balance cache when balance.updated event is received', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      const event: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { total: '1000.00' },
      };

      // Trigger event
      (wsManager as any).__triggerEvent(event);

      // Check that balance cache was invalidated
      expect(invalidateSpy).toHaveBeenCalledWith(['Balance']);
    });

    it('should invalidate balance and performance caches when trade.executed event is received', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      const event: WebSocketEvent = {
        type: 'trade.executed',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { tradeId: '123' },
      };

      // Trigger event
      (wsManager as any).__triggerEvent(event);

      // Check that both caches were invalidated
      expect(invalidateSpy).toHaveBeenCalledWith(['Balance', 'Performance']);
    });

    it('should invalidate balance cache when position.updated event is received', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      const event: WebSocketEvent = {
        type: 'position.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { positionId: '456' },
      };

      // Trigger event
      (wsManager as any).__triggerEvent(event);

      // Check that balance cache was invalidated
      expect(invalidateSpy).toHaveBeenCalledWith(['Balance']);
    });

    it('should handle strategy.status events without errors', () => {
      const event: WebSocketEvent = {
        type: 'strategy.status',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { strategyId: '789', status: 'RUNNING' },
      };

      // Should not throw
      expect(() => {
        (wsManager as any).__triggerEvent(event);
      }).not.toThrow();
    });

    it('should handle risk.alert events without errors', () => {
      const event: WebSocketEvent = {
        type: 'risk.alert',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { severity: 'high', message: 'Drawdown limit reached' },
      };

      // Should not throw
      expect(() => {
        (wsManager as any).__triggerEvent(event);
      }).not.toThrow();
    });

    it('should handle system.error events without errors', () => {
      const event: WebSocketEvent = {
        type: 'system.error',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { code: 'ERR_500', message: 'Internal server error' },
      };

      // Should not throw
      expect(() => {
        (wsManager as any).__triggerEvent(event);
      }).not.toThrow();
    });

    it('should stream backtest progress into the cached history list', () => {
      const updateQueryDataSpy = vi.spyOn(backtestApi.util, 'updateQueryData');
      const invalidateTagsSpy = vi.spyOn(backtestApi.util, 'invalidateTags');

      const event: WebSocketEvent = {
        type: 'backtest.progress',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: {
          backtestId: 42,
          strategyId: 'SMA_CROSSOVER',
          datasetName: 'BTC 1h',
          experimentName: 'WebSocket test',
          symbol: 'BTC/USDT',
          timeframe: '1h',
          executionStatus: 'RUNNING',
          validationStatus: 'PENDING',
          feesBps: 10,
          slippageBps: 3,
          timestamp: '2024-03-09T11:59:00Z',
          initialBalance: 1000,
          finalBalance: 1002,
          executionStage: 'SIMULATING',
          progressPercent: 64,
          processedCandles: 64,
          totalCandles: 100,
          currentDataTimestamp: '2024-01-03T00:00:00Z',
          statusMessage: 'Replaying candle 64 of 100.',
          lastProgressAt: '2024-03-09T12:00:00Z',
          startedAt: '2024-03-09T11:59:30Z',
          completedAt: null,
          errorMessage: null,
        },
      };

      (wsManager as any).__triggerEvent(event);

      expect(invalidateTagsSpy).toHaveBeenCalledWith(['Backtests']);
      expect(updateQueryDataSpy).toHaveBeenCalledWith(
        'getBacktestDetails',
        42,
        expect.any(Function)
      );
    });

    it('should stream market-data import progress into the cached jobs list', () => {
      const updateQueryDataSpy = vi.spyOn(marketDataApi.util, 'updateQueryData');

      const event: WebSocketEvent = {
        type: 'marketData.import.progress',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: {
          id: 12,
          providerId: 'binance',
          providerLabel: 'Binance',
          assetType: 'CRYPTO',
          datasetName: 'BTC majors',
          symbolsCsv: 'BTC/USDT,ETH/USDT',
          timeframe: '1h',
          startDate: '2024-03-01',
          endDate: '2024-03-31',
          adjusted: false,
          regularSessionOnly: false,
          status: 'RUNNING',
          statusMessage: 'Fetched 240 bars for BTC/USDT.',
          nextRetryAt: null,
          currentSymbolIndex: 0,
          totalSymbols: 2,
          currentSymbol: 'BTC/USDT',
          importedRowCount: 240,
          datasetId: null,
          datasetReady: false,
          currentChunkStart: '2024-03-03T00:00:00Z',
          attemptCount: 1,
          createdAt: '2024-03-09T11:59:00Z',
          updatedAt: '2024-03-09T12:00:00Z',
          startedAt: '2024-03-09T11:59:30Z',
          completedAt: null,
        },
      };

      (wsManager as any).__triggerEvent(event);

      expect(updateQueryDataSpy).toHaveBeenCalledWith(
        'getMarketDataJobs',
        undefined,
        expect.any(Function)
      );
    });
  });

  describe('Event Throttling', () => {
    it('should process first event immediately', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      const event: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { total: '1000.00' },
      };

      // Trigger event
      (wsManager as any).__triggerEvent(event);

      // Should be processed immediately
      expect(invalidateSpy).toHaveBeenCalledTimes(1);
    });

    it('should throttle events of same type within 1 second', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      const event1: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00.000Z',
        data: { total: '1000.00' },
      };

      const event2: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00.500Z',
        data: { total: '1001.00' },
      };

      // Trigger first event
      (wsManager as any).__triggerEvent(event1);
      expect(invalidateSpy).toHaveBeenCalledTimes(1);

      // Trigger second event 500ms later (should be throttled)
      vi.advanceTimersByTime(500);
      (wsManager as any).__triggerEvent(event2);
      expect(invalidateSpy).toHaveBeenCalledTimes(1); // Still 1, throttled

      // Advance time to complete throttle interval
      vi.advanceTimersByTime(500);
      expect(invalidateSpy).toHaveBeenCalledTimes(2); // Now processed
    });

    it('should allow events of different types to process independently', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      const balanceEvent: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { total: '1000.00' },
      };

      const tradeEvent: WebSocketEvent = {
        type: 'trade.executed',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00.100Z',
        data: { tradeId: '123' },
      };

      // Trigger balance event
      (wsManager as any).__triggerEvent(balanceEvent);
      expect(invalidateSpy).toHaveBeenCalledTimes(1);

      // Trigger trade event immediately after (different type, should not be throttled)
      (wsManager as any).__triggerEvent(tradeEvent);
      expect(invalidateSpy).toHaveBeenCalledTimes(2);
    });

    it('should process events after throttle interval expires', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      const event1: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { total: '1000.00' },
      };

      const event2: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:01Z',
        data: { total: '1001.00' },
      };

      // Trigger first event
      (wsManager as any).__triggerEvent(event1);
      expect(invalidateSpy).toHaveBeenCalledTimes(1);

      // Advance time by 1 second
      vi.advanceTimersByTime(1000);

      // Trigger second event (should process immediately)
      (wsManager as any).__triggerEvent(event2);
      expect(invalidateSpy).toHaveBeenCalledTimes(2);
    });
  });

  describe('Tab Visibility Handling', () => {
    it('should pause event processing when tab becomes inactive', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      // Simulate tab becoming inactive
      Object.defineProperty(document, 'hidden', {
        configurable: true,
        get: () => true,
      });
      document.dispatchEvent(new Event('visibilitychange'));

      const event: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { total: '1000.00' },
      };

      // Trigger event while tab is inactive
      (wsManager as any).__triggerEvent(event);

      // Event should be deferred, not processed immediately
      expect(invalidateSpy).not.toHaveBeenCalled();
    });

    it('should resume event processing when tab becomes active', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      // Simulate tab becoming inactive
      Object.defineProperty(document, 'hidden', {
        configurable: true,
        get: () => true,
      });
      document.dispatchEvent(new Event('visibilitychange'));

      const event: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { total: '1000.00' },
      };

      // Trigger event while tab is inactive
      (wsManager as any).__triggerEvent(event);
      expect(invalidateSpy).not.toHaveBeenCalled();

      // Simulate tab becoming active
      Object.defineProperty(document, 'hidden', {
        configurable: true,
        get: () => false,
      });
      document.dispatchEvent(new Event('visibilitychange'));

      // Pending event should now be processed
      expect(invalidateSpy).toHaveBeenCalledWith(['Balance']);
    });

    it('should process new events immediately when tab is active', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      // Ensure tab is active
      Object.defineProperty(document, 'hidden', {
        configurable: true,
        get: () => false,
      });

      const event: WebSocketEvent = {
        type: 'balance.updated',
        environment: 'test',
        timestamp: '2024-03-09T12:00:00Z',
        data: { total: '1000.00' },
      };

      // Trigger event while tab is active
      (wsManager as any).__triggerEvent(event);

      // Event should be processed immediately
      expect(invalidateSpy).toHaveBeenCalledWith(['Balance']);
    });
  });

  describe('Multiple Events', () => {
    it('should handle multiple events of different types correctly', () => {
      const invalidateSpy = vi.spyOn(accountApi.util, 'invalidateTags');

      const events: WebSocketEvent[] = [
        {
          type: 'balance.updated',
          environment: 'test',
          timestamp: '2024-03-09T12:00:00Z',
          data: { total: '1000.00' },
        },
        {
          type: 'trade.executed',
          environment: 'test',
          timestamp: '2024-03-09T12:00:01Z',
          data: { tradeId: '123' },
        },
        {
          type: 'position.updated',
          environment: 'test',
          timestamp: '2024-03-09T12:00:02Z',
          data: { positionId: '456' },
        },
      ];

      // Trigger all events
      events.forEach((event) => {
        (wsManager as any).__triggerEvent(event);
        vi.advanceTimersByTime(1000);
      });

      // All events should be processed
      expect(invalidateSpy).toHaveBeenCalledTimes(3);
      expect(invalidateSpy).toHaveBeenCalledWith(['Balance']);
      expect(invalidateSpy).toHaveBeenCalledWith(['Balance', 'Performance']);
    });

    it('should update lastEventTime for each event', () => {
      const events: WebSocketEvent[] = [
        {
          type: 'balance.updated',
          environment: 'test',
          timestamp: '2024-03-09T12:00:00Z',
          data: { total: '1000.00' },
        },
        {
          type: 'trade.executed',
          environment: 'test',
          timestamp: '2024-03-09T12:00:01Z',
          data: { tradeId: '123' },
        },
      ];

      // Trigger first event
      (wsManager as any).__triggerEvent(events[0]);
      let state = store.getState();
      expect(state.websocket.lastEventTime).toBe('2024-03-09T12:00:00Z');

      // Trigger second event
      vi.advanceTimersByTime(1000);
      (wsManager as any).__triggerEvent(events[1]);
      state = store.getState();
      expect(state.websocket.lastEventTime).toBe('2024-03-09T12:00:01Z');
    });
  });
});

