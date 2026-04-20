import { Button, Stack, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';

import {
  MetricCard,
  StatusPill,
  SurfacePanel,
} from '@/components/ui/Workbench';
import { useGetPaperTradingStateQuery } from '@/features/paperApi';
import { formatCurrency } from '@/utils/formatters';

export const PaperTradingCard: React.FC = () => {
  const { data, isLoading, isError } = useGetPaperTradingStateQuery();
  const followUpMessage = data
    ? data.alerts.find((alert) => !data.recoveryMessage.includes(alert.summary))?.summary ??
      data.incidentSummary
    : null;

  return (
    <SurfacePanel
      title="Paper Trading"
      description="Keep desk recovery, order counts, and paper posture visible before opening the simulated order desk."
      actions={
        <Button component={RouterLink} to="/paper" size="small" variant="outlined">
          Open Paper Desk
        </Button>
      }
      sx={{ height: '100%' }}
    >
      {isLoading ? (
        <Typography variant="body2" color="text.secondary">
          Loading paper state...
        </Typography>
      ) : isError || !data ? (
        <Typography variant="body2" color="error.main">
          Unable to load paper-trading state.
        </Typography>
      ) : (
        <Stack spacing={1.25}>
          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
            <StatusPill
              label={data.paperMode ? 'Paper mode active' : 'Paper mode inactive'}
              tone={data.paperMode ? 'success' : 'warning'}
              variant="filled"
            />
            <StatusPill label={data.recoveryStatus} tone="info" />
          </Stack>

          <Typography variant="body2" color="text.secondary">
            {data.recoveryMessage}
          </Typography>
          {followUpMessage && followUpMessage !== data.recoveryMessage ? (
            <Typography variant="caption" color="text.secondary">
              {followUpMessage}
            </Typography>
          ) : null}

          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            spacing={1}
            sx={{ '& > *': { flex: 1 } }}
          >
            <MetricCard
              label="Cash Balance"
              value={formatCurrency(data.cashBalance)}
              detail={`${data.positionCount} open position(s)`}
              tone="success"
            />
            <MetricCard
              label="Orders"
              value={`${data.openOrders} open`}
              detail={`${data.totalOrders} total | ${data.filledOrders} filled`}
              tone="info"
            />
          </Stack>

          {data.alerts[0]?.recommendedAction ? (
            <Typography variant="caption" color="text.secondary">
              {data.alerts[0].recommendedAction}
            </Typography>
          ) : null}
        </Stack>
      )}
    </SurfacePanel>
  );
};
