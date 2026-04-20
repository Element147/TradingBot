import { describe, expect, it } from 'vitest';

import { buildRunBacktestPayload, type BacktestConfigFormState } from './backtestConfigForm';

const baseForm: BacktestConfigFormState = {
  algorithmType: 'BUY_AND_HOLD',
  datasetId: '7',
  experimentName: 'Universe review',
  symbol: 'BTC/USDT',
  timeframe: '1h',
  startDate: '2025-01-01',
  endDate: '2025-01-02',
  initialBalance: '1000',
  feesBps: '10',
  slippageBps: '3',
};

describe('buildRunBacktestPayload', () => {
  it('keeps symbol for single-symbol strategies', () => {
    expect(buildRunBacktestPayload(baseForm, 'SINGLE_SYMBOL')).toMatchObject({
      symbol: 'BTC/USDT',
    });
  });

  it('omits symbol for dataset-universe strategies', () => {
    expect(buildRunBacktestPayload(baseForm, 'DATASET_UNIVERSE')).toMatchObject({
      algorithmType: 'BUY_AND_HOLD',
      datasetId: 7,
      timeframe: '1h',
    });
    expect(buildRunBacktestPayload(baseForm, 'DATASET_UNIVERSE').symbol).toBeUndefined();
  });
});
