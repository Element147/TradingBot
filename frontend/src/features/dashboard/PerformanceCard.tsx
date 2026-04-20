import {
  Alert,
  Box,
  ToggleButton,
  ToggleButtonGroup,
  Stack,
  Typography,
} from '@mui/material';
import { useState } from 'react';

import {
  useGetPerformanceQuery,
  type PerformanceTimeframe,
} from '../account/accountApi';

import { MetricCard, NumericText, SurfacePanel } from '@/components/ui/Workbench';
import { getApiErrorMessage } from '@/services/api';
import { formatCompactNumber, formatCurrency, formatPercentage } from '@/utils/formatters';

export const PerformanceCard: React.FC = () => {
  const [timeframe, setTimeframe] = useState<PerformanceTimeframe>('today');
  const { data: performance, isLoading, error } = useGetPerformanceQuery(timeframe);

  const handleTimeframeChange = (
    _: React.MouseEvent<HTMLElement>,
    newTimeframe: PerformanceTimeframe | null
  ) => {
    if (newTimeframe !== null) {
      setTimeframe(newTimeframe);
    }
  };

  const isProfitable = performance && parseFloat(performance.totalProfitLoss) >= 0;
  const profitTone = isProfitable ? 'success' : 'error';

  return (
    <SurfacePanel
      title="Performance"
      description="Switch time windows without losing the top-line return, win rate, and capital posture."
      sx={{ height: '100%' }}
    >
      <ToggleButtonGroup
        value={timeframe}
        exclusive
        onChange={handleTimeframeChange}
        size="small"
        fullWidth
        aria-label="Performance timeframe"
      >
        <ToggleButton
          value="today"
          aria-label="Today"
          title="Current-day performance. Most sensitive to intraday volatility."
        >
          Today
        </ToggleButton>
        <ToggleButton
          value="week"
          aria-label="This week"
          title="Weekly performance window. Useful for short trend validation."
        >
          Week
        </ToggleButton>
        <ToggleButton
          value="month"
          aria-label="This month"
          title="Monthly performance summary. Balances noise and stability."
        >
          Month
        </ToggleButton>
        <ToggleButton
          value="all"
          aria-label="All time"
          title="Full available history. Can hide recent regime changes."
        >
          All
        </ToggleButton>
      </ToggleButtonGroup>

      {error ? (
        <Alert severity="error">
          {getApiErrorMessage(error, 'Failed to load performance data. Please try again.')}
        </Alert>
      ) : isLoading || !performance ? (
        <Typography variant="body2" color="text.secondary">
          Loading performance snapshot...
        </Typography>
      ) : (
        <Box
          sx={{
            display: 'grid',
            gap: 1,
            gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 1fr))' },
          }}
        >
          <MetricCard
            label="Total Profit/Loss"
            value={
              <Stack spacing={0.45}>
                <NumericText variant="h5" tone={profitTone}>
                  {formatCurrency(performance.totalProfitLoss)}
                </NumericText>
                <NumericText variant="body2" tone={profitTone}>
                  {`${isProfitable ? '+' : ''}${formatPercentage(performance.profitLossPercentage)}`}
                </NumericText>
              </Stack>
            }
            detail="Top-line return for the selected review window."
            tone={profitTone}
            kicker={timeframe}
            sx={{ gridColumn: { xs: 'span 1', md: 'span 2' } }}
          />
          <MetricCard
            label="Win Rate"
            value={formatPercentage(performance.winRate)}
            detail="Share of completed trades that closed positive."
            tone="success"
          />
          <MetricCard
            label="Trade Count"
            value={performance.tradeCount}
            detail="Recorded trades in the selected review window."
            tone="info"
          />
          <MetricCard
            label="Cash to Invested Capital Ratio"
            value={formatCompactNumber(performance.cashRatio, 2, 2)}
            detail="Higher values leave more dry powder for controlled follow-up."
            tone="warning"
            sx={{ gridColumn: { xs: 'span 1', md: 'span 2' } }}
          />
        </Box>
      )}
    </SurfacePanel>
  );
};
