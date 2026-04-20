import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';

import { useGetStrategyConfigHistoryQuery, type Strategy } from './strategiesApi';
import { getStrategyProfile } from './strategyProfiles';
import {
  type StrategyConfigOutput,
  validateStrategyConfig,
} from './strategyValidation';

import { FieldTooltip } from '@/components/ui/FieldTooltip';
import { sanitizeText } from '@/utils/security';

interface ConfigDraft {
  symbol: string;
  timeframe: string;
  riskPerTrade: string;
  minPositionSize: string;
  maxPositionSize: string;
  shortSellingEnabled: boolean;
}

const createDraft = (strategy: Strategy): ConfigDraft => ({
  symbol: strategy.symbol,
  timeframe: strategy.timeframe,
  riskPerTrade: String(strategy.riskPerTrade),
  minPositionSize: String(strategy.minPositionSize),
  maxPositionSize: String(strategy.maxPositionSize),
  shortSellingEnabled: strategy.shortSellingEnabled,
});

export interface StrategyConfigModalProps {
  strategy: Strategy;
  busy: boolean;
  onClose: () => void;
  onSave: (strategy: Strategy, payload: StrategyConfigOutput) => Promise<void> | void;
}

export function StrategyConfigModal({
  strategy,
  busy,
  onClose,
  onSave,
}: StrategyConfigModalProps) {
  const { data: history = [] } = useGetStrategyConfigHistoryQuery(strategy.id);
  const [draft, setDraft] = useState<ConfigDraft>(() => createDraft(strategy));
  const [submitError, setSubmitError] = useState<string | null>(null);
  const profile = useMemo(() => getStrategyProfile(strategy.type), [strategy.type]);

  const validation = useMemo(
    () =>
      validateStrategyConfig({
        symbol: draft.symbol,
        timeframe: draft.timeframe,
        riskPerTrade: Number(draft.riskPerTrade),
        minPositionSize: Number(draft.minPositionSize),
        maxPositionSize: Number(draft.maxPositionSize),
        shortSellingEnabled: draft.shortSellingEnabled,
      }),
    [draft]
  );

  const save = async () => {
    if (!validation.valid || !validation.data) {
      return;
    }

    try {
      await onSave(strategy, validation.data);
      onClose();
    } catch {
      setSubmitError('Failed to update strategy configuration.');
    }
  };

  const applyRecommendedSettings = () => {
    if (!profile) {
      return;
    }

    setDraft((prev) => ({
      ...prev,
      timeframe: profile.configPreset.timeframe,
      riskPerTrade: String(profile.configPreset.riskPerTrade),
      minPositionSize: String(profile.configPreset.minPositionSize),
      maxPositionSize: String(profile.configPreset.maxPositionSize),
    }));
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Update Strategy Configuration</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {profile ? (
            <Alert severity="info">
              <strong>{profile.title}</strong>: {profile.bestFor} Recommended timeframes:{' '}
              {profile.timeframeOptions.join(', ')}. Risk note: {profile.riskNotes}
            </Alert>
          ) : null}
          {profile ? (
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              <Button variant="outlined" onClick={applyRecommendedSettings}>
                Apply Strategy Defaults
              </Button>
              {profile.timeframeOptions.map((timeframe) => (
                <Button
                  key={timeframe}
                  variant={draft.timeframe === timeframe ? 'contained' : 'text'}
                  onClick={() => setDraft((prev) => ({ ...prev, timeframe }))}
                >
                  {timeframe}
                </Button>
              ))}
            </Stack>
          ) : null}
          <FieldTooltip title="Trading pair for this strategy. Wrong symbol can invalidate assumptions and risk settings.">
            <TextField
              label="Symbol"
              value={draft.symbol}
              onChange={(event) =>
                setDraft((prev) => ({ ...prev, symbol: sanitizeText(event.target.value) }))
              }
              error={Boolean(validation.errors.symbol)}
              helperText={validation.errors.symbol ?? 'Market pair to trade (for example BTC/USDT).'}
              fullWidth
            />
          </FieldTooltip>
          <FieldTooltip title="Candle interval used by signal logic. Shorter intervals increase trade frequency and noise.">
            <TextField
              label="Timeframe"
              value={draft.timeframe}
              onChange={(event) =>
                setDraft((prev) => ({ ...prev, timeframe: sanitizeText(event.target.value) }))
              }
              error={Boolean(validation.errors.timeframe)}
              helperText={validation.errors.timeframe ?? 'Candle interval such as 15m, 1h, or 4h.'}
              fullWidth
            />
          </FieldTooltip>
          <FieldTooltip title="Fraction of capital risked per trade. Higher values increase drawdown and stop-out probability.">
            <TextField
              label="Risk Per Trade (0.01 - 0.05)"
              type="number"
              value={draft.riskPerTrade}
              onChange={(event) =>
                setDraft((prev) => ({
                  ...prev,
                  riskPerTrade: event.target.value,
                }))
              }
              inputProps={{ step: '0.001' }}
              error={Boolean(validation.errors.riskPerTrade)}
              helperText={
                validation.errors.riskPerTrade ??
                'Fraction of account risked per position. 0.02 means 2%.'
              }
              fullWidth
            />
          </FieldTooltip>
          <FieldTooltip title="Minimum order size gate. Too high may skip valid signals; too low may create noisy micro-trades.">
            <TextField
              label="Min Position Size"
              type="number"
              value={draft.minPositionSize}
              onChange={(event) =>
                setDraft((prev) => ({
                  ...prev,
                  minPositionSize: event.target.value,
                }))
              }
              error={Boolean(validation.errors.minPositionSize)}
              helperText={
                validation.errors.minPositionSize ?? 'Lower position bound used by execution sizing logic.'
              }
              fullWidth
            />
          </FieldTooltip>
          <FieldTooltip title="Maximum order size cap. Raising this increases tail risk per signal.">
            <TextField
              label="Max Position Size"
              type="number"
              value={draft.maxPositionSize}
              onChange={(event) =>
                setDraft((prev) => ({
                  ...prev,
                  maxPositionSize: event.target.value,
                }))
              }
              error={Boolean(validation.errors.maxPositionSize)}
              helperText={
                validation.errors.maxPositionSize ?? 'Upper position bound to limit exposure per trade.'
              }
              fullWidth
            />
          </FieldTooltip>
          <FieldTooltip title="Allows the strategy to open short exposure in research and paper mode. Live execution remains disabled.">
            <FormControlLabel
              control={
                <Switch
                  checked={draft.shortSellingEnabled}
                  onChange={(_, checked) =>
                    setDraft((prev) => ({ ...prev, shortSellingEnabled: checked }))
                  }
                />
              }
              label={draft.shortSellingEnabled ? 'Short Selling Enabled' : 'Long Only'}
            />
          </FieldTooltip>

          {submitError ? <Alert severity="error">{submitError}</Alert> : null}

          <Divider />
          <Stack spacing={1}>
            <Typography variant="subtitle2">Config Version History</Typography>
            <Typography variant="caption" color="text.secondary">
              Versioned snapshots make paper-trading and backtest changes traceable over time.
            </Typography>
            {history.length > 0 ? (
              history.map((entry) => (
                <Alert key={entry.id} severity={entry.versionNumber === strategy.configVersion ? 'info' : 'success'}>
                  v{entry.versionNumber}: {entry.changeReason}
                  <br />
                  {entry.symbol} ({entry.timeframe}) | Risk {(entry.riskPerTrade * 100).toFixed(2)}% | Size{' '}
                  {entry.minPositionSize} - {entry.maxPositionSize} | {entry.shortSellingEnabled ? 'Short On' : 'Long Only'}
                </Alert>
              ))
            ) : (
              <Typography variant="body2" color="text.secondary">
                No config history available yet.
              </Typography>
            )}
          </Stack>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void save()} disabled={busy || !validation.valid}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
