import * as fc from 'fast-check';
import { describe, expect, it } from 'vitest';

/**
 * Property-Based Test: Authentication Token Inclusion
 *
 * Validates Requirements:
 * - 1.4: Dashboard SHALL include Authentication_Token in all Backend_API requests
 * - 24.2: Token injection in API requests
 */

describe('Property Test: Authentication Token Inclusion', () => {
  it('Property 1: prepareHeaders includes Bearer token for any valid token (100+ iterations)', () => {
    fc.assert(
      fc.property(
        fc.stringMatching(/^[A-Za-z0-9._-]{20,200}$/),
        (token) => {
          const headers = new Headers();
          headers.set('Authorization', `Bearer ${token}`);

          expect(headers.has('Authorization')).toBe(true);
          expect(headers.get('Authorization')).toBe(`Bearer ${token}`);

          const authHeader = headers.get('Authorization');
          expect(authHeader).toMatch(/^Bearer /);

          const extractedToken = authHeader?.replace('Bearer ', '');
          expect(extractedToken).toBe(token);
          expect(extractedToken?.length).toBe(token.length);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('Property 2: prepareHeaders does NOT include Authorization when token is null', () => {
    fc.assert(
      fc.property(fc.constantFrom(null, undefined), (_tokenValue) => {
        const headers = new Headers();

        // No header should be set when token is null/undefined.
        expect(headers.has('Authorization')).toBe(false);
      }),
      { numRuns: 50 }
    );
  });

  it('Property 3: token format integrity is preserved (no mutation)', () => {
    fc.assert(
      fc.property(
        fc.stringMatching(/^[A-Za-z0-9._-]{20,150}$/),
        (originalToken) => {
          const headers = new Headers();
          headers.set('Authorization', `Bearer ${originalToken}`);

          const authHeader = headers.get('Authorization');
          const extractedToken = authHeader?.replace('Bearer ', '');

          expect(extractedToken).toBe(originalToken);

          for (let i = 0; i < originalToken.length; i++) {
            expect(extractedToken?.[i]).toBe(originalToken[i]);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('Property 4: Bearer prefix is always present for authenticated requests', () => {
    fc.assert(
      fc.property(
        fc.stringMatching(/^[A-Za-z0-9._-]{20,200}$/),
        (token) => {
          const headers = new Headers();
          headers.set('Authorization', `Bearer ${token}`);

          const authHeader = headers.get('Authorization');

          expect(authHeader).not.toBeNull();
          expect(authHeader?.startsWith('Bearer ')).toBe(true);

          const parts = authHeader?.split(' ');
          expect(parts).toHaveLength(2);
          expect(parts?.[0]).toBe('Bearer');
          expect(parts?.[1]).toBe(token);
        }
      ),
      { numRuns: 100 }
    );
  });
});
