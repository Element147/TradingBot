import DOMPurify from 'dompurify';

const CSRF_STORAGE_KEY = 'csrf_token';

export const sanitizeText = (input: string): string =>
  DOMPurify.sanitize(input, {
    ALLOWED_TAGS: [],
    ALLOWED_ATTR: [],
  }).trim();

export const getOrCreateCsrfToken = (): string => {
  const existing = sessionStorage.getItem(CSRF_STORAGE_KEY);
  if (existing) {
    return existing;
  }

  const token =
    globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  sessionStorage.setItem(CSRF_STORAGE_KEY, token);
  return token;
};

export const clearCsrfToken = (): void => {
  sessionStorage.removeItem(CSRF_STORAGE_KEY);
};
