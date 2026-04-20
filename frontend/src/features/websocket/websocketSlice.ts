/**
 * WebSocket Redux Slice
 * Manages WebSocket connection state in Redux store
 */

import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

import type { WebSocketEventType } from '@/services/websocket';

export interface WebSocketState {
  connected: boolean;
  connecting: boolean;
  error: string | null;
  lastReconnectAttempt: string | null;
  reconnectAttempts: number;
  subscribedChannels: string[];
  lastEventTime: string | null;
  lastEventByType: Partial<Record<WebSocketEventType, string>>;
}

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

const websocketSlice = createSlice({
  name: 'websocket',
  initialState,
  reducers: {
    connectionStarted: (state) => {
      state.connecting = true;
      state.error = null;
    },
    connectionEstablished: (state, action: PayloadAction<string[]>) => {
      state.connected = true;
      state.connecting = false;
      state.error = null;
      state.reconnectAttempts = 0;
      state.subscribedChannels = action.payload;
    },
    subscriptionsUpdated: (state, action: PayloadAction<string[]>) => {
      state.subscribedChannels = Array.from(
        new Set([...state.subscribedChannels, ...action.payload])
      );
      state.error = null;
    },
    subscriptionsRemoved: (state, action: PayloadAction<string[]>) => {
      const removedChannels = new Set(action.payload);
      state.subscribedChannels = state.subscribedChannels.filter(
        (channel) => !removedChannels.has(channel)
      );
    },
    subscriptionFailed: (state, action: PayloadAction<string>) => {
      state.error = action.payload;
    },
    connectionFailed: (state, action: PayloadAction<string>) => {
      state.connected = false;
      state.connecting = false;
      state.error = action.payload;
    },
    connectionClosed: (state) => {
      state.connected = false;
      state.connecting = false;
      state.subscribedChannels = [];
    },
    reconnectAttempted: (state) => {
      state.reconnectAttempts += 1;
      state.lastReconnectAttempt = new Date().toISOString();
    },
    eventReceived: (
      state,
      action: PayloadAction<{ timestamp: string; type: WebSocketEventType }>
    ) => {
      state.lastEventTime = action.payload.timestamp;
      state.lastEventByType[action.payload.type] = action.payload.timestamp;
    },
    resetState: () => initialState,
  },
});

export const {
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
} = websocketSlice.actions;

// Selectors
const selectWebSocketSlice = (state: { websocket?: WebSocketState }) => state.websocket ?? initialState;

export const selectWebSocketState = (state: { websocket?: WebSocketState }) =>
  selectWebSocketSlice(state);
export const selectIsConnected = (state: { websocket?: WebSocketState }) =>
  selectWebSocketSlice(state).connected;
export const selectIsConnecting = (state: { websocket?: WebSocketState }) =>
  selectWebSocketSlice(state).connecting;
export const selectConnectionError = (state: { websocket?: WebSocketState }) =>
  selectWebSocketSlice(state).error;
export const selectReconnectAttempts = (state: { websocket?: WebSocketState }) =>
  selectWebSocketSlice(state).reconnectAttempts;
export const selectSubscribedChannels = (state: { websocket?: WebSocketState }) =>
  selectWebSocketSlice(state).subscribedChannels;
export const selectLastEventTime = (state: { websocket?: WebSocketState }) =>
  selectWebSocketSlice(state).lastEventTime;
export const selectLastEventTimeForType = (
  state: { websocket?: WebSocketState },
  eventType: WebSocketEventType
) => selectWebSocketSlice(state).lastEventByType[eventType] ?? null;
export default websocketSlice.reducer;

