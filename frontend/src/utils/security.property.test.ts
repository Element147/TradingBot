import fc from 'fast-check';
import { describe, expect, it } from 'vitest';

import { sanitizeText } from './security';

describe('security sanitization properties', () => {
  it('removes executable script vectors from arbitrary input', () => {
    fc.assert(
      fc.property(fc.string(), (input) => {
        const sanitized = sanitizeText(input);
        expect(sanitized.toLowerCase()).not.toContain('<script');
        expect(sanitized.toLowerCase()).not.toContain('javascript:');
        expect(sanitized.toLowerCase()).not.toContain('onerror=');
        expect(sanitized.toLowerCase()).not.toContain('onload=');
      }),
      { numRuns: 220 }
    );
  });
});
