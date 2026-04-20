import { Alert, Stack, Typography } from '@mui/material';
import React from 'react';

import { NumericText, StatusPill, SurfacePanel } from '@/components/ui/Workbench';
import { useGetOpenPositionsQuery } from '@/features/account/accountApi';
import { getApiErrorMessage } from '@/services/api';
import { formatCompactNumber, formatCurrency, formatPercentage } from '@/utils/formatters';

export const PositionsList: React.FC = () => {
  const { data: positions, isLoading, error } = useGetOpenPositionsQuery();

  return (
    <SurfacePanel
      title="Open Positions"
      description="Current exposure stays readable as review rows instead of another dense dashboard table."
      actions={
        <StatusPill
          label={`${positions?.length || 0} Position${positions?.length === 1 ? '' : 's'}`}
          tone="info"
          variant="filled"
        />
      }
    >
      {isLoading ? (
        <Typography variant="body2" color="text.secondary">
          Loading positions...
        </Typography>
      ) : error ? (
        <Alert severity="error">
          {getApiErrorMessage(error, 'Failed to load positions. Please try again.')}
        </Alert>
      ) : !positions || positions.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No open positions.
        </Typography>
      ) : (
        <Stack spacing={1.1}>
          {positions.map((position) => {
            const pnlValue = parseFloat(position.unrealizedPnL);
            const pnlTone = pnlValue >= 0 ? 'success' : 'error';
            const pnlSign = pnlValue >= 0 ? '+' : '';

            return (
              <Stack
                key={position.id}
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
                    <Typography variant="subtitle2">{position.symbol}</Typography>
                    <StatusPill
                      label={position.side}
                      tone={position.side === 'LONG' ? 'success' : 'warning'}
                      variant="filled"
                    />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    {position.strategyName}
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
                    <NumericText variant="body2">{formatCurrency(position.entryPrice)}</NumericText>
                  </Stack>
                  <Stack spacing={0.35}>
                    <Typography variant="caption" color="text.secondary">
                      Current
                    </Typography>
                    <NumericText variant="body2">
                      {formatCurrency(position.currentPrice)}
                    </NumericText>
                  </Stack>
                  <Stack spacing={0.35}>
                    <Typography variant="caption" color="text.secondary">
                      Quantity
                    </Typography>
                    <NumericText variant="body2">
                      {formatCompactNumber(position.quantity, 4)}
                    </NumericText>
                  </Stack>
                  <Stack spacing={0.35} sx={{ minWidth: 120 }}>
                    <Typography variant="caption" color="text.secondary">
                      Unrealized P&amp;L
                    </Typography>
                    <NumericText variant="body2" tone={pnlTone}>
                      {`${pnlSign}${formatCurrency(position.unrealizedPnL)}`}
                    </NumericText>
                    <Typography variant="caption" color={`${pnlTone}.main`}>
                      {`(${pnlSign}${formatPercentage(position.unrealizedPnLPercentage)})`}
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
