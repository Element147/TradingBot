import { describe, expect, it } from 'vitest';

import {
  normalizeLoginResponse,
  normalizeRefreshTokenResponse,
  normalizeUser,
} from './authContract';

describe('authContract', () => {
  it('normalizes login responses from generated transport shapes', () => {
    const response = normalizeLoginResponse({
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      expiresIn: 3600,
      user: {
        id: 7,
        username: 'alice',
        email: 'alice@example.com',
        role: 'ADMIN',
      },
    });

    expect(response).toEqual({
      token: 'access-token',
      refreshToken: 'refresh-token',
      expiresIn: 3600,
      user: {
        id: '7',
        username: 'alice',
        email: 'alice@example.com',
        role: 'admin',
      },
    });
  });

  it('fails fast when a login response is missing an access token', () => {
    expect(() =>
      normalizeLoginResponse({
        expiresIn: 3600,
        user: {
          id: 1,
          username: 'missing-token',
          role: 'TRADER',
        },
      })
    ).toThrow(/usable access token/i);
  });

  it('normalizes refresh responses and current-user payloads', () => {
    expect(
      normalizeRefreshTokenResponse({
        token: 'refreshed-token',
        expiresIn: 1800,
      })
    ).toEqual({
      token: 'refreshed-token',
      expiresIn: 1800,
    });

    expect(
      normalizeUser({
        id: '9',
        username: 'bob',
        role: 'TRADER',
      })
    ).toEqual({
      id: '9',
      username: 'bob',
      email: '',
      role: 'trader',
    });
  });
});
