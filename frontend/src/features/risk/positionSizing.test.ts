import { describe, expect, it } from 'vitest';

import { calculatePositionSizing } from './positionSizing';

describe('calculatePositionSizing', () => {
  it('computes notional and units with valid inputs', () => {
    const result = calculatePositionSizing({
      accountBalance: 10000,
      riskPercent: 2,
      stopLossDistancePercent: 1,
    });

    expect(result.valid).toBe(true);
    expect(result.notional).toBe(20000);
  });

  it('rejects invalid risk percent', () => {
    const result = calculatePositionSizing({
      accountBalance: 10000,
      riskPercent: 10,
      stopLossDistancePercent: 1,
    });

    expect(result.valid).toBe(false);
  });
});
