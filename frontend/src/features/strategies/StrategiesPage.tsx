import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Grid,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';

import {
  PAPER_STRATEGIES_QUERY,
  type Strategy,
  useGetStrategiesQuery,
  useStartStrategyMutation,
  useStopStrategyMutation,
  useUpdateStrategyConfigMutation,
} from './strategiesApi';
import { StrategyConfigModal } from './StrategyConfigModal';
import { getAllStrategyProfiles, getStrategyProfile } from './strategyProfiles';
import type { StrategyConfigOutput } from './strategyValidation';

import { AppLayout } from '@/components/layout/AppLayout';
import {
  PageContent,
  PageIntro,
  type PageMetricItem,
  PageMetricStrip,
  PageSectionHeader,
} from '@/components/layout/PageContent';
import {
  EmptyState,
  NumericText,
  StatusPill,
  SurfacePanel,
} from '@/components/ui/Workbench';
import { getApiErrorMessage } from '@/services/api';
import { formatDateTime } from '@/utils/formatters';

type ActionDialogState = {
  strategy: Strategy;
  action: 'start' | 'stop';
} | null;

const statusTone = (status: Strategy['status']) => {
  if (status === 'RUNNING') {
    return 'success';
  }
  if (status === 'ERROR') {
    return 'error';
  }
  return 'warning';
};

type StrategyGroupKey =
  | 'RUNNING'
  | 'PAPER_MONITOR_CANDIDATE'
  | 'RESEARCH_ONLY'
  | 'ARCHIVE_CANDIDATE'
  | 'BASELINE_ONLY'
  | 'OTHER';

const strategyGroupMeta: Record<
  StrategyGroupKey,
  {
    title: string;
    description: string;
    tone: 'success' | 'info' | 'warning' | 'error';
    defaultExpanded: boolean;
  }
> = {
  RUNNING: {
    title: 'Running now',
    description: 'Any active saved strategy stays surfaced first, regardless of audit disposition.',
    tone: 'success',
    defaultExpanded: true,
  },
  PAPER_MONITOR_CANDIDATE: {
    title: 'Paper-monitor candidates',
    description: 'Saved configs closest to cautious shadow-paper follow-up stay open by default.',
    tone: 'success',
    defaultExpanded: true,
  },
  RESEARCH_ONLY: {
    title: 'Research-only configs',
    description: 'These remain visible, but they still belong in deeper research rather than operator-first follow-up.',
    tone: 'warning',
    defaultExpanded: true,
  },
  ARCHIVE_CANDIDATE: {
    title: 'Archive candidates',
    description: 'Historical comparison configs stay tucked away by default so they do not dominate the operator lane.',
    tone: 'error',
    defaultExpanded: false,
  },
  BASELINE_ONLY: {
    title: 'Baseline-only configs',
    description: 'Benchmarks remain available for comparison but stay collapsed unless you need them.',
    tone: 'info',
    defaultExpanded: false,
  },
  OTHER: {
    title: 'Other configs',
    description: 'Fallback bucket for saved configs without a mapped audit disposition.',
    tone: 'info',
    defaultExpanded: false,
  },
};

const defaultExpandedGroups = Object.entries(strategyGroupMeta)
  .filter(([, meta]) => meta.defaultExpanded)
  .map(([group]) => group as StrategyGroupKey);

const formatCountLabel = (count: number, singular: string, plural = `${singular}s`) =>
  `${count} ${count === 1 ? singular : plural}`;

