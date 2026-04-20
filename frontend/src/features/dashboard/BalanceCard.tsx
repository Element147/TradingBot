import RefreshIcon from '@mui/icons-material/Refresh';
import {
  Alert,
  Box,
  IconButton,
  Stack,
  Typography,
} from '@mui/material';

import { useGetBalanceQuery } from '../account/accountApi';

import { MetricCard, NumericText, SurfacePanel } from '@/components/ui/Workbench';
import { getApiErrorMessage } from '@/services/api';
import { formatCompactNumber, formatCurrency, formatDateTime } from '@/utils/formatters';

export const BalanceCard: React.FC = () => {
  const { data: balance, isLoading, error, refetch } = useGetBalanceQuery();

  if (error) {
    return (
      <SurfacePanel title="Account Balance" description="Portfolio totals and asset mix.">
        <Alert severity="error">
          {getApiErrorMessage(error, 'Failed to load balance data. Please try again.')}
        </Alert>
      </SurfacePanel>
    );
  }

  return (
    <SurfacePanel
      title="Account Balance"
      description="Keep total, available, and locked capital visible before opening a denser account view."
      actions={
        <IconButton
          onClick={() => void refetch()}
          size="small"
          aria-label="Refresh balance"
          title="Refresh balance"
        >
          <RefreshIcon />
        </IconButton>
      }
      sx={{ height: '100%' }}
    >
      {isLoading || !balance ? (
        <Typography variant="body2" color="text.secondary">
          Loading balance posture...
        </Typography>
      ) : (
        <Stack spacing={1.5}>
          <Box
            sx={{
              display: 'grid',
              gap: 1,
              gridTemplateColumns: { xs: '1fr', md: 'repeat(3, minmax(0, 1fr))' },
            }}
          >
            <MetricCard
              label="Total Balance"
              value={formatCurrency(balance.total)}
              detail="Combined workstation balance across tracked assets."
              tone="info"
            />
            <MetricCard
              label="Available"
              value={formatCurrency(balance.available)}
              detail="Free capital available for paper or test allocations."
              tone="success"
            />
            <MetricCard
              label="Locked"
              value={formatCurrency(balance.locked)}
              detail="Capital reserved in open orders or current position state."
              tone="warning"
            />
          </Box>

          <Stack spacing={1}>
            <Typography variant="subtitle2">Asset Breakdown</Typography>
            {balance.assets.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                No assets reported yet.
              </Typography>
            ) : (
              balance.assets.map((asset) => (
                <Stack
                  key={asset.symbol}
                  direction="row"
                  justifyContent="space-between"
                  spacing={1.5}
                  alignItems="center"
                  sx={{
                    pt: 1,
                    borderTop: '1px solid',
                    borderColor: 'divider',
                  }}
                >
                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>
                      {asset.symbol}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Position amount
                    </Typography>
                  </Box>
                  <Stack alignItems="flex-end" sx={{ minWidth: 0 }}>
                    <NumericText variant="body2">
                      {formatCompactNumber(asset.amount, 4)}
                    </NumericText>
                    <Typography variant="caption" color="text.secondary">
                      {formatCurrency(asset.valueUSD)}
                    </Typography>
                  </Stack>
                </Stack>
              ))
            )}
          </Stack>

          <Typography variant="caption" color="text.secondary">
            Last updated: {formatDateTime(balance.lastSync)}
          </Typography>
        </Stack>
      )}
    </SurfacePanel>
  );
};
