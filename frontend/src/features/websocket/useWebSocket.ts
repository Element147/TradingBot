/**
 * useWebSocket Hook
 * Custom hook for managing WebSocket connections in React components
 */

import { useEffect, useCallback } from 'react';
import { useDispatch, useSelector } from 'react-redux';

import {
  getWebSocketManager,
  type WebSocketEventType,
  type WebSocketEventHandler,
} from '../../services/websocket';
import { selectAuthToken } from '../auth/authSlice';
import { selectEnvironmentMode } from '../environment/environmentSlice';

import {
  connectionStarted,
  connectionEstablished,
  connectionFailed,
  connectionClosed,
  reconnectAttempted,
  eventReceived,
  subscriptionsUpdated,
  subscriptionsRemoved,
  subscriptionFailed,
  selectIsConnected,
} from './websocketSlice';

/**
 * Hook to manage WebSocket connection lifecycle
 */
export const useWebSocketConnection = () => {
  const dispatch = useDispatch();
  const token = useSelector(selectAuthToken);
  const environment = useSelector(selectEnvironmentMode);
  const wsManager = getWebSocketManager();
  const isConnected = useSelector(selectIsConnected);

  useEffect(() => {
    const unsubscribeConnectionState = wsManager.subscribeConnectionState(({ error, state }) => {
      if (state === 'connecting') {
        dispatch(connectionStarted());
        return;
      }

      if (state === 'open') {
        dispatch(connectionEstablished([]));
        return;
      }

      dispatch(connectionClosed());
      if (error) {
        dispatch(connectionFailed(error));
      }
    });

    const unsubscribeReconnects = wsManager.subscribeReconnectAttempts(() => {
      dispatch(reconnectAttempted());
    });

    const unsubscribeSubscriptionState = wsManager.subscribeSubscriptionState(
      ({ action, channels, error, rejectedChannels }) => {
        if (action === 'subscribed' && channels.length > 0) {
          dispatch(subscriptionsUpdated(channels));
        }

        if (action === 'unsubscribed' && channels.length > 0) {
          dispatch(subscriptionsRemoved(channels));
        }

        if (error) {
          const suffix =
            rejectedChannels.length > 0 ? ` (${rejectedChannels.join(', ')})` : '';
          dispatch(subscriptionFailed(`${error}${suffix}`));
        }
      }
    );

    return () => {
      unsubscribeConnectionState();
      unsubscribeReconnects();
      unsubscribeSubscriptionState();
    };
  }, [dispatch, wsManager]);

  useEffect(() => {
    if (!token) {
      wsManager.disconnect();
      return;
    }

    wsManager
      .connect(token, environment)
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Connection failed';
        if (message !== 'Connection already in progress') {
          dispatch(connectionFailed(message));
        }
      });

    // Cleanup on unmount or token/environment change
    return () => {
      wsManager.disconnect();
    };
  }, [token, environment, dispatch, wsManager]);

  const reconnect = useCallback(() => {
    if (token) {
      wsManager
        .connect(token, environment)
        .catch((error: unknown) => {
          const message = error instanceof Error ? error.message : 'Connection failed';
          if (message !== 'Connection already in progress') {
            dispatch(connectionFailed(message));
          }
        });
    }
  }, [token, environment, dispatch, wsManager]);

  return {
    reconnect,
    isConnected,
    state: wsManager.getState(),
  };
};

/**
 * Hook to subscribe to WebSocket events
 */
export const useWebSocketSubscription = (
  eventType: WebSocketEventType | '*',
  handler: WebSocketEventHandler
) => {
  const dispatch = useDispatch();
  const wsManager = getWebSocketManager();

  useEffect(() => {
    // Wrap handler to dispatch eventReceived action
    const wrappedHandler: WebSocketEventHandler = (event) => {
      dispatch(
        eventReceived({
          timestamp: event.timestamp,
          type: event.type,
        })
      );
      handler(event);
    };

    // Subscribe to event
    const unsubscribe = wsManager.subscribe(eventType, wrappedHandler);

    // Cleanup on unmount
    return () => {
      unsubscribe();
    };
  }, [eventType, handler, dispatch, wsManager]);
};




