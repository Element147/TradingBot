import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useEffect, useMemo } from 'react';

import type { BacktestAlgorithm, BacktestDataset, RunBacktestPayload } from './backtestApi';
import {
  buildRunBacktestPayload,
  type BacktestConfigFormState,
} from './backtestConfigForm';

import { FieldTooltip } from '@/components/ui/FieldTooltip';
import { getStrategyProfile } from '@/features/strategies/strategyProfiles';
import { sanitizeText } from '@/utils/security';

interface BacktestConfigModalProps {
  open: boolean;
  form: BacktestConfigFormState;
  algorithms: BacktestAlgorithm[];
  datasets: BacktestDataset[];
  busy: boolean;
  onChange: (next: BacktestConfigFormState) => void;
  onClose: () => void;
  onRun: (payload: RunBacktestPayload) => Promise<void> | void;
}

const parseSymbols = (symbolsCsv: string): string[] =>
  symbolsCsv
    .split(',')
    .map((symbol) => symbol.trim())
    .filter((symbol) => symbol.length > 0);

const COMMON_TIMEFRAMES = ['15m', '1h', '4h', '1d'];

export function BacktestConfigModal({
  open,
  form,
  algorithms,
  datasets,
  busy,
  onChange,
  onClose,
  onRun,
}: BacktestConfigModalProps) {
  const selectedAlgorithm = useMemo(
    () => algorithms.find((algorithm) => algorithm.id === form.algorithmType) ?? null,
    [algorithms, form.algorithmType]
  );
  const selectedDataset = useMemo(
    () => datasets.find((dataset) => String(dataset.id) === form.datasetId) ?? null,
    [datasets, form.datasetId]
  );
  const availableSymbols = useMemo(
    () => (selectedDataset ? parseSymbols(selectedDataset.symbolsCsv) : []),
    [selectedDataset]
  );
  const requiresDatasetUniverse = selectedAlgorithm?.selectionMode === 'DATASET_UNIVERSE';
  const selectedAlgorithmProfile = useMemo(
    () => getStrategyProfile(form.algorithmType),
    [form.algorithmType]
  );
  const timeframeOptions = selectedAlgorithmProfile?.timeframeOptions ?? COMMON_TIMEFRAMES;
  const recommendedTimeframe =
    selectedAlgorithmProfile?.configPreset.timeframe ?? timeframeOptions[0] ?? '1h';
  const dispositionHeadline = selectedAlgorithmProfile
    ? `${selectedAlgorithmProfile.auditLabel}: ${selectedAlgorithmProfile.auditSummary}`
    : null;

  useEffect(() => {
    if (requiresDatasetUniverse) {
      if (form.symbol) {
        onChange({ ...form, symbol: '' });
      }
      return;
    }

    if (availableSymbols.length === 0) {
      return;
    }

    if (!form.symbol || !availableSymbols.includes(form.symbol)) {
      onChange({ ...form, symbol: availableSymbols[0] });
    }
  }, [availableSymbols, form, onChange, requiresDatasetUniverse]);

  const validation = useMemo(() => {
    const errors: Partial<Record<keyof BacktestConfigFormState | 'dateRange', string>> = {};

    if (!form.algorithmType.trim()) {
      errors.algorithmType = 'Choose a strategy before starting a run.';
    }

    if (!form.datasetId) {
      errors.datasetId = 'Please choose a dataset first.';
    } else if (!selectedDataset) {
      errors.datasetId = 'The selected dataset is no longer available. Pick another one.';
    }

    if (!requiresDatasetUniverse) {
      if (!form.symbol.trim()) {
        errors.symbol = 'Please choose one symbol from the selected dataset.';
      } else if (!availableSymbols.includes(form.symbol)) {
        errors.symbol = 'The selected symbol is not available in this dataset.';
      }
    }

    if (!form.timeframe.trim()) {
      errors.timeframe = 'Choose a timeframe.';
    } else if (!timeframeOptions.includes(form.timeframe)) {
      errors.timeframe = `Choose one of the supported timeframes for this strategy: ${timeframeOptions.join(', ')}.`;
    }

    const initialBalance = Number(form.initialBalance);
    if (Number.isNaN(initialBalance) || initialBalance <= 100) {
      errors.initialBalance = 'Initial balance must be greater than 100.';
    }

    const fees = Number(form.feesBps);
    if (Number.isNaN(fees) || fees < 0 || fees > 200) {
      errors.feesBps = 'Fees must be between 0 and 200 bps.';
    }

    const slippage = Number(form.slippageBps);
    if (Number.isNaN(slippage) || slippage < 0 || slippage > 200) {
      errors.slippageBps = 'Slippage must be between 0 and 200 bps.';
    }

    if (new Date(form.startDate) >= new Date(form.endDate)) {
      errors.dateRange = 'Start date must be earlier than end date.';
    }

    return {
      errors,
      summary:
        errors.algorithmType ??
        errors.datasetId ??
        errors.symbol ??
        errors.timeframe ??
        errors.initialBalance ??
        errors.feesBps ??
        errors.slippageBps ??
        errors.dateRange ??
        null,
    };
  }, [availableSymbols, form, requiresDatasetUniverse, selectedDataset, timeframeOptions]);

  const run = async () => {
    if (validation.summary) {
      return;
    }

    await onRun(
      buildRunBacktestPayload(form, selectedAlgorithm?.selectionMode ?? 'SINGLE_SYMBOL')
    );
  };

  const applyRecommendedSetup = () => {
    onChange({
      ...form,
      timeframe: recommendedTimeframe,
      feesBps: '10',
      slippageBps: '3',
      symbol: requiresDatasetUniverse ? '' : availableSymbols[0] ?? form.symbol,
    });
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Run New Backtest</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Paper
            variant="outlined"
            sx={{
              p: 2,
              borderRadius: 3,
              backgroundColor: 'background.paper',
            }}
          >
            <Stack spacing={1.25}>
              <Typography variant="subtitle1" fontWeight={700}>
                Beginner-friendly setup
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Pick a strategy, choose a dataset that matches its style, then keep timeframe and
                cost assumptions realistic. Backtests are research evidence, not proof of future
                profits.
              </Typography>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip
                  label={
                    requiresDatasetUniverse ? 'Multi-symbol strategy' : 'Single-symbol strategy'
                  }
                  color="primary"
                  variant="outlined"
                />
                {selectedAlgorithmProfile ? (
                  <Chip
                    label={selectedAlgorithmProfile.auditLabel}
                    color={selectedAlgorithmProfile.auditTone}
                    variant="outlined"
                  />
                ) : null}
                <Chip
                  label={`Recommended timeframe: ${recommendedTimeframe}`}
                  color="success"
                  variant="outlined"
                />
                <Button size="small" variant="contained" onClick={applyRecommendedSetup}>
                  Use Recommended Setup
                </Button>
              </Stack>
            </Stack>
          </Paper>

          <FieldTooltip title="Select the strategy model to evaluate. Different models can produce very different risk and drawdown behavior.">
            <Autocomplete
              options={algorithms}
              value={selectedAlgorithm}
              onChange={(_event, value) =>
                onChange({
                  ...form,
                  algorithmType: value?.id ?? '',
                  timeframe: value
                    ? getStrategyProfile(value.id)?.configPreset.timeframe ?? form.timeframe
                    : form.timeframe,
                  symbol: value?.selectionMode === 'DATASET_UNIVERSE' ? '' : form.symbol,
                })
              }
              getOptionLabel={(option) => option.label}
              isOptionEqualToValue={(option, value) => option.id === value.id}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Algorithm"
                  error={Boolean(validation.errors.algorithmType)}
                  helperText={
                    validation.errors.algorithmType ??
                    'Determines the signal logic used in the simulation.'
                  }
                />
              )}
              renderOption={(props, option) => {
                const profile = getStrategyProfile(option.id);
                return (
                  <Box component="li" {...props}>
                    <Stack spacing={0.25}>
                      <Typography variant="body2" fontWeight={600}>
                        {option.label}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {profile?.shortDescription ?? option.description}
                      </Typography>
                    </Stack>
                  </Box>
                );
              }}
            />
          </FieldTooltip>

          {selectedAlgorithmProfile ? (
            <Alert severity={selectedAlgorithmProfile.auditTone}>
              <strong>{selectedAlgorithmProfile.title}:</strong>{' '}
              {selectedAlgorithmProfile.shortDescription} Best for:{' '}
              {selectedAlgorithmProfile.bestFor} Audit outcome: {selectedAlgorithmProfile.auditLabel}.
              {' '}Recommended action: {selectedAlgorithmProfile.operatorAction}
            </Alert>
          ) : null}

          {dispositionHeadline ? (
            <Alert severity={selectedAlgorithmProfile?.auditTone ?? 'info'}>
              {dispositionHeadline}
            </Alert>
          ) : null}

          <FieldTooltip title="Experiment labels group related runs together so multi-run research stays reviewable and repeatable.">
            <TextField
              label="Experiment Name (optional)"
              value={form.experimentName}
              onChange={(event) =>
                onChange({ ...form, experimentName: sanitizeText(event.target.value) })
              }
              helperText="Examples: Q1 Trend Rotation Review, BTC Mean Reversion Retest"
            />
          </FieldTooltip>

          <FieldTooltip title="Dataset controls what market history is replayed. Wrong dataset means misleading conclusions.">
            <Autocomplete
              options={datasets}
              value={selectedDataset}
              onChange={(_event, value) =>
                onChange({
                  ...form,
                  datasetId: value ? String(value.id) : '',
                  symbol:
                    value && !requiresDatasetUniverse
                      ? parseSymbols(value.symbolsCsv)[0] ?? ''
                      : '',
                })
              }
              getOptionLabel={(option) => option.name}
              isOptionEqualToValue={(option, value) => option.id === value.id}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label="Dataset"
                  error={Boolean(validation.errors.datasetId)}
                  helperText={
                    validation.errors.datasetId ?? 'Historical CSV dataset used for this run.'
                  }
                />
              )}
              renderOption={(props, option) => (
                <Box component="li" {...props}>
                  <Stack spacing={0.25}>
                    <Typography variant="body2" fontWeight={600}>
                      {option.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {parseSymbols(option.symbolsCsv).length} symbols | {option.rowCount} rows |{' '}
                      {option.dataStart.slice(0, 10)} to {option.dataEnd.slice(0, 10)}
                    </Typography>
                  </Stack>
                </Box>
              )}
            />
          </FieldTooltip>

          {selectedDataset ? (
            <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
              <Stack spacing={0.75}>
                <Typography variant="subtitle2">Dataset snapshot</Typography>
                <Typography variant="body2" color="text.secondary">
                  Symbols: {availableSymbols.join(', ')}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Coverage: {selectedDataset.dataStart.slice(0, 10)} to{' '}
                  {selectedDataset.dataEnd.slice(0, 10)} | {selectedDataset.rowCount} rows
                </Typography>
              </Stack>
            </Paper>
          ) : null}

          {selectedAlgorithm ? (
            <Alert severity="info">
              {requiresDatasetUniverse
                ? 'This strategy evaluates the whole dataset universe, not just one pair. No symbol override is required for submission.'
                : 'This strategy runs against one symbol from the selected dataset. Confirm the dataset first, then choose the market pair below.'}
            </Alert>
          ) : null}

          {requiresDatasetUniverse ? null : availableSymbols.length > 0 ? (
            <FieldTooltip title="Trading pair to simulate. Must match dataset coverage for meaningful results.">
              <Autocomplete
                options={availableSymbols}
                value={form.symbol || null}
                onChange={(_event, value) => onChange({ ...form, symbol: value ?? '' })}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Symbol"
                    error={Boolean(validation.errors.symbol)}
                    helperText={
                      validation.errors.symbol ?? 'Primary market pair used by the strategy.'
                    }
                  />
                )}
              />
            </FieldTooltip>
          ) : (
            <Alert severity="warning">
              This dataset does not expose any symbols that can be selected.
            </Alert>
          )}

          <FieldTooltip title="Candle interval for strategy logic. A mismatch with dataset granularity can distort metrics.">
            <TextField
              select
              label="Timeframe"
              value={form.timeframe}
              onChange={(event) => onChange({ ...form, timeframe: sanitizeText(event.target.value) })}
              error={Boolean(validation.errors.timeframe)}
              helperText={
                validation.errors.timeframe ??
                `Recommended choices for this strategy: ${timeframeOptions.join(', ')}.`
              }
              SelectProps={{ native: true }}
            >
              {timeframeOptions.map((timeframe) => (
                <option key={timeframe} value={timeframe}>
                  {timeframe}
                </option>
              ))}
            </TextField>
          </FieldTooltip>

          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {timeframeOptions.map((timeframe) => (
              <Chip
                key={timeframe}
                label={timeframe === recommendedTimeframe ? `${timeframe} recommended` : timeframe}
                color={form.timeframe === timeframe ? 'primary' : 'default'}
                variant={form.timeframe === timeframe ? 'filled' : 'outlined'}
                onClick={() => onChange({ ...form, timeframe })}
              />
            ))}
          </Stack>

          <FieldTooltip title="Backtest start boundary. Earlier start includes more market regimes.">
            <TextField
              label="Start Date"
              type="date"
              value={form.startDate}
              onChange={(event) => onChange({ ...form, startDate: event.target.value })}
              helperText="Must be earlier than end date."
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </FieldTooltip>

          <FieldTooltip title="Backtest end boundary. Very short windows can overfit conclusions.">
            <TextField
              label="End Date"
              type="date"
              value={form.endDate}
              onChange={(event) => onChange({ ...form, endDate: event.target.value })}
              helperText="Choose a window that includes normal and stressed periods."
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </FieldTooltip>

          <FieldTooltip title="Starting capital for simulation. Small values can exaggerate position-size constraints.">
            <TextField
              label="Initial Balance"
              type="number"
              value={form.initialBalance}
              onChange={(event) => onChange({ ...form, initialBalance: event.target.value })}
              error={Boolean(validation.errors.initialBalance)}
              helperText={validation.errors.initialBalance ?? 'Must be greater than 100.'}
              inputProps={{ min: 101, step: 100 }}
            />
          </FieldTooltip>

          <FieldTooltip title="Transaction fee in basis points. Understating fees inflates performance.">
            <TextField
              label="Fees (bps)"
              type="number"
              value={form.feesBps}
              onChange={(event) => onChange({ ...form, feesBps: event.target.value })}
              error={Boolean(validation.errors.feesBps)}
              helperText={
                validation.errors.feesBps ?? '1 bps = 0.01%. Keep realistic exchange costs.'
              }
              inputProps={{ min: 0, max: 200, step: 1 }}
            />
          </FieldTooltip>

          <FieldTooltip title="Execution slippage in basis points. Lower values can overstate real-world fills.">
            <TextField
              label="Slippage (bps)"
              type="number"
              value={form.slippageBps}
              onChange={(event) => onChange({ ...form, slippageBps: event.target.value })}
              error={Boolean(validation.errors.slippageBps)}
              helperText={
                validation.errors.slippageBps ?? 'Models adverse fill movement during execution.'
              }
              inputProps={{ min: 0, max: 200, step: 1 }}
            />
          </FieldTooltip>

          <Divider />

          <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
            <Stack spacing={0.75}>
              <Typography variant="subtitle2">Run summary</Typography>
              <Typography variant="body2" color="text.secondary">
                Strategy: {selectedAlgorithm?.label ?? 'Not selected'} | Dataset:{' '}
                {selectedDataset?.name ?? 'Not selected'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Market focus:{' '}
                {requiresDatasetUniverse ? 'Whole dataset universe' : form.symbol || 'Choose a symbol'}{' '}
                | Timeframe: {form.timeframe || 'Choose a timeframe'}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Capital: {form.initialBalance || '-'} | Fees/slippage: {form.feesBps || '-'} /{' '}
                {form.slippageBps || '-'} bps
              </Typography>
            </Stack>
          </Paper>

          {validation.errors.dateRange ? (
            <Alert severity="error">{validation.errors.dateRange}</Alert>
          ) : null}
          {validation.summary ? <Alert severity="error">{validation.summary}</Alert> : null}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={busy || Boolean(validation.summary)}
          onClick={() => void run()}
        >
          Run Backtest
        </Button>
      </DialogActions>
    </Dialog>
  );
}
