import { createApi } from '@reduxjs/toolkit/query/react';

import type { LoginRequest, LoginResponse, RefreshTokenResponse } from './authContract';
import {
  normalizeLoginResponse,
  normalizeRefreshTokenResponse,
  normalizeUser,
  toRefreshTokenRequest,
} from './authContract';
import type { User } from './authSlice';
import { getStoredRefreshToken } from './authStorage';

import { baseQueryWithEnvironment } from '@/services/api';

export type { LoginRequest, LoginResponse, RefreshTokenResponse } from './authContract';

export const authApi = createApi({
  reducerPath: 'authApi',
  baseQuery: baseQueryWithEnvironment,
  tagTypes: ['Auth'],
  endpoints: (builder) => ({
    login: builder.mutation<LoginResponse, LoginRequest>({
      query: (credentials) => ({
        url: '/api/auth/login',
        method: 'POST',
        body: credentials,
      }),
      transformResponse: normalizeLoginResponse,
      invalidatesTags: ['Auth'],
    }),

    logout: builder.mutation<void, void>({
      query: () => ({
        url: '/api/auth/logout',
        method: 'POST',
      }),
      invalidatesTags: ['Auth'],
    }),

    refreshToken: builder.mutation<RefreshTokenResponse, void>({
      query: () => {
        const refreshToken = getStoredRefreshToken();

        return {
          url: '/api/auth/refresh',
          method: 'POST',
          body: refreshToken ? toRefreshTokenRequest(refreshToken) : undefined,
        };
      },
      transformResponse: normalizeRefreshTokenResponse,
    }),

    getMe: builder.query<User, void>({
      query: () => '/api/auth/me',
      transformResponse: normalizeUser,
      providesTags: ['Auth'],
    }),
  }),
});

export const { useLoginMutation, useLogoutMutation, useRefreshTokenMutation, useGetMeQuery } =
  authApi;
