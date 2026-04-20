import { Alert, Chip, Grid } from '@mui/material';
import { useEffect, useMemo, useRef, useState } from 'react';

import { CircuitBreakerPanel } from './CircuitBreakerPanel';
import { PositionSizingCalculator } from './PositionSizingCalculator';
import {
  useGetCircuitBreakersQuery,
  useGetRiskAlertsQuery,
  useGetRiskConfigQuery,
  useGetRiskStatusQuery,
  useOverrideCircuitBreakerMutation,
  useUpdateRiskConfigMutation,
} from './riskApi';
import { RiskConfigForm } from './RiskConfigForm';
import { RiskMetrics } from './RiskMetrics';

import { useAppSelector } from '@/app/hooks';
import { AppLayout } from '@/components/layout/AppLayout';
import {
  PageContent,
  PageIntro,
  type PageMetricItem,
  PageMetricStrip,
} from '@/components/layout/PageContent';
import { selectEnvironmentMode } from '@/features/environment/environmentSlice';

const playAlertTone = () => {
  if (typeof window === 'undefined' || !window.AudioContext) {
    return;
  }
  const audio = new window.AudioContext();
  const oscillator = audio.createOscillator();
  const gain = audio.createGain();
  oscillator.type = 'triangle';
  oscillator.frequency.setValueAtTime(880, audio.currentTime);
  gain.gain.setValueAtTime(0.04, audio.currentTime);
  oscillator.connect(gain);
  gain.connect(audio.destination);
  oscillator.start();
  oscillator.stop(audio.currentTime + 0.15);
};

export default function RiskPage() {
  const environmentMode = useAppSelector(selectEnvironmentMode);
  const { data: status, isLoading: isStatusLoading } = useGetRiskStatusQuery(undefined, {
    pollingInterval: 30000,
    skipPollingIfUnfocused: true,
  });
  const { data: config, isLoading: isConfigLoading } = useGetRiskConfigQuery();
  const { data: circuitBreakers = [] } = useGetCircuitBreakersQuery();
  const { data: alerts = [] } = useGetRiskAlertsQuery(undefined, {
    pollingInterval: 5000,
    skipPollingIfUnfocused: true,
  });

  const [updateConfig, { isLoading: isSavingConfig }] = useUpdateRiskConfigMutation();
  const [overrideCircuitBreaker, { isLoading: isOverriding }] =
    useOverrideCircuitBreakerMutation();
  const [feedback, setFeedback] = useState<{
    severity: 'success' | 'error' | 'warning';
    message: string;
  } | null>(null);

  const seenAlertIds = useRef<Set<number>>(new Set());
  const highAlerts = useMemo(
    () => alerts.filter((alert) => alert.severity === 'HIGH'),
    [alerts]
  );
  const summaryItems = useMemo<PageMetricItem[]>(
    () => [
      {
        label: 'Environment',
        value: environmentMode.toUpperCase(),
        detail:
          environmentMode === 'live'
            ? 'Live overrides remain blocked here.'
            : 'Controls currently guard test and paper workflows.',
        tone: environmentMode === 'live' ? 'error' : 'success',
      },
      {
        label: 'Circuit Breaker',
        value: status?.circuitBreakerActive ? 'Active' : 'Inactive',
        detail: status?.circuitBreakerReason ?? 'No breaker reason currently reported.',
        tone: status?.circuitBreakerActive ? 'error' : 'success',
      },
      {
        label: 'Alerts',
        value: alerts.length.toString(),
        detail: `${highAlerts.length} high-priority alert${highAlerts.length === 1 ? '' : 's'} in the recent window.`,
        tone: highAlerts.length > 0 ? 'warning' : 'info',
      },
      {
        label: 'Breaker Inventory',
        value: circuitBreakers.length.toString(),
        detail: 'Manual overrides stay audit-gated and paper-only.',
        tone: 'info',
      },
    ],
    [alerts.length, circuitBreakers.length, environmentMode, highAlerts.length, status]
  );

  useEffect(() => {
    const newHighAlert = highAlerts.find((alert) => !seenAlertIds.current.has(alert.id));
    alerts.forEach((alert) => seenAlertIds.current.add(alert.id));

    if (newHighAlert) {
      playAlertTone();
      setFeedback({
        severity: 'warning',
        message: `High priority risk alert: ${newHighAlert.message}`,
      });
    }
  }, [alerts, highAlerts]);

  const saveRiskConfig = async (payload: {
    maxRiskPerTrade: number;
    maxDailyLossLimit: number;
    maxDrawdownLimit: number;
    maxOpenPositions: number;
    correlationLimit: number;
  }) => {
    try {
      await updateConfig(payload).unwrap();
      setFeedback({ severity: 'success', message: 'Risk configuration saved.' });
    } catch {
      setFeedback({ severity: 'error', message: 'Failed to save risk configuration.' });
    }
  };

  const overrideBreaker = async (payload: {
    confirmationCode: string;
    reason: string;
  }) => {
    try {
      await overrideCircuitBreaker(payload).unwrap();
      setFeedback({ severity: 'success', message: 'Circuit breaker override accepted.' });
    } catch {
      setFeedback({
        severity: 'error',
        message: 'Override rejected. Verify confirmation code and reason.',
      });
    }
  };

  return (
    <AppLayout>
      <PageContent maxWidth="research">
        <PageIntro
          eyebrow="Protective controls first"
          description="Review breaker posture, active alerts, and saved limits before you touch overrides or position-sizing assumptions."
          chips={
            <>
              <Chip label="Test and paper protected" variant="outlined" />
              <Chip label="Live override blocked" variant="outlined" />
              <Chip label="Manual override requires audit context" variant="outlined" />
            </>
          }
        />

        <PageMetricStrip items={summaryItems} />

        {feedback ? (
          <Alert severity={feedback.severity} onClose={() => setFeedback(null)}>
            {feedback.message}
          </Alert>
        ) : null}

        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12 }}>
            <RiskMetrics status={status} loading={isStatusLoading} />
          </Grid>

          <Grid size={{ xs: 12 }}>
            <CircuitBreakerPanel
              alerts={alerts}
              circuitBreakers={circuitBreakers}
              environmentMode={environmentMode}
              busy={isOverriding}
              onOverride={overrideBreaker}
            />
          </Grid>

          <Grid size={{ xs: 12, lg: 7 }}>
            <RiskConfigForm
              config={config}
              loading={isConfigLoading}
              busy={isSavingConfig}
              onSave={saveRiskConfig}
            />
          </Grid>

          <Grid size={{ xs: 12, lg: 5 }}>
            <PositionSizingCalculator />
          </Grid>
        </Grid>
      </PageContent>
    </AppLayout>
  );
}
