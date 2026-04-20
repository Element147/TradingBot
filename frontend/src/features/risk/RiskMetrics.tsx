import {
  Alert,
  Box,
  LinearProgress,
  Stack,
  Typography,
} from '@mui/material';

import type { RiskStatus } from './riskApi';

import { StatusPill, SurfacePanel } from '@/components/ui/Workbench';

interface RiskMetricsProps {
  status: RiskStatus | undefined;
  loading: boolean;
}

const colorForRatio = (ratio: number): 'success' | 'warning' | 'error' => {
  if (ratio >= 0.9) {
    return 'error';
  }
  if (ratio >= 0.7) {
    return 'warning';
  }
  return 'success';
};

export function RiskMetrics({ status, loading }: RiskMetricsProps) {
  const drawdownRatio = status
    ? status.currentDrawdown / Math.max(status.maxDrawdownLimit, 0.0001)
    : 0;
  const dailyLossRatio = status
    ? status.dailyLoss / Math.max(status.dailyLossLimit, 0.0001)
    : 0;

  return (
    <SurfacePanel
      title="Breaker posture"
      description="This block stays dominant because every other risk action depends on whether the system is already near or beyond configured limits."
      tone={status?.circuitBreakerActive ? 'error' : 'success'}
      elevated
      actions={
        <StatusPill
          label={status?.circuitBreakerActive ? 'Breaker active' : 'Breaker clear'}
          tone={status?.circuitBreakerActive ? 'error' : 'success'}
          variant="filled"
        />
      }
    >
      {loading || !status ? (
        <Typography>Loading risk status...</Typography>
      ) : (
        <Stack spacing={2}>
          <Box>
            <Typography variant="body2">
              Current drawdown: {status.currentDrawdown.toFixed(2)}% /{' '}
              {status.maxDrawdownLimit.toFixed(2)}%
            </Typography>
            <LinearProgress
              color={colorForRatio(drawdownRatio)}
              variant="determinate"
              value={Math.min(100, drawdownRatio * 100)}
            />
          </Box>
          <Box>
            <Typography variant="body2">
              Daily loss: {status.dailyLoss.toFixed(2)}% / {status.dailyLossLimit.toFixed(2)}%
            </Typography>
            <LinearProgress
              color={colorForRatio(dailyLossRatio)}
              variant="determinate"
              value={Math.min(100, dailyLossRatio * 100)}
            />
          </Box>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <StatusPill label={`Open exposure ${status.openRiskExposure.toFixed(2)}%`} tone="info" />
            <StatusPill
              label={`Correlation ${status.positionCorrelation.toFixed(2)}%`}
              tone={status.positionCorrelation >= 75 ? 'warning' : 'default'}
            />
          </Stack>
          {status.circuitBreakerActive ? (
            <Alert severity="error">
              Circuit breaker active: {status.circuitBreakerReason || 'No reason provided'}
            </Alert>
          ) : (
            <Alert severity="success">Circuit breaker not active.</Alert>
          )}
        </Stack>
      )}
    </SurfacePanel>
  );
}
