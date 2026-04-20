import type { SerializedError } from '@reduxjs/toolkit';
import { fetchBaseQuery } from '@reduxjs/toolkit/query';
import type { BaseQueryFn, FetchArgs, FetchBaseQueryError } from '@reduxjs/toolkit/query';
import { Mutex } from 'async-mutex';

import type { RootState } from '../app/store';

import { setToken, logout } from '@/features/auth/authSlice';
import { getStoredRefreshToken, redirectToLogin } from '@/features/auth/authStorage';
import {
  resolveExecutionEnvironment,
  type ExecutionContext,
} from '@/features/execution/executionContext';
import { getOrCreateCsrfToken } from '@/utils/security';

// Mutex to prevent multiple simultaneous refresh attempts
const mutex = new Mutex();

/**
 * Base query configuration for RTK Query with environment injection
 * 
 * Features:
 * - Automatic authentication token injection
 * - Environment mode header injection (test/live)
 * - Retry logic for failed requests
 * - Error handling
 */
const baseQuery = fetchBaseQuery({
  baseUrl: (() => {
    const configured = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
    if (import.meta.env.PROD && configured.startsWith('http://')) {
      return configured.replace('http://', 'https://');
    }
    return configured;
  })(),
  prepareHeaders: (headers, context) => {
    const { getState, arg } = context;
    const state = getState() as RootState;
    const requestedExecutionContext =
      typeof arg === 'string'
        ? null
        : getHeaderValue(arg.headers, 'X-Execution-Context');
    
    // Add authentication token if available
    // Note: authSlice will be implemented in task 1.4
    const token = state.auth?.token;
    if (token) {
      headers.set('Authorization', `Bearer ${token}`);
    }
    
    // Add environment mode header (test/live)
    // Note: environmentSlice will be implemented in task 2.3
    const requestedEnvironment =
      typeof arg === 'string'
        ? null
        : getHeaderValue(arg.headers, 'X-Environment');
    const environment = requestedExecutionContext
      ? resolveExecutionEnvironment(requestedExecutionContext as ExecutionContext)
      : requestedEnvironment ?? state.environment?.mode ?? 'test';
    headers.set('X-Environment', environment);
    if (requestedExecutionContext) {
      headers.set('X-Execution-Context', requestedExecutionContext);
    }
    
    const method =
      typeof arg === 'string'
        ? 'GET'
        : (arg.method?.toUpperCase() ?? 'GET');
    const body = typeof arg === 'string' ? undefined : arg.body;
    const isMutationMethod = ['POST', 'PUT', 'PATCH', 'DELETE'].includes(method);

    if (isMutationMethod) {
      headers.set('X-CSRF-Token', getOrCreateCsrfToken());
      headers.set('X-Requested-With', 'XMLHttpRequest');
    }

    // Add content type for JSON requests
    if (!headers.has('Content-Type') && !(body instanceof FormData)) {
      headers.set('Content-Type', 'application/json');
    }
    
    return headers;
  },
  // Credentials for CORS
  credentials: 'include',
});

const getHeaderValue = (headers: FetchArgs['headers'], headerName: string): string | null => {
  if (!headers) {
    return null;
  }

  if (headers instanceof Headers) {
    return headers.get(headerName) ?? headers.get(headerName.toLowerCase());
  }

  if (Array.isArray(headers)) {
    const matchedHeader = headers.find(([name]) => name.toLowerCase() === headerName.toLowerCase());
    return matchedHeader ? matchedHeader[1] : null;
  }

  return headers[headerName] ?? headers[headerName.toLowerCase()] ?? null;
};

export type EnvironmentOverride = 'test' | 'live';
export type ExecutionContextOverride = ExecutionContext;

export const withEnvironmentMode = (
  request: string | FetchArgs,
  environment: EnvironmentOverride
): FetchArgs => {
  const headers = new Headers(
    typeof request === 'string' ? undefined : (request.headers as HeadersInit | undefined)
  );
  headers.set('X-Environment', environment);

  if (typeof request === 'string') {
    return { url: request, headers };
  }

  return {
    ...request,
    headers,
  };
};

export const withExecutionContext = (
  request: string | FetchArgs,
  executionContext: ExecutionContextOverride
): FetchArgs => {
  const headers = new Headers(
    typeof request === 'string' ? undefined : (request.headers as HeadersInit | undefined)
  );
  headers.set('X-Execution-Context', executionContext);
  headers.set('X-Environment', resolveExecutionEnvironment(executionContext));

  if (typeof request === 'string') {
    return { url: request, headers };
  }

  return {
    ...request,
    headers,
  };
};

/**
 * Base query with automatic retry logic
 * 
 * Retry behavior:
 * - Retries on network errors (no response)
 * - Retries on 5xx server errors (3 attempts)
 * - Does NOT retry on 4xx client errors
 * - Exponential backoff: 1s, 2s, 4s
 */
