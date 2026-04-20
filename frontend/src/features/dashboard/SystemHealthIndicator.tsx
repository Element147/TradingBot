import {
  Box,
  Stack,
  Typography,
} from '@mui/material';
import React from 'react';
import { useSelector } from 'react-redux';

import {
  selectConnectionError,
  selectIsConnected,
  selectIsConnecting,
  selectLastEventTime,
  selectReconnectAttempts,
} from '../websocket/websocketSlice';

import {
  StatusPill,
  SurfacePanel,
} from '@/components/ui/Workbench';
import { DEV_AUTH_BYPASS_ENABLED } from '@/features/auth/devAuth';
import { useGetRiskStatusQuery } from '@/features/risk/riskApi';
import { useGetSystemInfoQuery } from '@/features/settings/exchangeApi';
import { getApiErrorMessage } from '@/services/api';
import { formatDistanceToNow } from '@/utils/formatters';

type HealthTone = 'success' | 'warning' | 'error' | 'default';

const resolveDependencyTone = (status: string): HealthTone => {
  const normalized = status.trim().toUpperCase();

  if (normalized === 'UP') {
    return 'success';
  }

  if (normalized.startsWith('DOWN')) {
    return 'error';
  }

  return 'warning';
};

export const SystemHealthIndicator: React.FC = () => {
  const wsConnected = useSelector(selectIsConnected);
  const wsConnecting = useSelector(selectIsConnecting);
  const wsError = useSelector(selectConnectionError);
  const reconnectAttempts = useSelector(selectReconnectAttempts);
  const lastEventTime = useSelector(selectLastEventTime);

  const {
    data: systemInfo,
    isError: systemInfoError,
    isLoading: systemInfoLoading,
    error: systemInfoQueryError,
  } = useGetSystemInfoQuery(undefined, {
    pollingInterval: 60000,
    skipPollingIfUnfocused: true,
  });
  const {
    data: riskStatus,
    isError: riskStatusError,
    isLoading: riskStatusLoading,
    error: riskStatusQueryError,
  } = useGetRiskStatusQuery(undefined, {
    pollingInterval: 30000,
    skipPollingIfUnfocused: true,
  });

  const apiStatus = systemInfoLoading
    ? 'Connecting'
    : systemInfoError
      ? 'Disconnected'
      : 'Connected';
  const usingPollingFallback =
    DEV_AUTH_BYPASS_ENABLED && !wsConnected && !wsConnecting && !wsError;
  const wsStatus = wsConnected
    ? 'Connected'
    : wsConnecting
      ? 'Connecting'
      : usingPollingFallback
        ? 'Polling fallback'
      : wsError
        ? 'Error'
        : 'Disconnected';
  const lastUpdateText = lastEventTime
    ? formatDistanceToNow(new Date(lastEventTime))
    : 'Never';
  const circuitBreakerStatus = riskStatusLoading
    ? 'Checking'
    : riskStatusError
      ? 'Unknown'
      : riskStatus?.circuitBreakerActive
        ? 'Active'
        : 'Inactive';

  return (
    <SurfacePanel
      title="System Health"
      description="Backend, transport, and breaker posture in one calmer shell-friendly review block."
      sx={{ height: '100%' }}
    >
      <Stack spacing={1.1}>
        <Stack
          direction="row"
          justifyContent="space-between"
          spacing={1.25}
          alignItems="center"
        >
          <Typography variant="body2" color="text.secondary">
            Backend API:
          </Typography>
          <StatusPill
            label={apiStatus}
            tone={
              apiStatus === 'Connected'
                ? 'success'
                : apiStatus === 'Disconnected'
                  ? 'error'
                  : 'warning'
            }
            variant="filled"
          />
        </Stack>

        <Stack
          direction="row"
          justifyContent="space-between"
          spacing={1.25}
          alignItems="center"
        >
          <Typography variant="body2" color="text.secondary">
            Database:
          </Typography>
          <StatusPill
            label={systemInfoLoading ? 'Checking' : systemInfo?.databaseStatus ?? 'Unknown'}
            tone={
              systemInfoLoading
                ? 'warning'
                : systemInfo?.databaseStatus
                  ? resolveDependencyTone(systemInfo.databaseStatus)
                  : 'warning'
            }
            variant="filled"
          />
        </Stack>

        <Stack
          direction="row"
          justifyContent="space-between"
          spacing={1.25}
          alignItems="center"
        >
          <Typography variant="body2" color="text.secondary">
            WebSocket:
          </Typography>
          <StatusPill
            label={wsStatus}
            tone={
              wsStatus === 'Connected'
                ? 'success'
                : wsStatus === 'Polling fallback'
                  ? 'warning'
                : wsStatus === 'Error'
                  ? 'error'
                  : 'warning'
            }
            variant="filled"
          />
        </Stack>

        <Stack
          direction="row"
          justifyContent="space-between"
          spacing={1.25}
          alignItems="center"
        >
          <Typography variant="body2" color="text.secondary">
            Last Update:
          </Typography>
          <StatusPill label={lastUpdateText} tone="info" />
        </Stack>

        <Stack
          direction="row"
          justifyContent="space-between"
          spacing={1.25}
          alignItems="center"
        >
          <Typography variant="body2" color="text.secondary">
            Circuit Breaker:
          </Typography>
          <StatusPill
            label={circuitBreakerStatus}
            tone={
              circuitBreakerStatus === 'Inactive'
                ? 'success'
                : circuitBreakerStatus === 'Active'
                  ? 'error'
                  : 'warning'
            }
            variant="filled"
          />
        </Stack>
      </Stack>

      <Box
        sx={{
          pt: 1.25,
          borderTop: '1px solid',
          borderColor: 'divider',
        }}
      >
        <Typography variant="caption" color="text.secondary" display="block">
          {systemInfoError
            ? getApiErrorMessage(systemInfoQueryError, 'Backend API is disconnected')
            : usingPollingFallback
              ? 'System info endpoint is responding. Local debug auth bypass keeps telemetry on polling fallback until a real session is used.'
              : 'System info endpoint is responding and transport state is available for review.'}
        </Typography>
        {reconnectAttempts > 0 ? (
          <Typography variant="caption" color="text.secondary" display="block">
            Reconnection attempts: {reconnectAttempts}
          </Typography>
        ) : null}
        {wsError ? (
          <Typography variant="caption" color="text.secondary" display="block">
            WebSocket detail: {wsError}
          </Typography>
        ) : null}
        {riskStatusError ? (
          <Typography variant="caption" color="text.secondary" display="block">
            {getApiErrorMessage(riskStatusQueryError, 'Circuit breaker status is unknown')}
          </Typography>
        ) : riskStatus?.circuitBreakerReason ? (
          <Typography variant="caption" color="text.secondary" display="block">
            {riskStatus.circuitBreakerReason}
          </Typography>
        ) : null}
      </Box>
    </SurfacePanel>
  );
};
