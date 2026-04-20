import type { RunBacktestPayload } from './backtestApi';
import type { BacktestSelectionMode } from './backtestTypes';

export interface BacktestConfigFormState {
  algorithmType: string;
  datasetId: string;
  experimentName: string;
  symbol: string;
  timeframe: string;
  startDate: string;
  endDate: string;
  initialBalance: string;
  feesBps: string;
  slippageBps: string;
}

export const buildRunBacktestPayload = (
  form: BacktestConfigFormState,
  selectionMode: BacktestSelectionMode
): RunBacktestPayload => ({
  algorithmType: form.algorithmType,
  datasetId: Number(form.datasetId),
  experimentName: form.experimentName.trim() || undefined,
  symbol:
    selectionMode === 'DATASET_UNIVERSE'
      ? undefined
      : form.symbol.trim() || undefined,
  timeframe: form.timeframe,
  startDate: form.startDate,
  endDate: form.endDate,
  initialBalance: Number(form.initialBalance),
  feesBps: Number(form.feesBps),
  slippageBps: Number(form.slippageBps),
});
