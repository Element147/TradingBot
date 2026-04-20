import { Button, Chip, Grid, Stack, Typography } from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';

import { BalanceCard } from './BalanceCard';
import { OperatorAuditCard } from './OperatorAuditCard';
import { PaperTradingCard } from './PaperTradingCard';
import { PerformanceCard } from './PerformanceCard';
import { PositionsList } from './PositionsList';
import { RecentTradesList } from './RecentTradesList';
import { SystemHealthIndicator } from './SystemHealthIndicator';

import { useAppSelector } from '@/app/hooks';
import { AppLayout } from '@/components/layout/AppLayout';
import {
  PageContent,
  PageIntro,
  type PageMetricItem,
  PageMetricStrip,
  PageSectionHeader,
} from '@/components/layout/PageContent';
import { StatusPill, SurfacePanel } from '@/components/ui/Workbench';

const DashboardPage: React.FC = () => {
  const isAdmin = useAppSelector((state) => state.auth.user?.role === 'admin');
  const environmentMode = useAppSelector((state) => state.environment.mode);
  const username = useAppSelector((state) => state.auth.user?.username);
  const operatorPosture =
    environmentMode === 'live'
      ? 'Live UI stays explicitly gated by controls.'
      : 'Test and paper remain the default operating posture.';
  const commandTiles: PageMetricItem[] = [
    {
      label: 'Operating Mode',
      value: environmentMode.toUpperCase(),
      detail: operatorPosture,
      tone: environmentMode === 'live' ? 'error' : 'success',
    },
    {
      label: 'Research Path',
      value: 'Backtest -> Paper',
      detail: 'Move from evidence to simulation before any live-facing decision.',
      tone: 'info',
    },
    {
      label: 'Operator',
      value: username || 'Workstation User',
      detail: 'State changes and overrides stay visible and audit-facing.',
      tone: 'warning',
    },
    {
      label: 'Access Posture',
      value: isAdmin ? 'Admin tools available' : 'Trader-safe view',
      detail: isAdmin
        ? 'Audit and system surfaces remain available without crowding the main workflow.'
        : 'Privileged operations stay hidden until the role explicitly allows them.',
      tone: isAdmin ? 'info' : 'default',
    },
  ];

  return (
    <AppLayout>
      <PageContent maxWidth="research">
        <PageIntro
          eyebrow="Start here"
          description="Review workstation health, paper posture, and open research signals before moving into deeper strategy or execution flows."
          actions={
            <>
              <Button component={RouterLink} to="/backtest" variant="contained">
                Open Backtest
              </Button>
              <Button component={RouterLink} to="/paper" variant="outlined">
                Open Paper Desk
              </Button>
            </>
          }
          chips={
            <>
              <Chip label="Mode defaults to test" variant="outlined" />
              <Chip label="Paper stays simulated" variant="outlined" />
              <Chip label="Review risk posture in Risk" variant="outlined" />
            </>
          }
        />

        <PageMetricStrip items={commandTiles} />

        <SurfacePanel
          title="Research Workstation"
          description="Calm, test-first access to research, paper workflows, risk controls, and operator settings."
          tone="info"
          actions={
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              <StatusPill label="Default: test" tone="success" variant="filled" />
              <StatusPill label="Paper stays simulated" tone="info" />
            </Stack>
          }
        >
          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 5 }}>
              <Stack spacing={1}>
                <Typography variant="subtitle2">Safety defaults</Typography>
                <Typography variant="body2" color="text.secondary">
                  Follow the workstation path: Backtest first, Paper second, and only explicit live context when the app says it is supported.
                </Typography>
              </Stack>
            </Grid>
            <Grid size={{ xs: 12, md: 7 }}>
              <Stack spacing={0.75}>
                <Typography variant="subtitle2">Workstation flow</Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  1. Review Dashboard
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  2. Validate in Backtest
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  3. Simulate in Paper
                </Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                  4. Monitor in Live
                </Typography>
              </Stack>
            </Grid>
          </Grid>
        </SurfacePanel>

        <PageSectionHeader
          title="Operator snapshot"
          description="The dashboard now keeps core balance, performance, health, and paper posture in one shared panel language before you drop into denser evidence views."
        />

        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <BalanceCard />
          </Grid>

          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <PerformanceCard />
          </Grid>

          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <SystemHealthIndicator />
          </Grid>

          <Grid size={{ xs: 12, md: 6, xl: 3 }}>
            <PaperTradingCard />
          </Grid>
        </Grid>

        <PageSectionHeader
          title="Evidence and follow-up"
          description="Review audit-sensitive actions, open exposure, and completed trades in calmer list-based panels instead of stacked legacy tables."
        />

        <Grid container spacing={2.5}>
          {isAdmin ? (
            <Grid size={{ xs: 12, lg: 6 }}>
              <OperatorAuditCard />
            </Grid>
          ) : null}

          <Grid size={{ xs: 12 }}>
            <PositionsList />
          </Grid>

          <Grid size={{ xs: 12 }}>
            <RecentTradesList />
          </Grid>
        </Grid>
      </PageContent>
    </AppLayout>
  );
};

export default DashboardPage;
