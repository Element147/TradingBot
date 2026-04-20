/**
 * WebSocket Slice Tests
 */

import { describe, it, expect } from 'vitest';

import websocketReducer, {
  connectionStarted,
  connectionEstablished,
  subscriptionsUpdated,
  subscriptionsRemoved,
  subscriptionFailed,
  connectionFailed,
  connectionClosed,
  reconnectAttempted,
  eventReceived,
  resetState,
  WebSocketState,
} from './websocketSlice';

describe('websocketSlice', () => {
  const initialState: WebSocketState = {
    connected: false,
    connecting: false,
    error: null,
    lastReconnectAttempt: null,
    reconnectAttempts: 0,
    subscribedChannels: [],
    lastEventTime: null,
    lastEventByType: {},
  };

  describe('connectionStarted', () => {
    it('should set connecting to true', () => {
      const state = websocketReducer(initialState, connectionStarted());

      expect(state.connecting).toBe(true);
      expect(state.error).toBeNull();
    });

    it('should clear previous error', () => {
      const stateWithError: WebSocketState = {
        ...initialState,
        error: 'Previous error',
      };

      const state = websocketReducer(stateWithError, connectionStarted());

      expect(state.error).toBeNull();
    });
  });

  describe('connectionEstablished', () => {
    it('should set connected to true and update channels', () => {
      const channels = ['test.balance', 'test.trades', 'test.positions'];
      const state = websocketReducer(initialState, connectionEstablished(channels));

      expect(state.connected).toBe(true);
      expect(state.connecting).toBe(false);
      expect(state.error).toBeNull();
      expect(state.reconnectAttempts).toBe(0);
      expect(state.subscribedChannels).toEqual(channels);
    });

    it('should reset reconnect attempts', () => {
      const stateWithAttempts: WebSocketState = {
        ...initialState,
        reconnectAttempts: 3,
      };

      const state = websocketReducer(
        stateWithAttempts,
        connectionEstablished(['test.balance'])
      );

      expect(state.reconnectAttempts).toBe(0);
    });
  });

  describe('subscriptionsUpdated', () => {
    it('should merge subscribed channels without duplicates', () => {
      const state = websocketReducer(
        {
          ...initialState,
          connected: true,
          subscribedChannels: ['test.balance'],
        },
        subscriptionsUpdated(['test.balance', 'test.backtests'])
      );

      expect(state.subscribedChannels).toEqual(['test.balance', 'test.backtests']);
      expect(state.error).toBeNull();
    });
  });

  describe('subscriptionsRemoved', () => {
    it('should remove unsubscribed channels', () => {
      const state = websocketReducer(
        {
          ...initialState,
          connected: true,
          subscribedChannels: ['test.balance', 'test.backtests'],
        },
        subscriptionsRemoved(['test.balance'])
      );

      expect(state.subscribedChannels).toEqual(['test.backtests']);
    });
  });

  describe('subscriptionFailed', () => {
    it('should retain connection state and expose the subscription error', () => {
      const state = websocketReducer(
        {
          ...initialState,
          connected: true,
        },
        subscriptionFailed('Rejected unauthorized or unknown channels (live.balance)')
      );

      expect(state.connected).toBe(true);
      expect(state.error).toBe('Rejected unauthorized or unknown channels (live.balance)');
    });
  });

  describe('connectionFailed', () => {
    it('should set error and clear connecting state', () => {
      const connectingState: WebSocketState = {
        ...initialState,
        connecting: true,
      };

      const state = websocketReducer(
        connectingState,
        connectionFailed('Connection timeout')
      );

      expect(state.connected).toBe(false);
      expect(state.connecting).toBe(false);
      expect(state.error).toBe('Connection timeout');
    });
  });

  describe('connectionClosed', () => {
    it('should reset connection state', () => {
      const connectedState: WebSocketState = {
        ...initialState,
        connected: true,
        subscribedChannels: ['test.balance', 'test.trades'],
      };

      const state = websocketReducer(connectedState, connectionClosed());

      expect(state.connected).toBe(false);
      expect(state.connecting).toBe(false);
      expect(state.subscribedChannels).toEqual([]);
    });
  });

  describe('reconnectAttempted', () => {
    it('should increment reconnect attempts', () => {
      const state = websocketReducer(initialState, reconnectAttempted());

      expect(state.reconnectAttempts).toBe(1);
      expect(state.lastReconnectAttempt).toBeTruthy();
    });

    it('should update lastReconnectAttempt timestamp', () => {
      const state = websocketReducer(initialState, reconnectAttempted());

      expect(state.lastReconnectAttempt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    });

    it('should increment from previous attempts', () => {
      const stateWithAttempts: WebSocketState = {
        ...initialState,
        reconnectAttempts: 2,
      };

      const state = websocketReducer(stateWithAttempts, reconnectAttempted());

      expect(state.reconnectAttempts).toBe(3);
    });
  });

  describe('eventReceived', () => {
    it('should update lastEventTime', () => {
      const timestamp = '2024-03-09T12:00:00Z';
      const state = websocketReducer(
        initialState,
        eventReceived({ timestamp, type: 'balance.updated' })
      );

      expect(state.lastEventTime).toBe(timestamp);
      expect(state.lastEventByType['balance.updated']).toBe(timestamp);
    });

    it('should overwrite previous event time', () => {
      const stateWithEvent: WebSocketState = {
        ...initialState,
        lastEventTime: '2024-03-09T11:00:00Z',
        lastEventByType: {
          'balance.updated': '2024-03-09T11:00:00Z',
        },
      };

      const newTimestamp = '2024-03-09T12:00:00Z';
      const state = websocketReducer(
        stateWithEvent,
        eventReceived({ timestamp: newTimestamp, type: 'balance.updated' })
      );

      expect(state.lastEventTime).toBe(newTimestamp);
      expect(state.lastEventByType['balance.updated']).toBe(newTimestamp);
    });
  });

  describe('resetState', () => {
    it('should reset to initial state', () => {
      const modifiedState: WebSocketState = {
        connected: true,
        connecting: false,
        error: 'Some error',
        lastReconnectAttempt: '2024-03-09T12:00:00Z',
        reconnectAttempts: 3,
        subscribedChannels: ['test.balance'],
        lastEventTime: '2024-03-09T12:00:00Z',
        lastEventByType: {
          'balance.updated': '2024-03-09T12:00:00Z',
        },
      };

      const state = websocketReducer(modifiedState, resetState());

      expect(state).toEqual(initialState);
    });
  });

  describe('state transitions', () => {
    it('should handle connection lifecycle', () => {
      // Start connecting
      let state = websocketReducer(initialState, connectionStarted());
      expect(state.connecting).toBe(true);

      // Connection established
      state = websocketReducer(state, connectionEstablished(['test.balance']));
      expect(state.connected).toBe(true);
      expect(state.connecting).toBe(false);

      // Connection closed
      state = websocketReducer(state, connectionClosed());
      expect(state.connected).toBe(false);
    });

    it('should handle reconnection attempts', () => {
      // Initial connection fails
      let state = websocketReducer(initialState, connectionStarted());
      state = websocketReducer(state, connectionFailed('Timeout'));
      expect(state.error).toBe('Timeout');

      // First reconnect attempt
      state = websocketReducer(state, reconnectAttempted());
      expect(state.reconnectAttempts).toBe(1);

      // Second reconnect attempt
      state = websocketReducer(state, reconnectAttempted());
      expect(state.reconnectAttempts).toBe(2);

      // Successful connection resets attempts
      state = websocketReducer(state, connectionEstablished(['test.balance']));
      expect(state.reconnectAttempts).toBe(0);
    });
  });
});
