import { configureStore } from '@reduxjs/toolkit';
import { renderHook, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockBaseQueryWithEnvironment } = vi.hoisted(() => ({
  mockBaseQueryWithEnvironment: vi.fn(
    (
      args:
        | string
        | {
            url: string;
            params?: Record<string, string | number>;
            headers?: Headers;
          }
    ) => {
      const url = typeof args === 'string' ? args : args.url;
      const timeframe = typeof args === 'string' ? undefined : args.params?.timeframe;
      const limit =
        typeof args === 'string' ? undefined : Number(args.params?.limit ?? Number.NaN);
      const executionContext =
        typeof args === 'string' ? null : args.headers?.get('X-Execution-Context');
      const environment = typeof args === 'string' ? null : args.headers?.get('X-Environment');

      if (url === '/api/account/balance') {
        return Promise.resolve({
          data: {
            total: '10000.00',
            available: '8500.00',
            locked: '1500.00',
            assets: [],
            lastSync: '2026-03-09T12:00:00Z',
            executionContext,
            environment,
          },
        });
      }

      if (url === '/api/account/performance') {
        return Promise.resolve({
          data: {
            totalProfitLoss: timeframe === 'all' ? '1250.00' : '125.00',
            profitLossPercentage: timeframe === 'all' ? '12.50' : '1.25',
            winRate: '58.3',
            tradeCount: timeframe === 'all' ? 48 : 6,
            cashRatio: '85.0',
            executionContext,
            environment,
          },
        });
      }

      if (url === '/api/positions/open') {
        return Promise.resolve({
          data: [
            {
              id: executionContext === 'live' ? 'live-position-1' : 'paper-position-1',
              strategyId: executionContext === 'live' ? 'live-strategy' : 'paper-strategy',
              strategyName:
                executionContext === 'live' ? 'Live Momentum Monitor' : 'Paper Breakout Desk',
              symbol: executionContext === 'live' ? 'BTCUSDT' : 'ETHUSDT',
              quantity: executionContext === 'live' ? '0.02' : '1.25',
              entryPrice: executionContext === 'live' ? '64000.00' : '3200.00',
              currentPrice: executionContext === 'live' ? '64550.00' : '3255.00',
              entryTime: '2026-03-09T10:00:00Z',
              unrealizedPnL: executionContext === 'live' ? '11.00' : '68.75',
              unrealizedPnLPercentage: executionContext === 'live' ? '0.86' : '1.72',
              side: 'LONG',
            },
          ],
        });
      }

      if (url === '/api/trades/recent') {
        return Promise.resolve({
          data: Array.from({ length: Number.isFinite(limit) ? limit : 10 }, (_, index) => ({
            id: `trade-${index + 1}`,
            strategyId: executionContext === 'live' ? 'live-strategy' : 'paper-strategy',
            strategyName:
              executionContext === 'live' ? 'Live Momentum Monitor' : 'Paper Breakout Desk',
            symbol: executionContext === 'live' ? 'BTCUSDT' : 'ETHUSDT',
            side: index % 2 === 0 ? 'BUY' : 'SELL',
            quantity: '0.01',
            entryPrice: '64000.00',
            exitPrice: '64250.00',
            entryTime: `2026-03-09T11:${String(index).padStart(2, '0')}:00Z`,
            exitTime: `2026-03-09T12:${String(index).padStart(2, '0')}:00Z`,
            duration: '60m',
            profitLoss: '2.50',
            profitLossPercentage: '0.39',
            fees: '0.10',
            slippage: '0.05',
            status: 'CLOSED',
          })),
        });
      }

      return Promise.resolve({
        error: {
          status: 404,
          data: { message: `Unhandled test query for ${url}` },
        },
      });
    }
  ),
}));

vi.mock('@/services/api', () => ({
  baseQueryWithEnvironment: mockBaseQueryWithEnvironment,
  withExecutionContext: (
    request: string | { url: string; params?: Record<string, string | number>; headers?: Headers },
    executionContext: 'research' | 'forward-test' | 'paper' | 'live'
  ) => {
    const headers = new Headers(typeof request === 'string' ? undefined : request.headers);
    headers.set('X-Execution-Context', executionContext);
    headers.set('X-Environment', executionContext === 'live' ? 'live' : 'test');

    if (typeof request === 'string') {
      return { url: request, headers };
    }

    return {
      ...request,
      headers,
    };
  },
}));

import {
  accountApi,
  useGetBalanceQuery,
  useGetOpenPositionsQuery,
  useGetPerformanceQuery,
  useGetRecentTradesQuery,
} from './accountApi';

import authReducer from '@/features/auth/authSlice';
import environmentReducer from '@/features/environment/environmentSlice';