export const baseQueryWithRetry: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  const maxRetries = 3;
  let attempt = 0;
  
  while (attempt < maxRetries) {
    const result = await baseQuery(args, api, extraOptions);
    
    // Success - return immediately
    if (!result.error) {
      return result;
    }
    
    // Client errors (4xx) - don't retry
    if (result.error.status && typeof result.error.status === 'number' && result.error.status >= 400 && result.error.status < 500) {
      return result;
    }
    
    // Last attempt - return error
    if (attempt === maxRetries - 1) {
      return result;
    }
    
    // Network error or 5xx - retry with exponential backoff
    attempt++;
    const delay = Math.pow(2, attempt - 1) * 1000; // 1s, 2s, 4s
    await new Promise((resolve) => setTimeout(resolve, delay));
  }
  
  // Fallback (should never reach here)
  return await baseQuery(args, api, extraOptions);
};

/**
 * Base query with error handling middleware and automatic token refresh
 * 
 * Handles:
 * - 401 Unauthorized - triggers token refresh or logout
 * - Network errors - displays user-friendly messages
 * - Server errors - logs for debugging
 * 
 * Token refresh flow:
 * 1. On 401 error, acquire mutex to prevent concurrent refresh attempts
 * 2. Check if token has already been refreshed by another request
 * 3. Attempt to refresh token using refresh token from localStorage
 * 4. If refresh succeeds, update token and retry original request
 * 5. If refresh fails, logout user and redirect to login
 */
export const baseQueryWithErrorHandling: BaseQueryFn<
  string | FetchArgs,
  unknown,
  FetchBaseQueryError
> = async (args, api, extraOptions) => {
  // Wait for any ongoing refresh to complete
  await mutex.waitForUnlock();
  
  let result = await baseQueryWithRetry(args, api, extraOptions);
  
  if (result.error && result.error.status === 401) {
    // Check if we're not already refreshing
    if (!mutex.isLocked()) {
      const release = await mutex.acquire();
      
      try {
        const state = api.getState() as RootState;
        const refreshToken = state.auth.refreshToken || getStoredRefreshToken();
        
        if (refreshToken) {
          // Attempt to refresh the token
          const refreshResult = await baseQuery(
            {
              url: '/api/auth/refresh',
              method: 'POST',
              body: { refreshToken },
            },
            api,
            extraOptions
          );
          
          if (refreshResult.data) {
            // Token refresh successful
            const refreshPayload = refreshResult.data as {
              token?: string;
              accessToken?: string;
              expiresIn: number;
            };
            const token = refreshPayload.token ?? refreshPayload.accessToken;

            if (!token) {
              api.dispatch(logout());
              redirectToLogin();

              return result;
            }
            
            // Update token in Redux store
            api.dispatch(setToken(token));
            
            // Retry the original request with new token
            result = await baseQueryWithRetry(args, api, extraOptions);
          } else {
            // Token refresh failed - logout user
            api.dispatch(logout());
            redirectToLogin();
          }
        } else {
          // No refresh token available - logout user
          api.dispatch(logout());
          redirectToLogin();
        }
      } finally {
        release();
      }
    } else {
      // Wait for the ongoing refresh to complete and retry
      await mutex.waitForUnlock();
      result = await baseQueryWithRetry(args, api, extraOptions);
    }
  }
  
  // Log errors in development
  if (result.error && import.meta.env.DEV) {
    console.error('API Error:', {
      status: result.error.status,
      endpoint: typeof args === 'string' ? args : args.url,
    });
  }
  
  return result;
};

// Export the configured base query for use in API slices
export const baseQueryWithEnvironment = baseQueryWithErrorHandling;

const extractErrorMessageFromPayload = (payload: unknown): string | null => {
  if (typeof payload === 'string' && payload.trim().length > 0) {
    return payload;
  }

  if (typeof payload !== 'object' || payload === null) {
    return null;
  }

  const typedPayload = payload as { message?: unknown; error?: unknown };
  if (typeof typedPayload.message === 'string' && typedPayload.message.trim().length > 0) {
    return typedPayload.message;
  }
  if (typeof typedPayload.error === 'string' && typedPayload.error.trim().length > 0) {
    return typedPayload.error;
  }

  return null;
};

export const getApiErrorMessage = (error: unknown, fallback = 'An unexpected error occurred'): string => {
  if (!error) {
    return fallback;
  }

  if (typeof error === 'object' && error !== null && 'status' in error) {
    const queryError = error as FetchBaseQueryError;
    const payloadMessage = extractErrorMessageFromPayload(queryError.data);
    if (payloadMessage) {
      return payloadMessage;
    }
    if (typeof queryError.status === 'string' && queryError.status.trim().length > 0) {
      return queryError.status;
    }
  }

  const serializedError = error as SerializedError;
  if (typeof serializedError.message === 'string' && serializedError.message.trim().length > 0) {
    return serializedError.message;
  }

  return fallback;
};


