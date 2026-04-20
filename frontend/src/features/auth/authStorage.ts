const AUTH_TOKEN_KEY = 'auth_token';
const AUTH_USER_KEY = 'user';
const REFRESH_TOKEN_KEY = 'refresh_token';
const CSRF_TOKEN_KEY = 'csrf_token';

export const setStoredSession = (token: string, user: unknown): void => {
  sessionStorage.setItem(AUTH_TOKEN_KEY, token);
  sessionStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
};

export const setStoredAuthToken = (token: string): void => {
  sessionStorage.setItem(AUTH_TOKEN_KEY, token);
};

export const setStoredRefreshToken = (refreshToken?: string | null): void => {
  if (refreshToken && refreshToken.trim()) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    return;
  }
  localStorage.removeItem(REFRESH_TOKEN_KEY);
};

export const clearStoredAuth = (): void => {
  sessionStorage.removeItem(AUTH_TOKEN_KEY);
  sessionStorage.removeItem(AUTH_USER_KEY);
  sessionStorage.removeItem(CSRF_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
};

export const getStoredAuthToken = (): string | null => sessionStorage.getItem(AUTH_TOKEN_KEY);

export const getStoredUser = (): string | null => sessionStorage.getItem(AUTH_USER_KEY);

export const getStoredRefreshToken = (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY);

export const redirectToLogin = (): void => {
  if (typeof window !== 'undefined') {
    if (import.meta.env.MODE === 'test') {
      if (typeof window.location.assign === 'function') {
        window.history.replaceState({}, '', '/login');
      } else {
        (window.location as { href: string }).href = '/login';
      }
      return;
    }

    window.location.href = '/login';
  }
};
