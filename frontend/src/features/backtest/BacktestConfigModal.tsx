import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import { useEffect, useMemo, useState } from 'react';

import type { BacktestAlgorithm, BacktestDataset, RunBacktestPayload } from './backtestApi';
import {
  buildRunBacktestPayloads,
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
  onRun: (payloads: RunBacktestPayload[]) => Promise<void> | void;
  onRunSweep?: (payload: RunBacktestPayload) => Promise<void> | void;
}

const parseSymbols = (symbolsCsv: string): string[] =>
  symbolsCsv
    .split(',')
    .map((symbol) => symbol.trim())
    .filter((symbol) => symbol.length > 0);

const COMMON_TIMEFRAMES = ['15m', '1h', '4h', '1d'];
const ALL_TIMEFRAMES_LIST = ['1m', '5m', '10m', '15m', '30m', '1h', '4h', '1d'];

export function BacktestConfigModal({
  open,
  form,
  algorithms,
  datasets,
  busy,
  onChange,
  onClose,
  onRun,
  onRunSweep,
}: BacktestConfigModalProps) {
  const [isSweepChecked, setIsSweepChecked] = useState(false);

  const selectedAlgorithm = useMemo(
    () => form.algorithmType === 'ALL_ALGORITHMS' ? ({ id: 'ALL_ALGORITHMS', label: 'All Strategies', description: 'Run backtests for all available strategies', selectionMode: 'MIXED' } as unknown as BacktestAlgorithm) : (algorithms.find((algorithm) => algorithm.id === form.algorithmType) ?? null),
    [algorithms, form.algorithmType]
  );

  const hasParameterGrid = useMemo(() => {
    return Boolean(
      selectedAlgorithm &&
      selectedAlgorithm.parameterGrid &&
      Object.keys(selectedAlgorithm.parameterGrid).length > 0
    );
  }, [selectedAlgorithm]);

  const isSweepActive = hasParameterGrid && isSweepChecked;

  const sweepCombinations = useMemo(() => {
    if (!selectedAlgorithm?.parameterGrid) return 0;
    const arrays = Object.values(selectedAlgorithm.parameterGrid);
    if (arrays.length === 0) return 0;
    return arrays.reduce((acc, arr) => acc * (arr?.length || 1), 1);
  }, [selectedAlgorithm]);

  useEffect(() => {
    if (!hasParameterGrid) {
      setIsSweepChecked(false);
    }
  }, [hasParameterGrid]);
  
  const algorithmOptions = useMemo(() => [
    { id: 'ALL_ALGORITHMS', label: 'All Strategies', description: 'Run backtests for all available strategies', selectionMode: 'MIXED' } as unknown as BacktestAlgorithm,
    ...algorithms,
  ], [algorithms]);

  const isAllDatasets = form.datasetId === 'ALL_DATASETS';
  const selectedDataset = useMemo(
    () => isAllDatasets ? { id: 'ALL_DATASETS', name: 'All Active Datasets', symbolsCsv: '', rowCount: 0, dataStart: '', dataEnd: '' } as unknown as BacktestDataset : (datasets.find((dataset) => String(dataset.id) === form.datasetId) ?? null),
    [datasets, form.datasetId, isAllDatasets]
  );
  const datasetOptions = useMemo(() => [
    { id: 'ALL_DATASETS', name: 'All Active Datasets', symbolsCsv: '', rowCount: 0, dataStart: '', dataEnd: '' } as unknown as BacktestDataset,
    ...datasets,
  ], [datasets]);
  const availableSymbols = useMemo(() => {
    if (isAllDatasets) {
      const allSyms = new Set<string>();
      datasets.forEach(d => parseSymbols(d.symbolsCsv).forEach(s => allSyms.add(s)));
      return Array.from(allSyms);
    }
    return selectedDataset && !isAllDatasets ? parseSymbols(selectedDataset.symbolsCsv) : [];
  }, [isAllDatasets, datasets, selectedDataset]);

  const symbolOptions = useMemo(() => [
    'ALL_SYMBOLS',
    ...availableSymbols,
  ], [availableSymbols]);

  const requiresDatasetUniverse = selectedAlgorithm?.selectionMode === 'DATASET_UNIVERSE';
  const selectedAlgorithmProfile = useMemo(
    () => getStrategyProfile(form.algorithmType),
    [form.algorithmType]
  );
  const timeframeOptions = selectedAlgorithmProfile?.timeframeOptions ?? COMMON_TIMEFRAMES;
  const timeframeDropdownOptions = useMemo(() => [
    'ALL_TIMEFRAMES',
    ...ALL_TIMEFRAMES_LIST,
  ], []);
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
    const errors: Partial<Record<keyof BacktestConfigFormState | 'dateRange' | 'sweep', string>> = {};

    if (!form.algorithmType.trim()) {
      errors.algorithmType = 'Choose a strategy before starting a run.';
    }

    if (!form.datasetId) {
      errors.datasetId = 'Please choose a dataset first.';
    } else if (!isAllDatasets && !selectedDataset) {
      errors.datasetId = 'The selected dataset is no longer available. Pick another one.';
    }

    if (!requiresDatasetUniverse) {
      if (!form.symbol.trim()) {
        errors.symbol = 'Please choose one symbol from the selected dataset.';
      } else if (form.symbol !== 'ALL_SYMBOLS' && !availableSymbols.includes(form.symbol)) {
        errors.symbol = 'The selected symbol is not available in this dataset.';
      }
    }

    if (!form.timeframe.trim()) {
      errors.timeframe = 'Choose a timeframe.';
    } else if (form.timeframe !== 'ALL_TIMEFRAMES' && !ALL_TIMEFRAMES_LIST.includes(form.timeframe)) {
      errors.timeframe = `Choose one of the supported timeframes for this strategy: ${ALL_TIMEFRAMES_LIST.join(', ')}.`;
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

    if (isSweepActive) {
      if (form.datasetId === 'ALL_DATASETS') {
        errors.datasetId = 'Bounded parameter sweep requires selecting a specific single dataset.';
      }
      if (form.symbol === 'ALL_SYMBOLS') {
        errors.symbol = 'Bounded parameter sweep requires selecting a specific single symbol.';
      }
      if (form.timeframe === 'ALL_TIMEFRAMES') {
        errors.timeframe = 'Bounded parameter sweep requires selecting a specific single timeframe.';
      }
      if (sweepCombinations > 100) {
        errors.sweep = `Cartesian sweep combinations (${sweepCombinations}) exceed the safety cap of 100. Please reduce parameter grid bounds on the backend.`;
      }
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
        errors.sweep ??
        null,
    };
  }, [availableSymbols, form, requiresDatasetUniverse, selectedDataset, timeframeOptions, isAllDatasets, isSweepActive, sweepCombinations]);

  const combinationsCount = useMemo(() => {
    if (isSweepActive) {
      return sweepCombinations;
    }
    try {
      return buildRunBacktestPayloads(
        form,
        algorithms,
        datasets,
        ALL_TIMEFRAMES_LIST
      ).length;
    } catch {
      return 0;
    }
  }, [form, algorithms, datasets, isSweepActive, sweepCombinations]);

  const run = async () => {
    if (validation.summary) {
      return;
    }

    if (isSweepActive && onRunSweep) {
      const payload: RunBacktestPayload = {
        algorithmType: form.algorithmType,
        datasetId: Number(form.datasetId),
        symbol: requiresDatasetUniverse ? undefined : (form.symbol || undefined),
        timeframe: form.timeframe,
        startDate: form.startDate,
        endDate: form.endDate,
        initialBalance: Number(form.initialBalance),
        feesBps: Number(form.feesBps),
        slippageBps: Number(form.slippageBps),
        experimentName: form.experimentName.trim() || undefined,
      };
      await onRunSweep(payload);
    } else {
      const payloads = buildRunBacktestPayloads(
        form,
        algorithms,
        datasets,
        ALL_TIMEFRAMES_LIST
      );
      await onRun(payloads);
    }
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
              options={algorithmOptions}
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

          {hasParameterGrid ? (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={isSweepChecked}
                    onChange={(e) => setIsSweepChecked(e.target.checked)}
                    color="primary"
                  />
                }
                label={
                  <Typography variant="body1" fontWeight={700}>
                    Trigger Bounded Parameter Sweep
                  </Typography>
                }
              />
              {isSweepChecked ? (
                <Paper
                  variant="outlined"
                  sx={{
                    p: 3,
                    borderRadius: 3,
                    backgroundColor: (theme) => alpha(theme.palette.primary.main, 0.02),
                    borderColor: (theme) => alpha(theme.palette.primary.main, 0.16),
                    borderWidth: 1.5,
                  }}
                >
                  <Stack spacing={2.5}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                      <Typography variant="subtitle2" color="primary.main" sx={{ textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                        Active Sweep Parameters
                      </Typography>
                      {sweepCombinations > 100 ? (
                        <Chip
                          label={`Limit Exceeded: ${sweepCombinations}/100`}
                          color="error"
                          variant="outlined"
                          size="small"
                        />
                      ) : (
                        <Chip
                          label={`Sequential Queue: ${sweepCombinations} runs`}
                          color="success"
                          variant="outlined"
                          size="small"
                        />
                      )}
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      This strategy defines a Cartesian grid managed securely on the backend. Ad-hoc client-side parameter fishing is blocked to prevent overfit strategies.
                    </Typography>
                    
                    <Divider />

                    <Stack spacing={2}>
                      {Object.entries(selectedAlgorithm?.parameterGrid || {}).map(([key, values]) => (
                        <Stack key={key} direction="row" spacing={3} alignItems="flex-start">
                          <Typography
                            variant="body2"
                            sx={{
                              fontFamily: 'monospace',
                              fontWeight: 700,
                              minWidth: 120,
                              pt: 0.5,
                              color: 'text.primary',
                            }}
                          >
                            {key}
                          </Typography>
                          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                            {values.map((val) => (
                              <Chip
                                key={val}
                                label={val}
                                size="small"
                                sx={{
                                  fontFamily: 'monospace',
                                  fontSize: '0.75rem',
                                  backgroundColor: (theme) => alpha(theme.palette.text.primary, 0.05),
                                  borderRadius: 1,
                                }}
                              />
                            ))}
                          </Stack>
                        </Stack>
                      ))}
                    </Stack>

                    {sweepCombinations > 100 ? (
                      <Alert severity="error" sx={{ mt: 1 }}>
                        Safety cap exceeded. This parameter sweep is locked. Please request a narrower strategy parameter grid from backend governance.
                      </Alert>
                    ) : (
                      <Alert severity="info" sx={{ mt: 1 }}>
                        Runs will execute sequentially under the single-threaded task manager to preserve system stability.
                      </Alert>
                    )}
                  </Stack>
                </Paper>
              ) : null}
            </Stack>
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
              options={datasetOptions}
              value={selectedDataset}
              onChange={(_event, value) =>
                onChange({
                  ...form,
                  datasetId: value ? String(value.id) : '',
                  symbol:
                    value && !requiresDatasetUniverse
                      ? (String(value.id) === 'ALL_DATASETS' ? 'ALL_SYMBOLS' : (parseSymbols(value.symbolsCsv)[0] ?? ''))
                      : '',
                })
              }
              getOptionLabel={(option) => option.name}
              isOptionEqualToValue={(option, value) => String(option.id) === String(value.id)}
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
                    {String(option.id) !== 'ALL_DATASETS' ? (
                      <Typography variant="caption" color="text.secondary">
                        {parseSymbols(option.symbolsCsv).length} symbols | {option.rowCount} rows |{' '}
                        {option.dataStart.slice(0, 10)} to {option.dataEnd.slice(0, 10)}
                      </Typography>
                    ) : null}
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
                options={symbolOptions}
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
                (form.algorithmType === 'ALL_ALGORITHMS' ? 'Recommended timeframes vary by strategy.' : `Recommended choices for this strategy: ${timeframeOptions.join(', ')}.`)
              }
              SelectProps={{ native: true }}
            >
              {timeframeDropdownOptions.map((timeframe) => (
                <option key={timeframe} value={timeframe}>
                  {timeframe === 'ALL_TIMEFRAMES' ? 'All Recommended Timeframes' : timeframe}
                </option>
              ))}
            </TextField>
          </FieldTooltip>

          <Typography variant="body2" color="text.secondary">
            {form.algorithmType === 'ALL_ALGORITHMS' ? 'Recommended timeframes vary by strategy. All chosen timeframes will be evaluated.' : `Recommended timeframes for this strategy: ${timeframeOptions.join(', ')}`}
          </Typography>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {timeframeDropdownOptions.map((timeframe) => {
              const isRecommended = timeframe !== 'ALL_TIMEFRAMES' && timeframeOptions.includes(timeframe);
              return (
                <Chip
                  key={timeframe}
                  label={timeframe === 'ALL_TIMEFRAMES' ? 'All Timeframes' : (isRecommended && form.algorithmType !== 'ALL_ALGORITHMS' ? `${timeframe} recommended` : timeframe)}
                  color={form.timeframe === timeframe ? 'primary' : 'default'}
                  variant={form.timeframe === timeframe ? 'filled' : 'outlined'}
                  onClick={() => onChange({ ...form, timeframe })}
                />
              );
            })}
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
                {isAllDatasets ? 'All Active Datasets' : (selectedDataset?.name ?? 'Not selected')}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Market focus:{' '}
                {requiresDatasetUniverse ? 'Whole dataset universe' : (form.symbol === 'ALL_SYMBOLS' ? 'All available symbols' : (form.symbol || 'Choose a symbol'))}{' '}
                | Timeframe: {form.timeframe === 'ALL_TIMEFRAMES' ? 'All Timeframes' : (form.timeframe || 'Choose a timeframe')}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Capital: {form.initialBalance || '-'} | Fees/slippage: {form.feesBps || '-'} /{' '}
                {form.slippageBps || '-'} bps
              </Typography>
              <Typography variant="body2" fontWeight={700} color="primary.main" sx={{ mt: 1 }}>
                Combinations to run: {combinationsCount}
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
          disabled={busy || Boolean(validation.summary) || combinationsCount === 0}
          onClick={() => void run()}
        >
          {isSweepActive
            ? `Run Parameter Sweep (${combinationsCount} runs)`
            : combinationsCount > 1
              ? `Run ${combinationsCount} Backtests`
              : 'Run Backtest'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
