import { Alert, Button, Stack, TextField, Typography } from '@mui/material';
import { useMemo, useState } from 'react';

import type { RiskAlert, RiskConfig } from './riskApi';

import { FieldTooltip } from '@/components/ui/FieldTooltip';
import { StatusPill, SurfacePanel } from '@/components/ui/Workbench';
import type { EnvironmentMode } from '@/features/environment/environmentSlice';
import { sanitizeText } from '@/utils/security';

interface CircuitBreakerPanelProps {
  alerts: RiskAlert[];
  circuitBreakers: RiskConfig[];
  environmentMode: EnvironmentMode;
  onOverride: (payload: { confirmationCode: string; reason: string }) => Promise<void> | void;
  busy: boolean;
}

export function CircuitBreakerPanel({
  alerts,
  circuitBreakers,
  environmentMode,
  onOverride,
  busy,
}: CircuitBreakerPanelProps) {
  const [overrideCode, setOverrideCode] = useState('');
  const [overrideReason, setOverrideReason] = useState('');

  const canSubmit = useMemo(
    () => overrideCode.trim().length > 0 && overrideReason.trim().length > 0 && !busy,
    [overrideCode, overrideReason, busy]
  );

  const submit = async () => {
    if (!canSubmit) {
      return;
    }
    await onOverride({
      confirmationCode: overrideCode.trim(),
      reason: overrideReason.trim(),
    });
    setOverrideCode('');
    setOverrideReason('');
  };

  return (
    <SurfacePanel
      title="Circuit breakers and alerts"
      description="Read the current breaker inventory and recent alerts before you even consider the manual override flow."
      tone={alerts.some((alert) => alert.severity === 'HIGH') ? 'warning' : 'default'}
      actions={
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <StatusPill
            label={`Environment: ${environmentMode}`}
            tone={environmentMode === 'live' ? 'error' : 'success'}
          />
          <StatusPill label={`${circuitBreakers.length} configured breakers`} />
        </Stack>
      }
    >
      <Stack spacing={1}>
        {circuitBreakers.length === 0 ? (
          <Typography variant="body2">No circuit-breaker configurations returned.</Typography>
        ) : (
          circuitBreakers.map((breaker, index) => (
            <Alert
              key={`${breaker.maxRiskPerTrade}-${breaker.maxDailyLossLimit}-${index}`}
              severity={breaker.circuitBreakerActive ? 'warning' : 'info'}
            >
              Max risk per trade {(breaker.maxRiskPerTrade * 100).toFixed(2)}% | Daily loss{' '}
              {(breaker.maxDailyLossLimit * 100).toFixed(2)}% | Drawdown{' '}
              {(breaker.maxDrawdownLimit * 100).toFixed(2)}% | Max positions{' '}
              {breaker.maxOpenPositions} | Correlation {(breaker.correlationLimit * 100).toFixed(0)}%
              {breaker.circuitBreakerReason
                ? ` | Active reason: ${breaker.circuitBreakerReason}`
                : ' | Breaker currently inactive'}
            </Alert>
          ))
        )}
      </Stack>

      <Stack spacing={1} aria-live="assertive">
        <Typography variant="subtitle2">Recent alerts</Typography>
        {alerts.length === 0 ? <Typography variant="body2">No recent alerts.</Typography> : null}
        {alerts.slice(0, 10).map((alert) => (
          <Alert key={alert.id} severity={alert.severity === 'HIGH' ? 'error' : 'warning'}>
            [{alert.type}] {alert.message} ({alert.actionTaken})
          </Alert>
        ))}
      </Stack>

      <SurfacePanel
        title="Danger zone: manual override"
        description="Override remains test and paper only. Use it only when you can justify the action for audit and incident review."
        tone="error"
        contentSx={{ gap: 1.5 }}
      >
        <Alert severity="warning">
          Override is test and paper only. Use confirmation code and reason for auditability.
        </Alert>
        <Stack spacing={2}>
          <FieldTooltip title="Required confirmation token for manual override. Invalid code blocks the action.">
            <TextField
              label="Confirmation Code"
              value={overrideCode}
              onChange={(event) => setOverrideCode(sanitizeText(event.target.value))}
              placeholder="OVERRIDE_PAPER_ONLY"
              helperText="Used as a deliberate safety gate."
            />
          </FieldTooltip>
          <FieldTooltip title="Required audit reason. Weak or empty rationale reduces operational traceability.">
            <TextField
              label="Override Reason"
              value={overrideReason}
              onChange={(event) => setOverrideReason(sanitizeText(event.target.value))}
              helperText="Document why override is necessary and temporary."
            />
          </FieldTooltip>
          <Button
            variant="outlined"
            color="warning"
            onClick={() => void submit()}
            disabled={!canSubmit}
          >
            Override circuit breaker
          </Button>
        </Stack>
      </SurfacePanel>
    </SurfacePanel>
  );
}
