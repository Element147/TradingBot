import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

export interface NotificationSettings {
  emailAlerts: boolean;
  telegramAlerts: boolean;
  profitLossThreshold: number;
  drawdownThreshold: number;
  riskThreshold: number;
}

/**
 * Settings state interface.
 * Manages user preferences with browser persistence for non-sensitive UI settings.
 */
export interface SettingsState {
  theme: 'light' | 'dark';
  currency: 'USD' | 'BTC';
  timezone: string;
  textScale: number;
  notifications: NotificationSettings;
}

const DEFAULT_NOTIFICATIONS: NotificationSettings = {
  emailAlerts: true,
  telegramAlerts: false,
  profitLossThreshold: 5,
  drawdownThreshold: 15,
  riskThreshold: 75,
};

const parseNotifications = (raw: string | null): NotificationSettings => {
  if (!raw) {
    return DEFAULT_NOTIFICATIONS;
  }
  try {
    const parsed = JSON.parse(raw) as Partial<NotificationSettings>;
    return {
      emailAlerts: parsed.emailAlerts ?? DEFAULT_NOTIFICATIONS.emailAlerts,
      telegramAlerts: parsed.telegramAlerts ?? DEFAULT_NOTIFICATIONS.telegramAlerts,
      profitLossThreshold:
        parsed.profitLossThreshold ?? DEFAULT_NOTIFICATIONS.profitLossThreshold,
      drawdownThreshold: parsed.drawdownThreshold ?? DEFAULT_NOTIFICATIONS.drawdownThreshold,
      riskThreshold: parsed.riskThreshold ?? DEFAULT_NOTIFICATIONS.riskThreshold,
    };
  } catch {
    return DEFAULT_NOTIFICATIONS;
  }
};

const persistNotifications = (notifications: NotificationSettings) => {
  localStorage.setItem('notificationSettings', JSON.stringify(notifications));
};

/**
 * Initialize settings from localStorage or use defaults.
 */
const initializeSettings = (): SettingsState => {
  const savedTheme = localStorage.getItem('theme') as 'light' | 'dark' | null;
  const savedCurrency = localStorage.getItem('currency') as 'USD' | 'BTC' | null;
  const savedTimezone = localStorage.getItem('timezone');
  const savedTextScale = Number(localStorage.getItem('textScale') ?? '1');
  const notifications = parseNotifications(localStorage.getItem('notificationSettings'));

  return {
    theme: savedTheme || 'light',
    currency: savedCurrency || 'USD',
    timezone: savedTimezone || Intl.DateTimeFormat().resolvedOptions().timeZone,
    textScale: Number.isFinite(savedTextScale) && savedTextScale >= 1 ? savedTextScale : 1,
    notifications,
  };
};

const initialState: SettingsState = initializeSettings();

/**
 * Settings slice.
 * Persists only non-sensitive display/notification preferences in localStorage.
 */
const settingsSlice = createSlice({
  name: 'settings',
  initialState,
  reducers: {
    setTheme: (state, action: PayloadAction<'light' | 'dark'>) => {
      state.theme = action.payload;
      localStorage.setItem('theme', action.payload);
    },
    setCurrency: (state, action: PayloadAction<'USD' | 'BTC'>) => {
      state.currency = action.payload;
      localStorage.setItem('currency', action.payload);
    },
    setTimezone: (state, action: PayloadAction<string>) => {
      state.timezone = action.payload;
      localStorage.setItem('timezone', action.payload);
    },
    setTextScale: (state, action: PayloadAction<number>) => {
      state.textScale = action.payload;
      localStorage.setItem('textScale', String(action.payload));
    },
    setNotificationSettings: (state, action: PayloadAction<NotificationSettings>) => {
      state.notifications = action.payload;
      persistNotifications(action.payload);
    },
    updateNotificationSetting: (
      state,
      action: PayloadAction<{
        key: keyof NotificationSettings;
        value: NotificationSettings[keyof NotificationSettings];
      }>
    ) => {
      const { key, value } = action.payload;
      state.notifications[key] = value as never;
      persistNotifications(state.notifications);
    },
    resetSettings: (state) => {
      state.theme = 'light';
      state.currency = 'USD';
      state.timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      state.textScale = 1;
      state.notifications = DEFAULT_NOTIFICATIONS;
      localStorage.removeItem('theme');
      localStorage.removeItem('currency');
      localStorage.removeItem('timezone');
      localStorage.removeItem('textScale');
      localStorage.removeItem('notificationSettings');
    },
  },
});

export const {
  setTheme,
  setCurrency,
  setTimezone,
  setTextScale,
  setNotificationSettings,
  updateNotificationSetting,
  resetSettings,
} = settingsSlice.actions;

type SettingsRootState = { settings: SettingsState };

export const selectTheme = (state: SettingsRootState) => state.settings.theme;
export const selectCurrency = (state: SettingsRootState) => state.settings.currency;
export const selectTimezone = (state: SettingsRootState) => state.settings.timezone;
export const selectTextScale = (state: SettingsRootState) => state.settings.textScale;
export const selectNotificationSettings = (state: SettingsRootState) => state.settings.notifications;
export const selectSettings = (state: SettingsRootState) => state.settings;

export default settingsSlice.reducer;
