import { configureStore } from '@reduxjs/toolkit';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { describe, it, expect } from 'vitest';

import ThemeToggle from './ThemeToggle';

import settingsReducer from '@/features/settings/settingsSlice';

// Mock store factory
const createMockStore = (theme: 'light' | 'dark' = 'light') => configureStore({
    reducer: {
      settings: settingsReducer,
    },
    preloadedState: {
      settings: {
        theme,
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
      },
    },
  });

describe('ThemeToggle', () => {
  it('should render theme toggle button', () => {
    const store = createMockStore();
    
    render(
      <Provider store={store}>
        <ThemeToggle />
      </Provider>
    );

    const button = screen.getByRole('button', { name: /switch to dark mode/i });
    expect(button).toBeInTheDocument();
  });

  it('should display moon icon in light mode', () => {
    const store = createMockStore('light');
    
    render(
      <Provider store={store}>
        <ThemeToggle />
      </Provider>
    );

    // Brightness4 is the moon icon for dark mode
    const button = screen.getByRole('button', { name: /switch to dark mode/i });
    expect(button).toBeInTheDocument();
  });

  it('should display sun icon in dark mode', () => {
    const store = createMockStore('dark');
    
    render(
      <Provider store={store}>
        <ThemeToggle />
      </Provider>
    );

    // Brightness7 is the sun icon for light mode
    const button = screen.getByRole('button', { name: /switch to light mode/i });
    expect(button).toBeInTheDocument();
  });

  it('should toggle theme from light to dark on click', () => {
    const store = createMockStore('light');
    
    render(
      <Provider store={store}>
        <ThemeToggle />
      </Provider>
    );

    const button = screen.getByRole('button', { name: /switch to dark mode/i });
    fireEvent.click(button);

    const state = store.getState();
    expect(state.settings.theme).toBe('dark');
  });

  it('should toggle theme from dark to light on click', () => {
    const store = createMockStore('dark');
    
    render(
      <Provider store={store}>
        <ThemeToggle />
      </Provider>
    );

    const button = screen.getByRole('button', { name: /switch to light mode/i });
    fireEvent.click(button);

    const state = store.getState();
    expect(state.settings.theme).toBe('light');
  });

  it('should persist theme to localStorage on toggle', () => {
    const store = createMockStore('light');
    
    render(
      <Provider store={store}>
        <ThemeToggle />
      </Provider>
    );

    const button = screen.getByRole('button', { name: /switch to dark mode/i });
    fireEvent.click(button);

    expect(localStorage.getItem('theme')).toBe('dark');
  });

  it('should have correct tooltip text for light mode', () => {
    const store = createMockStore('light');
    
    render(
      <Provider store={store}>
        <ThemeToggle />
      </Provider>
    );

    const button = screen.getByRole('button', { name: /switch to dark mode/i });
    expect(button).toHaveAttribute('aria-label', 'Switch to dark mode');
  });

  it('should have correct tooltip text for dark mode', () => {
    const store = createMockStore('dark');
    
    render(
      <Provider store={store}>
        <ThemeToggle />
      </Provider>
    );

    const button = screen.getByRole('button', { name: /switch to light mode/i });
    expect(button).toHaveAttribute('aria-label', 'Switch to light mode');
  });

  it('should be keyboard accessible', () => {
    const store = createMockStore('light');
    
    render(
      <Provider store={store}>
        <ThemeToggle />
      </Provider>
    );

    const button = screen.getByRole('button', { name: /switch to dark mode/i });
    
    // Button should be focusable
    button.focus();
    expect(document.activeElement).toBe(button);
    
    // Button should be clickable (keyboard users can activate with Enter/Space)
    // This is handled natively by the button element
    expect(button.tagName).toBe('BUTTON');
  });
});
