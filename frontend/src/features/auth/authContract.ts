import { z } from 'zod';

import type { User } from './authSlice';

import type { ApiRequestBody, ApiResponse } from '@/services/openapi';

type RawAuthUser = ApiResponse<'/api/auth/me', 'get'>;
type RawLoginResponse = ApiResponse<'/api/auth/login', 'post'>;
type RawRefreshTokenRequest = ApiRequestBody<'/api/auth/refresh', 'post'>;
type RawRefreshTokenResponse = ApiResponse<'/api/auth/refresh', 'post'>;

export type LoginRequest = ApiRequestBody<'/api/auth/login', 'post'>;

export interface LoginResponse {
  token: string;
  refreshToken?: string;
  user: User;
  expiresIn: number;
}

export interface RefreshTokenResponse {
  token: string;
  expiresIn: number;
}

const authUserSchema = z.object({
  id: z.union([z.string(), z.number()]),
  username: z.string().min(1),
  email: z.string().optional().nullable(),
  role: z.string().min(1),
});

const loginResponseSchema = z.object({
  token: z.string().min(1).optional(),
  accessToken: z.string().min(1).optional(),
  refreshToken: z.string().min(1).optional(),
  user: authUserSchema,
  expiresIn: z.number(),
});

const refreshTokenResponseSchema = z.object({
  token: z.string().min(1).optional(),
  accessToken: z.string().min(1).optional(),
  expiresIn: z.number(),
});

const normalizeRole = (role: string): User['role'] =>
  role.toLowerCase() === 'admin' ? 'admin' : 'trader';

const resolveToken = (token?: string, accessToken?: string): string => {
  const resolved = token ?? accessToken;

  if (!resolved) {
    throw new Error('Authentication response did not include a usable access token');
  }

  return resolved;
};

export const normalizeUser = (user: RawAuthUser): User => {
  const parsed = authUserSchema.parse(user);

  return {
    id: String(parsed.id),
    username: parsed.username,
    email: parsed.email ?? '',
    role: normalizeRole(parsed.role),
  };
};

export const normalizeLoginResponse = (response: RawLoginResponse): LoginResponse => {
  const parsed = loginResponseSchema.parse(response);

  return {
    token: resolveToken(parsed.token, parsed.accessToken),
    refreshToken: parsed.refreshToken,
    user: normalizeUser(parsed.user),
    expiresIn: parsed.expiresIn,
  };
};

export const normalizeRefreshTokenResponse = (
  response: RawRefreshTokenResponse
): RefreshTokenResponse => {
  const parsed = refreshTokenResponseSchema.parse(response);

  return {
    token: resolveToken(parsed.token, parsed.accessToken),
    expiresIn: parsed.expiresIn,
  };
};

export const toRefreshTokenRequest = (refreshToken: string): RawRefreshTokenRequest => ({
  refreshToken,
});
