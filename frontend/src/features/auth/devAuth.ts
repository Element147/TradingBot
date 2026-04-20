import type { User } from './authSlice';

const parseBooleanEnv = (value: boolean | string | undefined) =>
  String(value).toLowerCase() === 'true';

export const DEV_AUTH_BYPASS_ENABLED =
  import.meta.env.MODE !== 'test' &&
  parseBooleanEnv(import.meta.env.DEV) &&
  parseBooleanEnv(import.meta.env.VITE_DEV_AUTH_BYPASS);

export const DEV_AUTH_BYPASS_USER: User = {
  id: 'local-debug-admin',
  username: 'admin',
  email: 'admin@algotrading.local',
  role: 'admin',
};
