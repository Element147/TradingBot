import fc from 'fast-check';
import { describe, expect, it } from 'vitest';

import { isValidPositionSize, isValidRiskPercentage } from './strategyValidation';

describe('strategy validation properties', () => {
  it('accepts risk percentages only within [0.01, 0.05]', () => {
    fc.assert(
      fc.property(fc.double({ noNaN: true, noDefaultInfinity: true }), (risk) => {
        const expected = risk >= 0.01 && risk <= 0.05;
        expect(isValidRiskPercentage(risk)).toBe(expected);
      }),
      { numRuns: 250 }
    );
  });

  it('accepts position size only for positive values', () => {
    fc.assert(
      fc.property(fc.double({ noNaN: true, noDefaultInfinity: true }), (size) => {
        expect(isValidPositionSize(size)).toBe(size > 0);
      }),
      { numRuns: 250 }
    );
  });
});
