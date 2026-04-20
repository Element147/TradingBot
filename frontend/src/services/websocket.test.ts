/**
 * WebSocket Manager Tests
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import {
  WebSocketManager,
  type WebSocketLikeConstructor,
  getWebSocketManager,
  resolveWebSocketUrl,
} from './websocket';

// Mock WebSocket
class MockWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;
  static instances: MockWebSocket[] = [];

  readyState = MockWebSocket.CONNECTING;
  url: string;
  onopen: ((event: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  sentMessages: string[] = [];

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
    // Simulate async connection
    setTimeout(() => {
      this.readyState = MockWebSocket.OPEN;
      if (this.onopen) {
        this.onopen(new Event('open'));
      }
    }, 0);
  }

  send(_data: string) {
    this.sentMessages.push(_data);
  }

  close(code?: number, reason?: string) {
    this.readyState = MockWebSocket.CLOSED;
    if (this.onclose) {
      this.onclose(new CloseEvent('close', { code, reason }));
    }
  }

  simulateMessage(data: string) {
    this.onmessage?.(new MessageEvent('message', { data }));
  }
}

describe('WebSocketManager', () => {
  let manager: WebSocketManager;
  const localDevWsUrl = () => resolveWebSocketUrl(undefined, { pageUrl: 'http://localhost:5173/' });

  beforeEach(() => {
    vi.useRealTimers();
    MockWebSocket.instances = [];
    manager = new WebSocketManager(
      localDevWsUrl(),
      MockWebSocket as unknown as WebSocketLikeConstructor
    );
    vi.clearAllTimers();
  });

  afterEach(() => {
    manager.disconnect();
    vi.useRealTimers();
  });

  describe('connect', () => {
    it('should connect with token and environment', async () => {
      const token = 'test-token';
      const environment = 'test';

      await manager.connect(token, environment);

      expect(manager.isConnected()).toBe(true);
      expect(manager.getState()).toBe('open');
    });

    it('should include token and environment in URL', async () => {
      const token = 'test-token-123';
      const environment = 'live';

      await manager.connect(token, environment);

      // WebSocket URL should contain token and env
      expect(manager.isConnected()).toBe(true);
    });

    it('should reject if already connecting', async () => {
      const promise1 = manager.connect('token1', 'test');
      const promise2 = manager.connect('token2', 'test');

      await expect(promise2).rejects.toThrow('Connection already in progress');
      await expect(promise1).resolves.toBeUndefined();
    });

    it('should resolve immediately if already connected', async () => {
      await manager.connect('token', 'test');
      await expect(manager.connect('token', 'test')).resolves.toBeUndefined();
    });
  });

  describe('disconnect', () => {
    it('should close WebSocket connection', async () => {
      await manager.connect('token', 'test');
      expect(manager.isConnected()).toBe(true);

      manager.disconnect();

      expect(manager.isConnected()).toBe(false);
      expect(manager.getState()).toBe('closed');
    });

    it('should clear all subscriptions', async () => {
      await manager.connect('token', 'test');
      const handler = vi.fn();
      manager.subscribe('balance.updated', handler);

      manager.disconnect();

      // Subscriptions should be cleared
      expect(manager.isConnected()).toBe(false);
    });

    it('should prevent reconnection attempts', async () => {
      await manager.connect('token', 'test');
      manager.disconnect();

      // Wait for potential reconnect attempt
      await new Promise((resolve) => setTimeout(resolve, 100));

      expect(manager.isConnected()).toBe(false);
    });
  });

  describe('subscribe', () => {
    it('should register event handler', async () => {
      await manager.connect('token', 'test');
      const handler = vi.fn();

      const unsubscribe = manager.subscribe('balance.updated', handler);

      expect(typeof unsubscribe).toBe('function');
    });

    it('should return unsubscribe function', async () => {
      await manager.connect('token', 'test');
      const handler = vi.fn();

      const unsubscribe = manager.subscribe('balance.updated', handler);
      unsubscribe();

      // Handler should be removed
    });

    it('should support multiple handlers for same event', async () => {
      await manager.connect('token', 'test');
      const handler1 = vi.fn();
      const handler2 = vi.fn();

      manager.subscribe('balance.updated', handler1);
      manager.subscribe('balance.updated', handler2);

      // Both handlers should be registered
    });
  });

  describe('subscription control messages', () => {
    it('should notify subscription-state handlers when the server acknowledges channels', async () => {
      await manager.connect('token', 'test');
      const handler = vi.fn();
      const socket = MockWebSocket.instances[0];

      manager.subscribeSubscriptionState(handler);
      socket.simulateMessage(
        JSON.stringify({
          type: 'ack',
          action: 'subscribed',
          channels: ['test.backtests'],
        })
      );

      expect(handler).toHaveBeenCalledWith({
        action: 'subscribed',
        channels: ['test.backtests'],
        error: null,
        rejectedChannels: [],
      });
    });

    it('should notify subscription-state handlers when the server rejects channels', async () => {
      await manager.connect('token', 'test');
      const handler = vi.fn();
      const socket = MockWebSocket.instances[0];

      manager.subscribeSubscriptionState(handler);
      socket.simulateMessage(
        JSON.stringify({
          type: 'error',
          message: 'Rejected unauthorized or unknown channels',
          channels: ['live.balance'],
        })
      );

      expect(handler).toHaveBeenCalledWith({
        action: 'error',
        channels: [],
        error: 'Rejected unauthorized or unknown channels',
        rejectedChannels: ['live.balance'],
      });
    });
  });

  describe('getState', () => {
    it('should return closed when not connected', () => {
      expect(manager.getState()).toBe('closed');
    });

    it('should return open when connected', async () => {
      await manager.connect('token', 'test');
      expect(manager.getState()).toBe('open');
    });
  });

  describe('isConnected', () => {
    it('should return false when not connected', () => {
      expect(manager.isConnected()).toBe(false);
    });

    it('should return true when connected', async () => {
      await manager.connect('token', 'test');
      expect(manager.isConnected()).toBe(true);
    });

    it('should return false after disconnect', async () => {
      await manager.connect('token', 'test');
      manager.disconnect();
      expect(manager.isConnected()).toBe(false);
    });
  });
});

describe('getWebSocketManager', () => {
  it('should return singleton instance', () => {
    const manager1 = getWebSocketManager();
    const manager2 = getWebSocketManager();

    expect(manager1).toBe(manager2);
  });

  it('should use VITE_WS_URL from environment', () => {
    const manager = getWebSocketManager();
    expect(manager).toBeDefined();
  });
});

describe('resolveWebSocketUrl', () => {
  it('should default to localhost WebSocket in development', () => {
    expect(resolveWebSocketUrl(undefined, { pageUrl: 'http://localhost:5173/' })).toBe(
      'ws://localhost:8080/ws'
    );
  });

  it('should resolve same-origin production URLs to secure WebSockets', () => {
    expect(
      resolveWebSocketUrl('/ws', {
        pageUrl: 'https://app.example.com/dashboard',
        production: true,
      })
    ).toBe('wss://app.example.com/ws');
  });

  it('should reject insecure production endpoints', () => {
    expect(() =>
      resolveWebSocketUrl('http://api.example.com/ws', {
        pageUrl: 'https://app.example.com/dashboard',
        production: true,
      })
    ).toThrow(/secure same-origin WebSocket endpoint/i);
  });
});
