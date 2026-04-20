/**
 * WebSocket Manager
 * Handles WebSocket connection lifecycle, reconnection logic, and event routing
 */

export type WebSocketEventType =
  | 'balance.updated'
  | 'trade.executed'
  | 'position.updated'
  | 'strategy.status'
  | 'risk.alert'
  | 'system.error'
  | 'backtest.progress'
  | 'marketData.import.progress';

export interface BacktestProgressEventData {
  backtestId: number;
  strategyId: string;
  datasetName: string | null;
  experimentName: string;
  symbol: string;
  timeframe: string;
  executionStatus: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  validationStatus: 'PENDING' | 'PASSED' | 'FAILED' | 'PRODUCTION_READY';
  feesBps: number;
  slippageBps: number;
  timestamp: string;
  initialBalance: number;
  finalBalance: number;
  executionStage:
    | 'QUEUED'
    | 'VALIDATING_REQUEST'
    | 'LOADING_DATASET'
    | 'FILTERING_CANDLES'
    | 'SIMULATING'
    | 'PERSISTING_RESULTS'
    | 'COMPLETED'
    | 'FAILED';
  progressPercent: number;
  processedCandles: number;
  totalCandles: number;
  currentDataTimestamp: string | null;
  statusMessage: string | null;
  lastProgressAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  errorMessage: string | null;
}

export interface MarketDataImportProgressEventData {
  id: number;
  providerId: string;
  providerLabel: string;
  assetType: 'STOCK' | 'CRYPTO';
  datasetName: string;
  symbolsCsv: string;
  timeframe: string;
  startDate: string;
  endDate: string;
  adjusted: boolean;
  regularSessionOnly: boolean;
  status: 'QUEUED' | 'RUNNING' | 'WAITING_RETRY' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
  statusMessage: string;
  nextRetryAt: string | null;
  currentSymbolIndex: number;
  totalSymbols: number;
  currentSymbol: string | null;
  importedRowCount: number;
  datasetId: number | null;
  datasetReady: boolean;
  currentChunkStart: string | null;
  attemptCount: number;
  createdAt: string;
  updatedAt: string;
  startedAt: string | null;
  completedAt: string | null;
}

export interface WebSocketEventDataMap {
  'balance.updated': unknown;
  'trade.executed': unknown;
  'position.updated': unknown;
  'strategy.status': unknown;
  'risk.alert': unknown;
  'system.error': unknown;
  'backtest.progress': BacktestProgressEventData;
  'marketData.import.progress': MarketDataImportProgressEventData;
}

export interface WebSocketEvent<T extends WebSocketEventType = WebSocketEventType> {
  type: T;
  environment: 'test' | 'live';
  timestamp: string;
  data: WebSocketEventDataMap[T];
}

export interface WebSocketAckMessage {
  type: 'ack';
  action: 'subscribed' | 'unsubscribed';
  channels: string[];
}

export interface WebSocketErrorMessage {
  type: 'error';
  message: string;
  channels?: string[];
}

export type WebSocketEventHandler = (event: WebSocketEvent) => void;
export type WebSocketConnectionState = 'connecting' | 'open' | 'closing' | 'closed';

export interface WebSocketConnectionStateEvent {
  error: string | null;
  state: WebSocketConnectionState;
}

export type WebSocketConnectionStateHandler = (event: WebSocketConnectionStateEvent) => void;
export type WebSocketReconnectHandler = (attempt: number, maxAttempts: number) => void;
export interface WebSocketSubscriptionStateEvent {
  action: 'subscribed' | 'unsubscribed' | 'error';
  channels: string[];
  error: string | null;
  rejectedChannels: string[];
}
export type WebSocketSubscriptionStateHandler = (
  event: WebSocketSubscriptionStateEvent
) => void;

interface SubscriptionHandlers {
  [channel: string]: Set<WebSocketEventHandler>;
}

