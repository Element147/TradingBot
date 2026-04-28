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

export const buildRunBacktestPayloads = (
  form: BacktestConfigFormState,
  selectionMode: BacktestSelectionMode,
  availableDatasets: { id: number; symbolsCsv: string }[],
  timeframeOptions: string[]
): RunBacktestPayload[] => {
  const payloads: RunBacktestPayload[] = [];
  
  const datasetIds = form.datasetId === 'ALL_DATASETS' 
    ? availableDatasets.map(d => d.id) 
    : [Number(form.datasetId)];

  const timeframes = form.timeframe === 'ALL_TIMEFRAMES'
    ? timeframeOptions
    : [form.timeframe];

  for (const dsId of datasetIds) {
    const ds = availableDatasets.find(d => d.id === dsId);
    if (!ds) continue;

    const availableSymbols = ds.symbolsCsv.split(',').map(s => s.trim()).filter(s => s.length > 0);
    const symbols = selectionMode === 'DATASET_UNIVERSE'
      ? [undefined]
      : form.symbol === 'ALL_SYMBOLS'
        ? availableSymbols
        : [form.symbol.trim() || undefined];

    for (const sym of symbols) {
      for (const tf of timeframes) {
        payloads.push({
          algorithmType: form.algorithmType,
          datasetId: dsId,
          experimentName: form.experimentName.trim() || undefined,
          symbol: sym,
          timeframe: tf,
          startDate: form.startDate,
          endDate: form.endDate,
          initialBalance: Number(form.initialBalance),
          feesBps: Number(form.feesBps),
          slippageBps: Number(form.slippageBps),
        });
      }
    }
  }

  return payloads;
};