export default function StrategiesPage() {
  const {
    data: strategies = [],
    isLoading,
    isError,
    error,
  } = useGetStrategiesQuery(PAPER_STRATEGIES_QUERY, {
    pollingInterval: 30000,
    skipPollingIfUnfocused: true,
  });
  const [startStrategy, { isLoading: isStarting }] = useStartStrategyMutation();
  const [stopStrategy, { isLoading: isStopping }] = useStopStrategyMutation();
  const [updateConfig, { isLoading: isSavingConfig }] =
    useUpdateStrategyConfigMutation();

  const [selectedStrategy, setSelectedStrategy] = useState<Strategy | null>(null);
  const [actionDialog, setActionDialog] = useState<ActionDialogState>(null);
  const [feedback, setFeedback] = useState<{
    severity: 'success' | 'error';
    message: string;
  } | null>(null);
  const [contentTab, setContentTab] = useState<'configs' | 'guide'>('configs');
  const [expandedGroups, setExpandedGroups] = useState<StrategyGroupKey[]>(defaultExpandedGroups);

  const isBusy = isStarting || isStopping || isSavingConfig;
  const strategyErrorMessage = useMemo(() => {
    const message = getApiErrorMessage(
      error,
      'Unable to load saved paper strategies right now.'
    );

    return message === 'FETCH_ERROR'
      ? 'Unable to reach the strategy service. Verify the backend is running, then refresh the page.'
      : message;
  }, [error]);
  const strategyProfiles = getAllStrategyProfiles();
  const groupedStrategies = useMemo(() => {
    const groups: Record<StrategyGroupKey, Array<{ strategy: Strategy; profile: ReturnType<typeof getStrategyProfile> }>> = {
      RUNNING: [],
      PAPER_MONITOR_CANDIDATE: [],
      RESEARCH_ONLY: [],
      ARCHIVE_CANDIDATE: [],
      BASELINE_ONLY: [],
      OTHER: [],
    };

    strategies.forEach((strategy) => {
      const profile = getStrategyProfile(strategy.type);
      const groupKey: StrategyGroupKey =
        strategy.status === 'RUNNING'
          ? 'RUNNING'
          : (profile?.auditDisposition as StrategyGroupKey | undefined) ?? 'OTHER';

      groups[groupKey].push({ strategy, profile });
    });

    return (Object.keys(strategyGroupMeta) as StrategyGroupKey[])
      .map((groupKey) => ({
        key: groupKey,
        meta: strategyGroupMeta[groupKey],
        items: groups[groupKey],
      }))
      .filter((group) => group.items.length > 0);
  }, [strategies]);
  const summaryItems = useMemo<PageMetricItem[]>(() => {
    const runningCount = strategies.filter((strategy) => strategy.status === 'RUNNING').length;
    const longOnlyCount = strategies.filter((strategy) => !strategy.shortSellingEnabled).length;
    const paperMonitorCandidateCount = strategyProfiles.filter(
      (profile) => profile.auditDisposition === 'PAPER_MONITOR_CANDIDATE'
    ).length;
    const archiveCandidateCount = strategyProfiles.filter(
      (profile) => profile.auditDisposition === 'ARCHIVE_CANDIDATE'
    ).length;

    return [
      {
        label: 'Catalog Coverage',
        value: `${strategyProfiles.length} templates`,
        detail: 'Canonical strategy profiles stay aligned with the backtest catalog.',
        tone: 'info',
      },
      {
        label: 'Running Now',
        value: formatCountLabel(runningCount, 'active'),
        detail: 'Running strategies stay in paper mode only.',
        tone: runningCount > 0 ? 'success' : 'default',
      },
      {
        label: 'Long-Only Default',
        value: formatCountLabel(longOnlyCount, 'config'),
        detail: 'Short exposure remains opt-in per saved strategy config.',
        tone: 'success',
      },
      {
        label: 'Shadow Paper',
        value: formatCountLabel(paperMonitorCandidateCount, 'candidate'),
        detail: 'Only audited paper-monitor candidates should be treated as near-term follow-up paths.',
        tone: paperMonitorCandidateCount > 0 ? 'success' : 'default',
      },
      {
        label: 'Archive Queue',
        value: formatCountLabel(archiveCandidateCount, 'config'),
        detail: 'Archive candidates stay visible for comparison, not because they are equally ready.',
        tone: archiveCandidateCount > 0 ? 'error' : 'default',
      },
    ];
  }, [strategies, strategyProfiles]);

  const runAction = async () => {
    if (!actionDialog) {
      return;
    }

    try {
      if (actionDialog.action === 'start') {
        await startStrategy(actionDialog.strategy.id).unwrap();
        setFeedback({ severity: 'success', message: 'Strategy started in paper mode.' });
      } else {
        await stopStrategy(actionDialog.strategy.id).unwrap();
        setFeedback({ severity: 'success', message: 'Strategy stopped.' });
      }
    } catch {
      setFeedback({
        severity: 'error',
        message:
          actionDialog.action === 'start'
            ? 'Failed to start strategy.'
            : 'Failed to stop strategy.',
      });
    } finally {
      setActionDialog(null);
    }
  };

  const handleSaveConfig = async (strategy: Strategy, payload: StrategyConfigOutput) => {
    if (strategy.status === 'RUNNING') {
      await stopStrategy(strategy.id).unwrap();
    }

    await updateConfig({
      strategyId: strategy.id,
      ...payload,
    }).unwrap();

    setFeedback({ severity: 'success', message: 'Strategy configuration updated.' });
  };

  const toggleGroup = (groupKey: StrategyGroupKey) => {
    setExpandedGroups((current) =>
      current.includes(groupKey)
        ? current.filter((value) => value !== groupKey)
        : [...current, groupKey]
    );
  };

  return (
    <AppLayout>
      <PageContent maxWidth="research">
        <PageIntro
          eyebrow="Paper-safe strategy desk"
          description="Pick a template, review how it behaves, then edit configuration before you start or stop anything in the paper workflow."
          chips={
            <>
              <Chip label="Start and stop remain paper-only" variant="outlined" />
              <Chip label="Short exposure is opt-in" variant="outlined" />
              <Chip label="Config history stays visible" variant="outlined" />
            </>
          }
        />

        <PageMetricStrip items={summaryItems} />

        {feedback ? (
          <Alert severity={feedback.severity} onClose={() => setFeedback(null)}>
            {feedback.message}
          </Alert>
        ) : null}

        {isLoading ? <Alert severity="info">Loading strategy catalog...</Alert> : null}

        {isError ? (
          <Alert severity="error">
            Failed to load strategies. {strategyErrorMessage}
          </Alert>
        ) : null}

        <SurfacePanel
          title="Audited posture"
          description="The frozen audit keeps only audited candidates in the near-term paper lane so the catalog does not imply every strategy is equally ready."
          tone="info"
          actions={<StatusPill label="March 27, 2026 frozen audit" tone="info" variant="filled" />}
        >
          <Typography variant="body2" color="text.secondary">
            Only `SMA_CROSSOVER` currently sits in the paper-monitor candidate lane. Archive
            candidates remain visible for comparison, not because they are equally ready to run.
          </Typography>
        </SurfacePanel>

        <PageSectionHeader
          title="Catalog and saved configs"
          description="Keep operator-facing configs in the default view and open the full strategy guide only when you need to learn or compare profile intent."
          actions={
            <StatusPill
              label={isBusy ? 'Updating strategy state' : 'Paper mode only'}
              tone={isBusy ? 'warning' : 'success'}
              variant="filled"
            />
          }
        />

        <SurfacePanel
          title="Strategy workspace"
          description={
            contentTab === 'configs'
              ? 'Saved runtime configs stay in the primary lane so edit and start-stop actions do not compete with the full learning catalog.'
              : 'The guide stays separate from saved runtime configs so learning and operation do not blur together.'
          }
        >
          <Tabs
            value={contentTab}
            onChange={(_, value: 'configs' | 'guide') => setContentTab(value)}
            variant="scrollable"
            allowScrollButtonsMobile
            sx={{ mb: 2 }}
          >
            <Tab value="configs" label="Saved configs" />
            <Tab value="guide" label="Strategy guide" />
          </Tabs>

          {contentTab === 'guide' ? (
            <Grid container spacing={1.5}>
              {strategyProfiles.map((profile) => (
                <Grid key={profile.key} size={{ xs: 12, md: 6, xl: 4 }}>
                  <SurfacePanel
                    title={profile.title}
                    description={profile.shortDescription}
                    sx={{ height: '100%' }}
                  >
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      <StatusPill
                        label={profile.auditLabel}
                        tone={profile.auditTone}
                        variant="filled"
                      />
                    </Stack>
                    <Typography variant="caption" display="block">
                      Entry: {profile.entryRule}
                    </Typography>
                    <Typography variant="caption" display="block">
                      Exit: {profile.exitRule}
                    </Typography>
                    <Typography variant="caption" display="block">
                      Best for: {profile.bestFor}
                    </Typography>
                    <Typography variant="caption" display="block">
                      Risk note: {profile.riskNotes}
                    </Typography>
                    <Typography variant="caption" display="block" sx={{ mt: 0.75 }}>
                      Audit note: {profile.auditSummary}
                    </Typography>
                    <Typography variant="caption" display="block">
                      Operator action: {profile.operatorAction}
                    </Typography>
                  </SurfacePanel>
                </Grid>
              ))}
            </Grid>
          ) : !isLoading && !isError ? (
            strategies.length === 0 ? (
              <EmptyState
                title="No saved strategies returned"
                description="When the backend exposes saved strategy configs, they will appear here for review and editing."
                tone="info"
              />
            ) : (
              <Stack spacing={1.25}>
                {groupedStrategies.map((group) => (
                  <Accordion
                    key={group.key}
                    disableGutters
                    elevation={0}
                    expanded={expandedGroups.includes(group.key)}
                    onChange={() => toggleGroup(group.key)}
                    sx={{
                      border: '1px solid',
                      borderColor: 'divider',
                      '&::before': { display: 'none' },
                    }}
                  >
                    <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                      <Stack spacing={0.5} sx={{ minWidth: 0, width: '100%' }}>
                        <Stack
                          direction={{ xs: 'column', sm: 'row' }}
                          spacing={1}
                          justifyContent="space-between"
                          alignItems={{ xs: 'flex-start', sm: 'center' }}
                        >
                          <Typography variant="subtitle2">{group.meta.title}</Typography>
                          <StatusPill
                            label={`${group.items.length} config${group.items.length === 1 ? '' : 's'}`}
                            tone={group.meta.tone}
                            variant="filled"
                          />
                        </Stack>
                        <Typography variant="caption" color="text.secondary">
                          {group.meta.description}
                        </Typography>
                      </Stack>
                    </AccordionSummary>
                    <AccordionDetails>
                      <Stack spacing={1.25}>
                        {group.items.map(({ strategy, profile: strategyProfile }) => (
                          <SurfacePanel
                            key={strategy.id}
                            title={strategy.name}
                            description={strategyProfile?.shortDescription ?? 'Saved paper-safe strategy config.'}
                            actions={
                              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                                <Button
                                  size="small"
                                  variant="contained"
                                  disabled={isBusy}
                                  onClick={() => setSelectedStrategy(strategy)}
                                >
                                  Edit config
                                </Button>
                                <Button
                                  size="small"
                                  variant="outlined"
                                  disabled={isBusy}
                                  onClick={() =>
                                    setActionDialog({
                                      strategy,
                                      action: strategy.status === 'RUNNING' ? 'stop' : 'start',
                                    })
                                  }
                                >
                                  {strategy.status === 'RUNNING' ? 'Stop' : 'Start'}
                                </Button>
                              </Stack>
                            }
                          >
                            <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                              <StatusPill
                                label={strategy.status}
                                tone={statusTone(strategy.status)}
                                variant="filled"
                              />
                              <StatusPill
                                label={strategyProfile?.auditLabel ?? strategy.type}
                                tone={strategyProfile?.auditTone ?? 'info'}
                              />
                              <StatusPill
                                label={strategy.shortSellingEnabled ? 'Long + short enabled' : 'Long only'}
                                tone={strategy.shortSellingEnabled ? 'warning' : 'success'}
                              />
                            </Stack>

                            <Grid container spacing={1}>
                              <Grid size={{ xs: 12, md: 6, xl: 3 }}>
                                <Typography variant="caption" color="text.secondary" display="block">
                                  Market
                                </Typography>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  {`${strategy.symbol} (${strategy.timeframe})`}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {strategyProfile?.title ?? strategy.type}
                                </Typography>
                              </Grid>
                              <Grid size={{ xs: 6, md: 3, xl: 2 }}>
                                <Typography variant="caption" color="text.secondary" display="block">
                                  Risk/Trade
                                </Typography>
                                <NumericText variant="body2">
                                  {(strategy.riskPerTrade * 100).toFixed(2)}%
                                </NumericText>
                              </Grid>
                              <Grid size={{ xs: 6, md: 3, xl: 2 }}>
                                <Typography variant="caption" color="text.secondary" display="block">
                                  P&amp;L
                                </Typography>
                                <NumericText
                                  variant="body2"
                                  tone={strategy.profitLoss >= 0 ? 'success' : 'error'}
                                >
                                  {strategy.profitLoss.toFixed(2)}
                                </NumericText>
                              </Grid>
                              <Grid size={{ xs: 6, md: 3, xl: 2 }}>
                                <Typography variant="caption" color="text.secondary" display="block">
                                  Trades
                                </Typography>
                                <NumericText variant="body2">{strategy.tradeCount}</NumericText>
                              </Grid>
                              <Grid size={{ xs: 6, md: 3, xl: 3 }}>
                                <Typography variant="caption" color="text.secondary" display="block">
                                  Config
                                </Typography>
                                <Typography variant="body2" sx={{ fontWeight: 700 }}>
                                  {`v${strategy.configVersion}`}
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                  {strategy.lastConfigChangedAt
                                    ? formatDateTime(strategy.lastConfigChangedAt)
                                    : 'No config timestamp available'}
                                </Typography>
                              </Grid>
                            </Grid>

                            <Typography variant="body2" color="text.secondary">
                              {strategyProfile?.auditSummary ?? 'No audit summary available for this strategy type.'}
                            </Typography>
                          </SurfacePanel>
                        ))}
                      </Stack>
                    </AccordionDetails>
                  </Accordion>
                ))}
              </Stack>
            )
          ) : null}
        </SurfacePanel>
      </PageContent>

      <Dialog open={Boolean(actionDialog)} onClose={() => setActionDialog(null)}>
        <DialogTitle>
          {actionDialog?.action === 'start' ? 'Start Strategy' : 'Stop Strategy'}
        </DialogTitle>
        <DialogContent>
          <DialogContentText>
            {actionDialog
              ? `Are you sure you want to ${actionDialog.action} "${actionDialog.strategy.name}"?`
              : ''}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setActionDialog(null)}>Cancel</Button>
          <Button variant="contained" onClick={() => void runAction()} disabled={isBusy}>
            Confirm
          </Button>
        </DialogActions>
      </Dialog>

      {selectedStrategy ? (
        <StrategyConfigModal
          key={selectedStrategy.id}
          strategy={selectedStrategy}
          busy={isSavingConfig}
          onClose={() => setSelectedStrategy(null)}
          onSave={handleSaveConfig}
        />
      ) : null}
    </AppLayout>
  );
}
