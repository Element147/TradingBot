import type { RunBacktestPayload } from './backtestApi';

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
  availableAlgorithms: any,
  availableDatasets: { id: number; symbolsCsv: string }[],
  allTimeframes: string[]
): RunBacktestPayload[] => {
  const payloads: RunBacktestPayload[] = [];
  
  let algorithms: { id: string; selectionMode?: any }[] = [];
  if (typeof availableAlgorithms === 'string') {
    algorithms = [{ id: form.algorithmType, selectionMode: availableAlgorithms }];
  } else if (form.algorithmType === 'ALL_ALGORITHMS') {
    algorithms = availableAlgorithms;
  } else {
    algorithms = availableAlgorithms.filter((a: any) => a.id === form.algorithmType);
    if (algorithms.length === 0) {
      algorithms = [{ id: form.algorithmType }];
    }
  }

  const datasetIds = form.datasetId === 'ALL_DATASETS' 
    ? availableDatasets.map(d => d.id) 
    : [Number(form.datasetId)];

  const timeframes = form.timeframe === 'ALL_TIMEFRAMES'
    ? allTimeframes
    : [form.timeframe];

  for (const algo of algorithms) {
    for (const dsId of datasetIds) {
      const ds = availableDatasets.find(d => d.id === dsId);
      if (!ds) continue;

      const availableSymbols = ds.symbolsCsv.split(',').map(s => s.trim()).filter(s => s.length > 0);
      const symbols = algo.selectionMode === 'DATASET_UNIVERSE'
        ? [undefined]
        : form.symbol === 'ALL_SYMBOLS'
          ? availableSymbols
          : [form.symbol.trim() || undefined];

      for (const sym of symbols) {
        for (const tf of timeframes) {
          payloads.push({
            algorithmType: algo.id,
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
  }

  return payloads;
};
