import { describe, expect, it } from 'vitest';

import {
  normalizeBacktestAlgorithms,
  normalizeBacktestRunSubmission,
  toRunBacktestRequest,
} from './backtestContract';

describe('backtestContract', () => {
  it('omits blank optional transport fields when building a run request', () => {
    expect(
      toRunBacktestRequest({
        algorithmType: 'BUY_AND_HOLD',
        datasetId: 7,
        symbol: '   ',
        timeframe: '1h',
        startDate: '2025-01-01',
        endDate: '2025-01-02',
        initialBalance: 1000,
        feesBps: 10,
        slippageBps: 3,
        experimentName: '  Smoke test  ',
      })
    ).toEqual({
      algorithmType: 'BUY_AND_HOLD',
      datasetId: 7,
      timeframe: '1h',
      startDate: '2025-01-01',
      endDate: '2025-01-02',
      initialBalance: 1000,
      feesBps: 10,
      slippageBps: 3,
      experimentName: 'Smoke test',
    });
  });

  it('rejects algorithm payloads that drift from the generated contract boundary', () => {
    expect(() =>
      normalizeBacktestAlgorithms([
        {
          id: 'BUY_AND_HOLD',
          label: 'Buy and Hold',
          description: 'Benchmark strategy',
        },
      ])
    ).toThrow();
  });

  it('requires submittedAt in normalized run submissions', () => {
    expect(() =>
      normalizeBacktestRunSubmission({
        id: 43,
        status: 'PENDING',
      })
    ).toThrow();
  });
});
