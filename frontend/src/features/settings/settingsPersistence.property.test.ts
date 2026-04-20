import fc from 'fast-check';
import { beforeEach, describe, expect, it } from 'vitest';

import settingsReducer, {
  resetSettings,
  setCurrency,
  setTextScale,
  setTheme,
  setTimezone,
  type SettingsState,
} from './settingsSlice';

const createState = (): SettingsState => ({
  theme: 'light',
  currency: 'USD',
  timezone: 'UTC',
  textScale: 1,
  notifications: {
    emailAlerts: true,
    telegramAlerts: false,
    profitLossThreshold: 5,
    drawdownThreshold: 15,
    riskThreshold: 75,
  },
});

describe('settings persistence properties', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('persists arbitrary preference changes to localStorage', () => {
    fc.assert(
      fc.property(
        fc.constantFrom<'light' | 'dark'>('light', 'dark'),
        fc.constantFrom<'USD' | 'BTC'>('USD', 'BTC'),
        fc.constantFrom('UTC', 'Europe/Prague', 'America/New_York', 'Asia/Tokyo'),
        fc.double({ min: 1, max: 2, noNaN: true }),
        (theme, currency, timezone, textScale) => {
          let state = settingsReducer(createState(), setTheme(theme));
          state = settingsReducer(state, setCurrency(currency));
          state = settingsReducer(state, setTimezone(timezone));
          state = settingsReducer(state, setTextScale(Number(textScale.toFixed(1))));

          expect(localStorage.getItem('theme')).toBe(theme);
          expect(localStorage.getItem('currency')).toBe(currency);
          expect(localStorage.getItem('timezone')).toBe(timezone);
          expect(localStorage.getItem('textScale')).toBe(String(state.textScale));
        }
      ),
      { numRuns: 150 }
    );
  });

  it('reset clears persisted values after arbitrary updates', () => {
    fc.assert(
      fc.property(
        fc.constantFrom<'light' | 'dark'>('light', 'dark'),
        fc.constantFrom<'USD' | 'BTC'>('USD', 'BTC'),
        fc.constantFrom('UTC', 'Europe/Prague', 'America/New_York', 'Asia/Tokyo'),
        fc.double({ min: 1, max: 2, noNaN: true }),
        (theme, currency, timezone, textScale) => {
          let state = settingsReducer(createState(), setTheme(theme));
          state = settingsReducer(state, setCurrency(currency));
          state = settingsReducer(state, setTimezone(timezone));
          state = settingsReducer(state, setTextScale(Number(textScale.toFixed(1))));
          state = settingsReducer(state, resetSettings());

          expect(state.theme).toBe('light');
          expect(state.currency).toBe('USD');
          expect(state.textScale).toBe(1);
          expect(localStorage.getItem('theme')).toBeNull();
          expect(localStorage.getItem('currency')).toBeNull();
          expect(localStorage.getItem('timezone')).toBeNull();
          expect(localStorage.getItem('textScale')).toBeNull();
        }
      ),
      { numRuns: 120 }
    );
  });
});
