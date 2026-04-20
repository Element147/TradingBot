import { configureStore, PreloadedState } from '@reduxjs/toolkit';
import { render, RenderOptions } from '@testing-library/react';
import { ReactElement } from 'react';
import { Provider } from 'react-redux';
import { BrowserRouter } from 'react-router-dom';

import type { RootState } from '@/app/store';
import { accountApi } from '@/features/account/accountApi';
import { authApi } from '@/features/auth/authApi';
import authReducer from '@/features/auth/authSlice';
import environmentReducer from '@/features/environment/environmentSlice';
import settingsReducer from '@/features/settings/settingsSlice';
import { tradesApi } from '@/features/trades/tradesApi';
import websocketReducer from '@/features/websocket/websocketSlice';

/**
 * Custom render function that wraps components with Redux Provider and Router
 * 
 * Usage:
 * ```tsx
 * renderWithProviders(<MyComponent />, {
 *   preloadedState: {
 *     auth: { token: 'test-token', isAuthenticated: true }
 *   }
 * });
 * ```
 */
interface ExtendedRenderOptions extends Omit<RenderOptions, 'queries'> {
  preloadedState?: PreloadedState<RootState>;
  store?: ReturnType<typeof setupStore>;
}

export function setupStore(preloadedState?: PreloadedState<RootState>) {
  return configureStore({
    reducer: {
      auth: authReducer,
      environment: environmentReducer,
      settings: settingsReducer,
      websocket: websocketReducer,
      [authApi.reducerPath]: authApi.reducer,
      [accountApi.reducerPath]: accountApi.reducer,
      [tradesApi.reducerPath]: tradesApi.reducer,
    },
    middleware: (getDefaultMiddleware) =>
      getDefaultMiddleware().concat(authApi.middleware, accountApi.middleware, tradesApi.middleware),
    preloadedState,
  });
}

export function renderWithProviders(
  ui: ReactElement,
  {
    preloadedState = {},
    store = setupStore(preloadedState),
    ...renderOptions
  }: ExtendedRenderOptions = {}
) {
  function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <Provider store={store}>
        <BrowserRouter>{children}</BrowserRouter>
      </Provider>
    );
  }

  return { store, ...render(ui, { wrapper: Wrapper, ...renderOptions }) };
}

// Re-export everything from React Testing Library
export * from '@testing-library/react';
