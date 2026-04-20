import { ThemeProvider, CssBaseline, Box } from '@mui/material';
import { lazy, Suspense, useEffect, useMemo } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from './app/hooks';
import { prefetchAuthenticatedWorkstationData } from './app/prefetchAuthenticatedWorkstationData';
import ErrorBoundary from './components/ErrorBoundary';
import LoadingFallback from './components/LoadingFallback';
import ProtectedRoute from './components/ProtectedRoute';
import { DEV_AUTH_BYPASS_ENABLED } from './features/auth/devAuth';
import { selectTextScale, selectTheme } from './features/settings/settingsSlice';
import { WebSocketRuntime } from './features/websocket/WebSocketRuntime';
import { lightTheme, darkTheme } from './theme/theme';
import './App.css';

// Lazy load page components for code splitting and performance
const LoginPage = lazy(() => import('./features/auth/LoginPage'));
const DashboardPage = lazy(() => import('./features/dashboard/DashboardPage'));
const ForwardTestingPage = lazy(() => import('./features/forwardTesting/ForwardTestingPage'));
const PaperTradingPage = lazy(() => import('./features/paper/PaperTradingPage'));
const LiveTradingPage = lazy(() => import('./features/live/LiveTradingPage'));
const StrategiesPage = lazy(() => import('./features/strategies/StrategiesPage'));
const TradesPage = lazy(() => import('./features/trades/TradesPage'));
const BacktestPage = lazy(() => import('./features/backtest/BacktestPage'));
const MarketDataPage = lazy(() => import('./features/marketData/MarketDataPage'));
const RiskPage = lazy(() => import('./features/risk/RiskPage'));
const SettingsPage = lazy(() => import('./features/settings/SettingsPage'));

function App() {
  // Get current theme from Redux state
  const dispatch = useAppDispatch();
  const isAuthenticated = useAppSelector((state) => state.auth.isAuthenticated);
  const authToken = useAppSelector((state) => state.auth.token);
  const themeMode = useAppSelector(selectTheme);
  const textScale = useAppSelector(selectTextScale);

  // Memoize theme to avoid unnecessary recalculations
  const theme = useMemo(
    () => (themeMode === 'light' ? lightTheme : darkTheme),
    [themeMode]
  );
  const enableWebSocketRuntime =
    import.meta.env.MODE !== 'test' &&
    (!DEV_AUTH_BYPASS_ENABLED || Boolean(authToken));

  useEffect(() => {
    if (import.meta.env.MODE === 'test' || !isAuthenticated) {
      return;
    }
    prefetchAuthenticatedWorkstationData(dispatch);
  }, [dispatch, isAuthenticated]);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {/* App-level error boundary catches all errors */}
      <ErrorBoundary>
        <BrowserRouter>
          <Box sx={{ fontSize: `${textScale}rem`, minHeight: '100vh' }}>
            {enableWebSocketRuntime ? <WebSocketRuntime /> : null}
            <Suspense fallback={<LoadingFallback />}>
              <Routes>
              {/* Public route - Login */}
              <Route
                path="/login"
                element={
                  <ErrorBoundary>
                    {DEV_AUTH_BYPASS_ENABLED ? (
                      <Navigate to="/dashboard" replace />
                    ) : (
                      <LoginPage />
                    )}
                  </ErrorBoundary>
                }
              />

              {/* Protected routes - All require authentication */}
              <Route
                path="/dashboard"
                element={
                  <ErrorBoundary>
                    <ProtectedRoute>
                      <DashboardPage />
                    </ProtectedRoute>
                  </ErrorBoundary>
                }
              />
              <Route
                path="/forward-testing"
                element={
                  <ErrorBoundary>
                    <ProtectedRoute>
                      <ForwardTestingPage />
                    </ProtectedRoute>
                  </ErrorBoundary>
                }
              />
              <Route
                path="/paper"
                element={
                  <ErrorBoundary>
                    <ProtectedRoute>
                      <PaperTradingPage />
                    </ProtectedRoute>
                  </ErrorBoundary>
                }
              />
              <Route
                path="/live"
                element={
                  <ErrorBoundary>
                    <ProtectedRoute>
                      <LiveTradingPage />
                    </ProtectedRoute>
                  </ErrorBoundary>
                }
              />
              <Route
                path="/strategies"
                element={
                  <ErrorBoundary>
                    <ProtectedRoute>
                      <StrategiesPage />
                    </ProtectedRoute>
                  </ErrorBoundary>
                }
              />
              <Route
                path="/trades"
                element={
                  <ErrorBoundary>
                    <ProtectedRoute>
                      <TradesPage />
                    </ProtectedRoute>
                  </ErrorBoundary>
                }
              />
              <Route
                path="/backtest"
                element={
                  <ErrorBoundary>
                    <ProtectedRoute>
                      <BacktestPage />
                    </ProtectedRoute>
                  </ErrorBoundary>
                }
              />
              <Route
                path="/market-data"
                element={
                  <ErrorBoundary>
                    <ProtectedRoute>
                      <MarketDataPage />
                    </ProtectedRoute>
                  </ErrorBoundary>
                }
              />
              <Route
                path="/risk"
                element={
                  <ErrorBoundary>
                    <ProtectedRoute>
                      <RiskPage />
                    </ProtectedRoute>
                  </ErrorBoundary>
                }
              />
              <Route
                path="/settings"
                element={
                  <ErrorBoundary>
                    <ProtectedRoute>
                      <SettingsPage />
                    </ProtectedRoute>
                  </ErrorBoundary>
                }
              />

              {/* Default and catch-all routes */}
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
              </Routes>
            </Suspense>
          </Box>
        </BrowserRouter>
      </ErrorBoundary>
    </ThemeProvider>
  );
}

export default App;
