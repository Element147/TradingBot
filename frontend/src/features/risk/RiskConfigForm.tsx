import { Alert, Button, Stack, TextField } from '@mui/material';
import { useMemo, useState } from 'react';

import type { RiskConfig } from './riskApi';

import { FieldTooltip } from '@/components/ui/FieldTooltip';
import { SurfacePanel } from '@/components/ui/Workbench';

interface RiskConfigFormProps {
  config: RiskConfig | undefined;
  loading: boolean;
  busy: boolean;
  onSave: (payload: {
    maxRiskPerTrade: number;
    maxDailyLossLimit: number;
    maxDrawdownLimit: number;
    maxOpenPositions: number;
    correlationLimit: number;
  }) => Promise<void> | void;
}

export function RiskConfigForm({ config, loading, busy, onSave }: RiskConfigFormProps) {
  const [draft, setDraft] = useState<{
    maxRiskPerTrade: string;
    maxDailyLossLimit: string;
    maxDrawdownLimit: string;
    maxOpenPositions: string;
    correlationLimit: string;
  } | null>(null);

  const values = useMemo(
    () =>
      draft ?? {
        maxRiskPerTrade: String(config?.maxRiskPerTrade ?? '0.02'),
        maxDailyLossLimit: String(config?.maxDailyLossLimit ?? '0.05'),
        maxDrawdownLimit: String(config?.maxDrawdownLimit ?? '0.25'),
        maxOpenPositions: String(config?.maxOpenPositions ?? '5'),
        correlationLimit: String(config?.correlationLimit ?? '0.75'),
      },
    [config, draft]
  );

  const error = useMemo(() => {
    const maxRiskPerTrade = Number(values.maxRiskPerTrade);
    const maxDailyLossLimit = Number(values.maxDailyLossLimit);
    const maxDrawdownLimit = Number(values.maxDrawdownLimit);
    const maxOpenPositions = Number(values.maxOpenPositions);
    const correlationLimit = Number(values.correlationLimit);

    if (Number.isNaN(maxRiskPerTrade) || maxRiskPerTrade < 0.01 || maxRiskPerTrade > 0.05) {
      return 'Max risk per trade must be between 0.01 and 0.05.';
    }
    if (Number.isNaN(maxDailyLossLimit) || maxDailyLossLimit < 0.01 || maxDailyLossLimit > 0.1) {
      return 'Max daily loss limit must be between 0.01 and 0.10.';
    }
    if (Number.isNaN(maxDrawdownLimit) || maxDrawdownLimit < 0.1 || maxDrawdownLimit > 0.5) {
      return 'Max drawdown limit must be between 0.10 and 0.50.';
    }
    if (!Number.isInteger(maxOpenPositions) || maxOpenPositions < 1 || maxOpenPositions > 10) {
      return 'Max open positions must be an integer between 1 and 10.';
    }
    if (Number.isNaN(correlationLimit) || correlationLimit < 0.1 || correlationLimit > 1) {
      return 'Correlation limit must be between 0.10 and 1.00.';
    }
    return null;
  }, [values]);

  const save = async () => {
    if (error) {
      return;
    }
    await onSave({
      maxRiskPerTrade: Number(values.maxRiskPerTrade),
      maxDailyLossLimit: Number(values.maxDailyLossLimit),
      maxDrawdownLimit: Number(values.maxDrawdownLimit),
      maxOpenPositions: Number(values.maxOpenPositions),
      correlationLimit: Number(values.correlationLimit),
    });
  };

  return (
    <SurfacePanel
      title="Risk configuration"
      description="Update baseline guardrails here. Save only after the breaker posture above makes sense."
    >
      {loading ? (
        'Loading config...'
      ) : (
        <Stack spacing={2}>
          <FieldTooltip title="Maximum capital risk per trade. Increasing this accelerates drawdown under losing streaks.">
            <TextField
              label="Max Risk Per Trade (0.01 - 0.05)"
              value={values.maxRiskPerTrade}
              onChange={(event) =>
                setDraft((prev) => ({ ...(prev ?? values), maxRiskPerTrade: event.target.value }))
              }
              helperText="0.02 means 2% risk cap per trade."
            />
          </FieldTooltip>
          <FieldTooltip title="Daily realized loss cap. When breached, strategy activity should stop for the day.">
            <TextField
              label="Max Daily Loss Limit (0.01 - 0.10)"
              value={values.maxDailyLossLimit}
              onChange={(event) =>
                setDraft((prev) => ({
                  ...(prev ?? values),
                  maxDailyLossLimit: event.target.value,
                }))
              }
              helperText="Protects account from cascading same-day losses."
            />
          </FieldTooltip>
          <FieldTooltip title="Portfolio drawdown hard limit. Raising it tolerates deeper equity decline before halt.">
            <TextField
              label="Max Drawdown Limit (0.10 - 0.50)"
              value={values.maxDrawdownLimit}
              onChange={(event) =>
                setDraft((prev) => ({
                  ...(prev ?? values),
                  maxDrawdownLimit: event.target.value,
                }))
              }
              helperText="Used by guardrails and circuit-breaker logic."
            />
          </FieldTooltip>
          <FieldTooltip title="Maximum simultaneous positions. More positions increase exposure complexity and correlation risk.">
            <TextField
              label="Max Open Positions (1 - 10)"
              value={values.maxOpenPositions}
              onChange={(event) =>
                setDraft((prev) => ({
                  ...(prev ?? values),
                  maxOpenPositions: event.target.value,
                }))
              }
              helperText="Limit concurrent trades to control aggregate risk."
            />
          </FieldTooltip>
          <FieldTooltip title="Allowed correlation between open positions. High values can concentrate risk in one market regime.">
            <TextField
              label="Correlation Limit (0.10 - 1.00)"
              value={values.correlationLimit}
              onChange={(event) =>
                setDraft((prev) => ({
                  ...(prev ?? values),
                  correlationLimit: event.target.value,
                }))
              }
              helperText="Lower values enforce more diversification."
            />
          </FieldTooltip>
          {error ? <Alert severity="error">{error}</Alert> : null}
          <Button variant="contained" onClick={() => void save()} disabled={busy || Boolean(error)}>
            Save risk config
          </Button>
        </Stack>
      )}
    </SurfacePanel>
  );
}