interface WebSocketLike {
  readyState: number;
  onopen: ((event: Event) => void) | null;
  onclose: ((event: CloseEvent) => void) | null;
  onerror: ((event: Event) => void) | null;
  onmessage: ((event: MessageEvent) => void) | null;
  send(data: string): void;
  close(code?: number, reason?: string): void;
}

export type WebSocketLikeConstructor = new (url: string) => WebSocketLike;

interface ResolveWebSocketUrlOptions {
  pageUrl?: string;
  production?: boolean;
}

const WS_CONNECTING = 0;
const WS_OPEN = 1;
const WS_CLOSING = 2;
const WS_CLOSED = 3;

const toError = (error: unknown): Error =>
  error instanceof Error ? error : new Error('Unknown WebSocket error');

const getDefaultPageUrl = (): string => {
  if (typeof window !== 'undefined' && window.location?.href) {
    return window.location.href;
  }

  return 'http://localhost:5173/';
};

const buildLocalDevWebSocketUrl = (): string => {
  const url = new URL('http://localhost:8080/ws');
  url.protocol = 'ws:';
  return url.toString();
};

export const buildEnvironmentChannels = (environment: 'test' | 'live'): string[] => [
  `${environment}.balance`,
  `${environment}.trades`,
  `${environment}.positions`,
  `${environment}.strategies`,
  `${environment}.risk`,
  `${environment}.backtests`,
  `${environment}.marketData`,
];

export const buildDefaultSubscriptionChannels = (): string[] => [
  ...buildEnvironmentChannels('test'),
  ...buildEnvironmentChannels('live'),
];

export const resolveWebSocketUrl = (
  configuredUrl?: string,
  options: ResolveWebSocketUrlOptions = {}
): string => {
  const production = options.production ?? import.meta.env.PROD;
  const pageUrl = new URL(options.pageUrl ?? getDefaultPageUrl());

  if (!configuredUrl || configuredUrl.trim() === '') {
    if (!production) {
      return buildLocalDevWebSocketUrl();
    }

    const sameOriginUrl = new URL('/ws', pageUrl);
    sameOriginUrl.protocol = 'wss:';
    return sameOriginUrl.toString();
  }

  const resolvedUrl = new URL(configuredUrl, pageUrl);

  if (production) {
    if (resolvedUrl.protocol === 'wss:') {
      return resolvedUrl.toString();
    }

    if (
      (resolvedUrl.protocol === 'http:' || resolvedUrl.protocol === 'https:') &&
      resolvedUrl.origin === pageUrl.origin
    ) {
      resolvedUrl.protocol = 'wss:';
      return resolvedUrl.toString();
    }

    throw new Error(
      'Production WebSocket URL must resolve to a secure same-origin WebSocket endpoint'
    );
  }

  if (resolvedUrl.protocol === 'http:' || resolvedUrl.protocol === 'https:') {
    resolvedUrl.protocol = resolvedUrl.protocol === 'https:' ? 'wss:' : 'ws:';
  }

  return resolvedUrl.toString();
};

export class WebSocketManager {
  private ws: WebSocketLike | null = null;
  private url: string;
  private token: string | null = null;
  private environment: 'test' | 'live' = 'test';
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 3;
  private reconnectDelay = 5000; // 5 seconds
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private subscriptions: SubscriptionHandlers = {};
  private isConnecting = false;
  private shouldReconnect = true;
  private webSocketConstructor: WebSocketLikeConstructor;
  private connectionState: WebSocketConnectionState = 'closed';
  private connectionError: string | null = null;
  private connectionStateHandlers = new Set<WebSocketConnectionStateHandler>();
  private reconnectHandlers = new Set<WebSocketReconnectHandler>();
  private subscriptionStateHandlers = new Set<WebSocketSubscriptionStateHandler>();

  constructor(url: string, webSocketConstructor?: WebSocketLikeConstructor) {
    this.url = url;
    this.webSocketConstructor =
      webSocketConstructor ?? (WebSocket as unknown as WebSocketLikeConstructor);
  }

