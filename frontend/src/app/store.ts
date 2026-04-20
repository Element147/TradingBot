import { configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';

import { accountApi } from '@/features/account/accountApi';
import { authApi } from '@/features/auth/authApi';
import authReducer from '@/features/auth/authSlice';
import { backtestApi } from '@/features/backtest/backtestApi';
import environmentReducer from '@/features/environment/environmentSlice';
import { forwardTestingApi } from '@/features/forwardTesting/forwardTestingApi';
import { marketDataApi } from '@/features/marketData/marketDataApi';
import { paperApi } from '@/features/paperApi';
import { riskApi } from '@/features/risk/riskApi';
import { exchangeApi } from '@/features/settings/exchangeApi';
import settingsReducer from '@/features/settings/settingsSlice';
import { strategiesApi } from '@/features/strategies/strategiesApi';
import { tradesApi } from '@/features/trades/tradesApi';
import { websocketMiddleware } from '@/features/websocket/websocketMiddleware';
import websocketReducer from '@/features/websocket/websocketSlice';

/**
 * Redux store configuration with RTK Query integration
 * 
 * Features:
 * - Centralized state management
 * - RTK Query for API caching and data fetching
 * - Redux DevTools integration (development only)
 * - Automatic refetch on focus/reconnect
 */
export const store = configureStore({
  reducer: {
    auth: authReducer,
    environment: environmentReducer,
    settings: settingsReducer,
    websocket: websocketReducer,
    [marketDataApi.reducerPath]: marketDataApi.reducer,
    [authApi.reducerPath]: authApi.reducer,
    [accountApi.reducerPath]: accountApi.reducer,
    [backtestApi.reducerPath]: backtestApi.reducer,
    [riskApi.reducerPath]: riskApi.reducer,
    [exchangeApi.reducerPath]: exchangeApi.reducer,
    [paperApi.reducerPath]: paperApi.reducer,
    [forwardTestingApi.reducerPath]: forwardTestingApi.reducer,
    [strategiesApi.reducerPath]: strategiesApi.reducer,
    [tradesApi.reducerPath]: tradesApi.reducer,
  },
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware({
      // Configure serialization checks for development
      serializableCheck: {
        // Ignore these action types for serialization checks
        ignoredActions: ['persist/PERSIST', 'persist/REHYDRATE'],
      },
    }).concat(
      authApi.middleware,
      accountApi.middleware,
      backtestApi.middleware,
      riskApi.middleware,
      exchangeApi.middleware,
      paperApi.middleware,
      forwardTestingApi.middleware,
      strategiesApi.middleware,
      tradesApi.middleware,
      marketDataApi.middleware,
      websocketMiddleware
    ),
  // Enable Redux DevTools in development only
  devTools: import.meta.env.DEV,
});

// Enable refetchOnFocus and refetchOnReconnect behaviors
setupListeners(store.dispatch);

// Infer types from the store itself
export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
