import { Alert, Stack, Typography } from '@mui/material';
import React from 'react';

import { NumericText, StatusPill, SurfacePanel } from '@/components/ui/Workbench';
import { useGetRecentTradesQuery } from '@/features/account/accountApi';
import { getApiErrorMessage } from '@/services/api';
import {
  formatCurrency,
  formatDateTime,
  formatPercentage,
} from '@/utils/formatters';

export const RecentTradesList: React.FC = () => {
  const { data: trades, isLoading, error } = useGetRecentTradesQuery(10);

  return (
    <SurfacePanel
      title="Recent Trades"
      description="Completed trade review stays compact and scan-friendly instead of leaning on a full-width results table."
      actions={
        <StatusPill
          label={`Last ${trades?.length || 0}`}
          tone="info"
          variant="filled"
        />
      }
    >
      {isLoading ? (
        <Typography variant="body2" color="text.secondary">
          Loading recent trades...
        </Typography>
      ) : error ? (
        <Alert severity="error">
          {getApiErrorMessage(error, 'Failed to load trades. Please try again.')}
        </Alert>
      ) : !trades || trades.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No recent trades.
        </Typography>
      ) : (
        <Stack spacing={1.1}>
          {trades.map((trade) => {
            const pnlValue = parseFloat(trade.profitLoss);
            const pnlTone = pnlValue >= 0 ? 'success' : 'error';
            const pnlSign = pnlValue >= 0 ? '+' : '';

            return (
              <Stack
                key={trade.id}
                direction={{ xs: 'column', lg: 'row' }}
                spacing={1.25}
                justifyContent="space-between"
                sx={{
                  pt: 1.25,
                  borderTop: '1px solid',
                  borderColor: 'divider',
                }}
              >
                <Stack spacing={0.4} sx={{ minWidth: 0, flex: 1 }}>
                  <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                    <Typography variant="subtitle2">{trade.symbol}</Typography>
                    <StatusPill
                      label={trade.side}
                      tone={trade.side === 'LONG' ? 'success' : 'warning'}
                      variant="filled"
                    />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    {trade.strategyName}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {formatDateTime(trade.exitTime)}
                  </Typography>
                </Stack>

                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  spacing={2}
                  flexWrap="wrap"
                  useFlexGap
                  sx={{ minWidth: 0 }}
                >
                  <Stack spacing={0.35}>
                    <Typography variant="caption" color="text.secondary">
                      Entry
                    </Typography>
                    <NumericText variant="body2">{formatCurrency(trade.entryPrice)}</NumericText>
                  </Stack>
                  <Stack spacing={0.35}>
                    <Typography variant="caption" color="text.secondary">
                      Exit
                    </Typography>
                    <NumericText variant="body2">{formatCurrency(trade.exitPrice)}</NumericText>
                  </Stack>
                  <Stack spacing={0.35} sx={{ minWidth: 120 }}>
                    <Typography variant="caption" color="text.secondary">
                      P&amp;L
                    </Typography>
                    <NumericText variant="body2" tone={pnlTone}>
                      {`${pnlSign}${formatCurrency(trade.profitLoss)}`}
                    </NumericText>
                    <Typography variant="caption" color={`${pnlTone}.main`}>
                      {`(${pnlSign}${formatPercentage(trade.profitLossPercentage)})`}
                    </Typography>
                  </Stack>
                </Stack>
              </Stack>
            );
          })}
        </Stack>
      )}
    </SurfacePanel>
  );
};
