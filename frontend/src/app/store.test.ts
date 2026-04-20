import { describe, it, expect } from 'vitest';

import { store } from './store';

describe('Redux Store Configuration', () => {
  it('should create store with initial state', () => {
    const state = store.getState();
    expect(state).toBeDefined();
    expect(typeof state).toBe('object');
  });

  it('should have dispatch function', () => {
    expect(typeof store.dispatch).toBe('function');
  });

  it('should have subscribe function', () => {
    expect(typeof store.subscribe).toBe('function');
  });

  it('should have getState function', () => {
    expect(typeof store.getState).toBe('function');
  });

  it('should allow subscribing to state changes', () => {
    const unsubscribe = store.subscribe(() => {
      // Subscription callback
    });
    
    expect(typeof unsubscribe).toBe('function');
    unsubscribe();
  });
});
