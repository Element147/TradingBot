import { describe, expect, it } from 'vitest';

import { buildRunBacktestPayloads, type BacktestConfigFormState } from './backtestConfigForm';

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

const datasets = [{ id: 7, symbolsCsv: 'BTC/USDT, ETH/USDT' }];
const timeframeOptions = ['15m', '1h', '4h', '1d'];

describe('buildRunBacktestPayloads', () => {
  it('keeps symbol for single-symbol strategies', () => {
    const payloads = buildRunBacktestPayloads(baseForm, 'SINGLE_SYMBOL', datasets, timeframeOptions);
    expect(payloads).toHaveLength(1);
    expect(payloads[0]).toMatchObject({
      symbol: 'BTC/USDT',
    });
  });

  it('omits symbol for dataset-universe strategies', () => {
    const payloads = buildRunBacktestPayloads(baseForm, 'DATASET_UNIVERSE', datasets, timeframeOptions);
    expect(payloads).toHaveLength(1);
    expect(payloads[0]).toMatchObject({
      algorithmType: 'BUY_AND_HOLD',
      datasetId: 7,
      timeframe: '1h',
    });
    expect(payloads[0].symbol).toBeUndefined();
  });

  it('generates combinations for ALL_SYMBOLS', () => {
    const form = { ...baseForm, symbol: 'ALL_SYMBOLS' };
    const payloads = buildRunBacktestPayloads(form, 'SINGLE_SYMBOL', datasets, timeframeOptions);
    expect(payloads).toHaveLength(2);
    expect(payloads[0].symbol).toBe('BTC/USDT');
    expect(payloads[1].symbol).toBe('ETH/USDT');
  });
});