describe('accountApi', () => {
  let store: ReturnType<typeof configureStore>;

  beforeEach(() => {
    mockBaseQueryWithEnvironment.mockClear();

    store = configureStore({
      reducer: {
        auth: authReducer,
        environment: environmentReducer,
        [accountApi.reducerPath]: accountApi.reducer,
      },
      middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware().concat(accountApi.middleware),
    });
  });

  describe('getBalance endpoint', () => {
    it('should define getBalance query', () => {
      expect(accountApi.endpoints.getBalance).toBeDefined();
    });

    it('should have correct query configuration', () => {
      const endpoint = accountApi.endpoints.getBalance;
      expect(endpoint.name).toBe('getBalance');
    });

    it('should provide Balance tag', () => {
      expect(accountApi.endpoints.getBalance).toBeDefined();
    });

    it('should query correct endpoint', async () => {
      const { result } = renderHook(() => useGetBalanceQuery(), {
        wrapper: ({ children }) => <Provider store={store}>{children}</Provider>,
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });
      expect(mockBaseQueryWithEnvironment).toHaveBeenCalled();
    });

    it('supports live execution-context overrides without changing global mode', async () => {
      const { result } = renderHook(
        () => useGetBalanceQuery({ executionContext: 'live' }),
        {
          wrapper: ({ children }) => <Provider store={store}>{children}</Provider>,
        }
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      const request = mockBaseQueryWithEnvironment.mock.calls.at(-1)?.[0] as {
        url: string;
        headers: Headers;
      };
      expect(request.url).toBe('/api/account/balance');
      expect(request.headers.get('X-Execution-Context')).toBe('live');
      expect(request.headers.get('X-Environment')).toBe('live');
      expect(store.getState().environment.mode).toBe('test');
    });
  });

  describe('getPerformance endpoint', () => {
    it('should define getPerformance query', () => {
      expect(accountApi.endpoints.getPerformance).toBeDefined();
    });

    it('should have correct query configuration', () => {
      const endpoint = accountApi.endpoints.getPerformance;
      expect(endpoint.name).toBe('getPerformance');
    });

    it('should provide Performance tag', () => {
      expect(accountApi.endpoints.getPerformance).toBeDefined();
    });

    it('should accept timeframe parameter', async () => {
      const { result } = renderHook(() => useGetPerformanceQuery('today'), {
        wrapper: ({ children }) => <Provider store={store}>{children}</Provider>,
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });
    });

    it('should accept all valid timeframe values', async () => {
      const timeframes: Array<'today' | 'week' | 'month' | 'all'> = [
        'today',
        'week',
        'month',
        'all',
      ];

      for (const timeframe of timeframes) {
        const { result, unmount } = renderHook(() => useGetPerformanceQuery(timeframe), {
          wrapper: ({ children }) => <Provider store={store}>{children}</Provider>,
        });

        await waitFor(() => {
          expect(result.current.isSuccess).toBe(true);
        });

        unmount();
      }
    });

    it('applies execution-context overrides to parameterized performance reads', async () => {
      const { result } = renderHook(
        () => useGetPerformanceQuery({ timeframe: 'month', executionContext: 'live' }),
        {
          wrapper: ({ children }) => <Provider store={store}>{children}</Provider>,
        }
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      const request = mockBaseQueryWithEnvironment.mock.calls.at(-1)?.[0] as {
        url: string;
        params: Record<string, string>;
        headers: Headers;
      };
      expect(request.url).toBe('/api/account/performance');
      expect(request.params).toEqual({ timeframe: 'month' });
      expect(request.headers.get('X-Execution-Context')).toBe('live');
      expect(request.headers.get('X-Environment')).toBe('live');
    });
  });

  describe('execution-context aware account reads', () => {
    it('routes open positions through explicit execution-context headers', async () => {
      const { result } = renderHook(
        () => useGetOpenPositionsQuery({ executionContext: 'live' }),
        {
          wrapper: ({ children }) => <Provider store={store}>{children}</Provider>,
        }
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      const request = mockBaseQueryWithEnvironment.mock.calls.at(-1)?.[0] as {
        url: string;
        headers: Headers;
      };
      expect(request.url).toBe('/api/positions/open');
      expect(request.headers.get('X-Execution-Context')).toBe('live');
      expect(result.current.data?.[0]?.symbol).toBe('BTCUSDT');
    });

    it('routes recent-trades queries through explicit execution-context headers', async () => {
      const { result } = renderHook(
        () => useGetRecentTradesQuery({ limit: 8, executionContext: 'live' }),
        {
          wrapper: ({ children }) => <Provider store={store}>{children}</Provider>,
        }
      );

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      const request = mockBaseQueryWithEnvironment.mock.calls.at(-1)?.[0] as {
        url: string;
        params: Record<string, number>;
        headers: Headers;
      };
      expect(request.url).toBe('/api/trades/recent');
      expect(request.params).toEqual({ limit: 8 });
      expect(request.headers.get('X-Execution-Context')).toBe('live');
      expect(result.current.data).toHaveLength(8);
    });
  });

  describe('API configuration', () => {
    it('should have correct reducerPath', () => {
      expect(accountApi.reducerPath).toBe('accountApi');
    });

    it('should define Balance and Performance tag types', () => {
      expect(accountApi.reducerPath).toBe('accountApi');
      expect(accountApi.endpoints.getBalance).toBeDefined();
      expect(accountApi.endpoints.getPerformance).toBeDefined();
    });

    it('should export hooks', () => {
      expect(useGetBalanceQuery).toBeDefined();
      expect(useGetPerformanceQuery).toBeDefined();
    });
  });

  describe('Cache invalidation', () => {
    it('should invalidate Balance tag when balance data changes', async () => {
      const { result } = renderHook(() => useGetBalanceQuery(), {
        wrapper: ({ children }) => <Provider store={store}>{children}</Provider>,
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      store.dispatch(accountApi.util.invalidateTags(['Balance']));

      await waitFor(() => {
        expect(mockBaseQueryWithEnvironment).toHaveBeenCalledTimes(2);
      });
    });

    it('should invalidate Performance tag when performance data changes', async () => {
      const { result } = renderHook(() => useGetPerformanceQuery('today'), {
        wrapper: ({ children }) => <Provider store={store}>{children}</Provider>,
      });

      await waitFor(() => {
        expect(result.current.isSuccess).toBe(true);
      });

      store.dispatch(accountApi.util.invalidateTags(['Performance']));

      await waitFor(() => {
        expect(mockBaseQueryWithEnvironment).toHaveBeenCalledTimes(2);
      });
    });
  });
});