  /**
   * Connect to WebSocket server with authentication token
   */
  connect(token: string, environment: 'test' | 'live' = 'test'): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.ws?.readyState === WS_OPEN) {
        this.notifyConnectionState('open');
        resolve();
        return;
      }

      if (this.isConnecting) {
        reject(new Error('Connection already in progress'));
        return;
      }

      this.token = token;
      this.environment = environment;
      this.isConnecting = true;
      this.shouldReconnect = true;
      this.connectionError = null;
      this.notifyConnectionState('connecting');

      try {
        // Include auth token and environment in URL
        const wsUrl = `${this.url}?token=${encodeURIComponent(token)}&env=${environment}`;
        this.ws = new this.webSocketConstructor(wsUrl);

        this.ws.onopen = () => {
          console.warn('[WebSocket] Connected successfully');
          this.isConnecting = false;
          this.reconnectAttempts = 0;
          this.connectionError = null;
          this.notifyConnectionState('open');
          this.subscribeToChannels();
          resolve();
        };

        this.ws.onmessage = (event) => {
          this.handleMessage(event);
        };

        this.ws.onerror = (error) => {
          console.error('[WebSocket] Error:', error);
          this.isConnecting = false;
          this.connectionError = 'WebSocket transport error';
          this.notifyConnectionState('closed', this.connectionError);
        };

        this.ws.onclose = (event) => {
          console.warn('[WebSocket] Connection closed:', event.code, event.reason);
          this.isConnecting = false;
          this.ws = null;
          this.notifyConnectionState('closed', event.reason || this.connectionError);

          if (this.shouldReconnect && this.reconnectAttempts < this.maxReconnectAttempts) {
            this.scheduleReconnect();
          }
        };
      } catch (error) {
        this.isConnecting = false;
        reject(toError(error));
      }
    });
  }

  /**
   * Disconnect from WebSocket server
   */
  disconnect(): void {
    this.shouldReconnect = false;
    this.isConnecting = false;

    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }

    if (this.ws) {
      this.ws.close(1000, 'Client disconnect');
      this.ws = null;
    }

    this.subscriptions = {};
    this.reconnectAttempts = 0;
    this.connectionError = null;
    this.notifyConnectionState('closed');
  }

  /**
   * Subscribe to WebSocket events
   */
  subscribe(channel: string, handler: WebSocketEventHandler): () => void {
    if (!this.subscriptions[channel]) {
      this.subscriptions[channel] = new Set();
    }

    this.subscriptions[channel].add(handler);

    // Return unsubscribe function
    return () => {
      this.subscriptions[channel]?.delete(handler);
      if (this.subscriptions[channel]?.size === 0) {
        delete this.subscriptions[channel];
      }
    };
  }

  /**
   * Get current connection state
   */
  getState(): 'connecting' | 'open' | 'closing' | 'closed' {
    if (!this.ws) return this.connectionState;

    switch (this.ws.readyState) {
      case WS_CONNECTING:
        return 'connecting';
      case WS_OPEN:
        return 'open';
      case WS_CLOSING:
        return 'closing';
      case WS_CLOSED:
        return 'closed';
      default:
        return 'closed';
    }
  }

  /**
   * Check if WebSocket is connected
   */
  isConnected(): boolean {
    return this.ws?.readyState === WS_OPEN;
  }

  subscribeConnectionState(handler: WebSocketConnectionStateHandler): () => void {
    this.connectionStateHandlers.add(handler);
    handler({
      error: this.connectionError,
      state: this.connectionState,
    });

    return () => {
      this.connectionStateHandlers.delete(handler);
    };
  }

  subscribeReconnectAttempts(handler: WebSocketReconnectHandler): () => void {
    this.reconnectHandlers.add(handler);
    return () => {
      this.reconnectHandlers.delete(handler);
    };
  }

  subscribeSubscriptionState(handler: WebSocketSubscriptionStateHandler): () => void {
    this.subscriptionStateHandlers.add(handler);
    return () => {
      this.subscriptionStateHandlers.delete(handler);
    };
  }

  /**
   * Handle incoming WebSocket messages
   */
  private handleMessage(event: MessageEvent): void {
    try {
      const rawMessage =
        typeof event.data === 'string' ? event.data : JSON.stringify(event.data);
      const message = JSON.parse(rawMessage) as
        | WebSocketEvent
        | WebSocketAckMessage
        | WebSocketErrorMessage;

      if (message.type === 'ack') {
        this.notifySubscriptionState({
          action: message.action,
          channels: message.channels,
          error: null,
          rejectedChannels: [],
        });
        return;
      }

      if (message.type === 'error') {
        const rejectedChannels = Array.isArray(message.channels) ? message.channels : [];
        this.notifySubscriptionState({
          action: 'error',
          channels: [],
          error: message.message,
          rejectedChannels,
        });
        return;
      }

      // Route event to subscribed handlers
      const handlers = this.subscriptions[message.type];
      if (handlers) {
        handlers.forEach((handler) => {
          try {
            handler(message);
          } catch (error) {
            console.error('[WebSocket] Handler error:', error);
          }
        });
      }

      // Also notify wildcard subscribers
      const wildcardHandlers = this.subscriptions['*'];
      if (wildcardHandlers) {
        wildcardHandlers.forEach((handler) => {
          try {
            handler(message);
          } catch (error) {
            console.error('[WebSocket] Wildcard handler error:', error);
          }
        });
      }
    } catch (error) {
      console.error('[WebSocket] Failed to parse message:', error);
    }
  }

  /**
   * Subscribe to environment-aware channels
   */
  private subscribeToChannels(): void {
    if (!this.ws || this.ws.readyState !== WS_OPEN) {
      return;
    }

    const channels = buildDefaultSubscriptionChannels();

    this.ws.send(
      JSON.stringify({
        type: 'subscribe',
        channels,
      })
    );

    console.warn('[WebSocket] Requested channel subscription:', channels);
  }

  /**
   * Schedule reconnection attempt
   */
  private scheduleReconnect(): void {
    if (this.reconnectTimer) {
      return;
    }

    this.reconnectAttempts++;
    this.reconnectHandlers.forEach((handler) => {
      try {
        handler(this.reconnectAttempts, this.maxReconnectAttempts);
      } catch (error) {
        console.error('[WebSocket] Reconnect handler error:', error);
      }
    });
    console.warn(
      `[WebSocket] Scheduling reconnect attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts} in ${this.reconnectDelay}ms`
    );

    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;

      if (this.token && this.shouldReconnect) {
        console.warn(
          `[WebSocket] Reconnecting (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`
        );
        this.connect(this.token, this.environment).catch((error) => {
          console.error('[WebSocket] Reconnection failed:', error);
        });
      }
    }, this.reconnectDelay);
  }

  private notifyConnectionState(
    state: WebSocketConnectionState,
    error: string | null = this.connectionError
  ): void {
    this.connectionState = state;
    this.connectionError = error;
    this.connectionStateHandlers.forEach((handler) => {
      try {
        handler({ error, state });
      } catch (handlerError) {
        console.error('[WebSocket] Connection-state handler error:', handlerError);
      }
    });
  }

  private notifySubscriptionState(event: WebSocketSubscriptionStateEvent): void {
    this.subscriptionStateHandlers.forEach((handler) => {
      try {
        handler(event);
      } catch (handlerError) {
        console.error('[WebSocket] Subscription-state handler error:', handlerError);
      }
    });
  }
}

// Singleton instance
let wsManager: WebSocketManager | null = null;

/**
 * Get or create WebSocket manager instance
 */
export const getWebSocketManager = (): WebSocketManager => {
  if (!wsManager) {
    const wsUrl = resolveWebSocketUrl(import.meta.env.VITE_WS_URL);
    wsManager = new WebSocketManager(wsUrl);
  }
  return wsManager;
};

/**
 * Set WebSocket manager instance (for testing)
 */
export const setWebSocketManager = (manager: WebSocketManager | null): void => {
  wsManager = manager;
};
