import Decimal from 'decimal.js';
import fc from 'fast-check';
import { describe, expect, it } from 'vitest';

import { calculatePositionSizing } from './positionSizing';

describe('risk validation and calculator properties', () => {
  it('rejects risk percentages outside [0.1, 5]', () => {
    fc.assert(
      fc.property(fc.double({ noNaN: true, noDefaultInfinity: true }), (risk) => {
        const result = calculatePositionSizing({
          accountBalance: 10000,
          riskPercent: risk,
          stopLossDistancePercent: 1,
        });
        expect(result.valid).toBe(risk >= 0.1 && risk <= 5);
      }),
      { numRuns: 200 }
    );
  });

  it('preserves BigDecimal precision for notional amount', () => {
    fc.assert(
      fc.property(
        fc.double({ min: 100, max: 100000, noNaN: true }),
        fc.double({ min: 0.1, max: 5, noNaN: true }),
        fc.double({ min: 0.1, max: 10, noNaN: true }),
        (balance, risk, stopLoss) => {
          const result = calculatePositionSizing({
            accountBalance: balance,
            riskPercent: risk,
            stopLossDistancePercent: stopLoss,
          });
          expect(result.valid).toBe(true);

          const expected = new Decimal(balance)
            .mul(new Decimal(risk).div(100))
            .div(new Decimal(stopLoss).div(100))
            .toDecimalPlaces(2)
            .toNumber();
          expect(result.notional).toBeCloseTo(expected, 2);
        }
      ),
      { numRuns: 180 }
    );
  });
});
