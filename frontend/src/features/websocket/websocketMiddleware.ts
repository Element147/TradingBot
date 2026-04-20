/**
 * WebSocket Redux Middleware
 * 
 * Integrates WebSocket events with Redux store by:
 * - Subscribing to WebSocket events
 * - Dispatching Redux actions on event receipt
 * - Implementing event throttling (max 1 update per second per type)
 * - Pausing event processing when tab is inactive
 * 
 * Requirements: 15.3, 15.4, 15.5, 15.11, 15.12
 */

import type { Middleware } from '@reduxjs/toolkit';

import {
  getWebSocketManager,
  type BacktestProgressEventData,
  type MarketDataImportProgressEventData,
  type WebSocketEvent,
  type WebSocketEventType,
} from '../../services/websocket';
import { accountApi } from '../account/accountApi';
import { backtestApi } from '../backtest/backtestApi';
import { marketDataApi } from '../marketData/marketDataApi';
import { riskApi } from '../risk/riskApi';
import { strategiesApi } from '../strategies/strategiesApi';
import { tradesApi } from '../trades/tradesApi';

import { eventReceived } from './websocketSlice';

/**
 * Throttle state for event types
 */
interface ThrottleState {
  lastUpdate: number;
  pendingEvent: WebSocketEvent | null;
  timeoutId: ReturnType<typeof setTimeout> | null;
}

/**
 * WebSocket middleware for Redux integration
 * 
 * Handles WebSocket events and dispatches appropriate Redux actions:
 * - balance.updated: Invalidates balance cache
 * - trade.executed: Invalidates balance and performance cache
 * - position.updated: Invalidates balance cache
 * - strategy.status: Invalidates strategy caches
 * - risk.alert: Invalidates risk caches
 * - system.error: Logs operator-visible system failures
 * - backtest.progress: Streams live backtest telemetry into RTK Query caches
 * - marketData.import.progress: Streams live import-job telemetry into RTK Query caches
 */

const isTerminalBacktestStatus = (status: BacktestProgressEventData['executionStatus']) =>
  status === 'COMPLETED' || status === 'FAILED';

const isTerminalMarketDataStatus = (status: MarketDataImportProgressEventData['status']) =>
  status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED';

const applyBacktestProgressToHistoryItem = (
  draft: BacktestProgressEventData,
  update: BacktestProgressEventData
) => {
  draft.executionStatus = update.executionStatus;
  draft.validationStatus = update.validationStatus;
  draft.progressPercent = update.progressPercent;
  draft.processedCandles = update.processedCandles;
  draft.totalCandles = update.totalCandles;
  draft.currentDataTimestamp = update.currentDataTimestamp;
  draft.statusMessage = update.statusMessage;
  draft.lastProgressAt = update.lastProgressAt;
  draft.startedAt = update.startedAt;
  draft.completedAt = update.completedAt;
  draft.executionStage = update.executionStage;
  draft.finalBalance = update.finalBalance;
};

const applyBacktestProgressToDetails = (
  draft: BacktestProgressEventData & { errorMessage: string | null },
  update: BacktestProgressEventData
) => {
  applyBacktestProgressToHistoryItem(draft, update);
  draft.errorMessage = update.errorMessage;
};

const applyMarketDataProgressToJob = (
  draft: MarketDataImportProgressEventData,
  update: MarketDataImportProgressEventData
) => {
  draft.status = update.status;
  draft.statusMessage = update.statusMessage;
  draft.nextRetryAt = update.nextRetryAt;
  draft.currentSymbolIndex = update.currentSymbolIndex;
  draft.totalSymbols = update.totalSymbols;
  draft.currentSymbol = update.currentSymbol;
  draft.importedRowCount = update.importedRowCount;
  draft.datasetId = update.datasetId;
  draft.datasetReady = update.datasetReady;
  draft.currentChunkStart = update.currentChunkStart;
  draft.attemptCount = update.attemptCount;
  draft.updatedAt = update.updatedAt;
  draft.startedAt = update.startedAt;
  draft.completedAt = update.completedAt;
};

export const websocketMiddleware: Middleware = (storeApi) => {
  const wsManager = getWebSocketManager();
  const throttleStates = new Map<WebSocketEventType, ThrottleState>();
  const THROTTLE_INTERVAL = 1000; // 1 second
  let isTabActive = true;

  // Track tab visibility
  const handleVisibilityChange = () => {
    isTabActive = !document.hidden;
    
    if (isTabActive) {
      console.warn('[WebSocket Middleware] Tab became active, resuming event processing');
      // Process any pending throttled events when tab becomes active
      throttleStates.forEach((state) => {
        if (state.pendingEvent) {
          processEvent(state.pendingEvent);
          state.pendingEvent = null;
        }
      });
    } else {
      console.warn('[WebSocket Middleware] Tab became inactive, pausing event processing');
    }
  };

  // Add visibility change listener
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', handleVisibilityChange);
  }

  /**
   * Process WebSocket event and dispatch appropriate Redux actions
   */
  const processEvent = (event: WebSocketEvent) => {
    const dispatch = storeApi.dispatch as (action: unknown) => unknown;

    // Update last event time in websocket slice
    dispatch(
      eventReceived({
        timestamp: event.timestamp,
        type: event.type,
      })
    );

    // Handle different event types
    switch (event.type) {
      case 'balance.updated':
        console.warn('[WebSocket Middleware] Balance updated, invalidating cache');
        // Invalidate balance cache to trigger refetch
        dispatch(
          accountApi.util.invalidateTags(['Balance'])
        );
        break;

      case 'trade.executed':
        console.warn('[WebSocket Middleware] Trade executed, invalidating caches');
        // Invalidate both balance and performance caches
        dispatch(
          accountApi.util.invalidateTags(['Balance', 'Performance'])
        );
        dispatch(
          tradesApi.util.invalidateTags(['TradeHistory'])
        );
        break;

      case 'position.updated':
        console.warn('[WebSocket Middleware] Position updated, invalidating balance cache');
        // Invalidate balance cache (positions affect available balance)
        dispatch(
          accountApi.util.invalidateTags(['Balance'])
        );
        break;

      case 'strategy.status':
        console.warn('[WebSocket Middleware] Strategy status updated, invalidating strategies cache');
        dispatch(
          strategiesApi.util.invalidateTags(['Strategies'])
        );
        break;

      case 'risk.alert':
        console.warn('[WebSocket Middleware] Risk alert received:', event.data);
        dispatch(
          riskApi.util.invalidateTags(['Risk'])
        );
        break;

      case 'system.error':
        console.error('[WebSocket Middleware] System error:', event.data);
        break;

      case 'backtest.progress': {
        const progress = event.data as BacktestProgressEventData;
        console.warn(
          '[WebSocket Middleware] Backtest progress received:',
          progress.backtestId,
          progress.executionStatus,
          progress.progressPercent
        );

        dispatch(backtestApi.util.invalidateTags(['Backtests']));
        dispatch(
          backtestApi.util.updateQueryData('getBacktestExperimentSummaries', undefined, (draft) => {
            const existing = draft.find((item) => item.latestBacktestId === progress.backtestId);
            if (!existing) {
              return;
            }

            existing.latestExecutionStatus = progress.executionStatus;
            existing.latestValidationStatus = progress.validationStatus;
          })
        );
        dispatch(
          backtestApi.util.updateQueryData('getBacktestDetails', progress.backtestId, (draft) => {
            applyBacktestProgressToDetails(
              draft as unknown as BacktestProgressEventData & { errorMessage: string | null },
              progress
            );
          })
        );

        if (isTerminalBacktestStatus(progress.executionStatus)) {
          dispatch(
            backtestApi.util.invalidateTags([
              'Backtests',
              { type: 'Backtests', id: progress.backtestId },
            ])
          );
        }
        break;
      }

      case 'marketData.import.progress': {
        const progress = event.data as MarketDataImportProgressEventData;
        console.warn(
          '[WebSocket Middleware] Market-data import progress received:',
          progress.id,
          progress.status,
          progress.currentSymbol
        );

        dispatch(
          marketDataApi.util.updateQueryData('getMarketDataJobs', undefined, (draft) => {
            const existing = draft.find((job) => job.id === progress.id);
            if (existing) {
              applyMarketDataProgressToJob(
                existing as unknown as MarketDataImportProgressEventData,
                progress
              );
              return;
            }

            draft.unshift({
              id: progress.id,
              providerId: progress.providerId,
              providerLabel: progress.providerLabel,
              assetType: progress.assetType,
              datasetName: progress.datasetName,
              symbolsCsv: progress.symbolsCsv,
              timeframe: progress.timeframe,
              startDate: progress.startDate,
              endDate: progress.endDate,
              adjusted: progress.adjusted,
              regularSessionOnly: progress.regularSessionOnly,
              status: progress.status,
              statusMessage: progress.statusMessage,
              nextRetryAt: progress.nextRetryAt,
              currentSymbolIndex: progress.currentSymbolIndex,
              totalSymbols: progress.totalSymbols,
              currentSymbol: progress.currentSymbol,
              importedRowCount: progress.importedRowCount,
              datasetId: progress.datasetId,
              datasetReady: progress.datasetReady,
              currentChunkStart: progress.currentChunkStart,
              attemptCount: progress.attemptCount,
              createdAt: progress.createdAt,
              updatedAt: progress.updatedAt,
              startedAt: progress.startedAt,
              completedAt: progress.completedAt,
            });
          })
        );

        if (isTerminalMarketDataStatus(progress.status)) {
          dispatch(marketDataApi.util.invalidateTags(['MarketDataJobs']));
        }
        if (progress.status === 'COMPLETED' && progress.datasetReady) {
          dispatch(backtestApi.util.invalidateTags(['BacktestDatasets']));
        }
        break;
      }

      default:
        console.warn('[WebSocket Middleware] Unknown event type:', event.type);
    }
  };

  /**
   * Throttle event processing (max 1 update per second per type)
   */
  const throttleEvent = (event: WebSocketEvent) => {
    const eventType = event.type;
    const now = Date.now();

    // Get or create throttle state for this event type
    let state = throttleStates.get(eventType);
    if (!state) {
      state = {
        lastUpdate: 0,
        pendingEvent: null,
        timeoutId: null,
      };
      throttleStates.set(eventType, state);
    }

    const timeSinceLastUpdate = now - state.lastUpdate;

    if (timeSinceLastUpdate >= THROTTLE_INTERVAL) {
      // Enough time has passed, process immediately
      state.lastUpdate = now;
      state.pendingEvent = null;
      
      // Clear any pending timeout
      if (state.timeoutId) {
        clearTimeout(state.timeoutId);
        state.timeoutId = null;
      }

      processEvent(event);
    } else {
      // Too soon, throttle the event
      state.pendingEvent = event;

      // Schedule processing if not already scheduled
      if (!state.timeoutId) {
        const delay = THROTTLE_INTERVAL - timeSinceLastUpdate;
        state.timeoutId = setTimeout(() => {
          if (state.pendingEvent && isTabActive) {
            state.lastUpdate = Date.now();
            processEvent(state.pendingEvent);
          }
          state.pendingEvent = null;
          state.timeoutId = null;
        }, delay);
      }
    }
  };

  /**
   * Handle WebSocket events
   */
  const handleWebSocketEvent = (event: WebSocketEvent) => {
    // Pause event processing when tab is inactive
    if (!isTabActive) {
      console.warn('[WebSocket Middleware] Tab inactive, deferring event:', event.type);
      
      // Store the event to process when tab becomes active
      const state = throttleStates.get(event.type);
      if (state) {
        state.pendingEvent = event;
      } else {
        throttleStates.set(event.type, {
          lastUpdate: 0,
          pendingEvent: event,
          timeoutId: null,
        });
      }
      return;
    }

    // Throttle event processing
    throttleEvent(event);
  };

  // Subscribe to all WebSocket events
  const eventTypes: (WebSocketEventType | '*')[] = [
    'balance.updated',
    'trade.executed',
    'position.updated',
    'strategy.status',
    'risk.alert',
    'system.error',
    'backtest.progress',
    'marketData.import.progress',
  ];

  eventTypes.forEach((eventType) => {
    wsManager.subscribe(eventType, handleWebSocketEvent);
  });

  console.warn('[WebSocket Middleware] Initialized and subscribed to events');

  // Return the middleware function
  return (next) => (action) => 
    // Pass all actions through
     next(action)
  ;
};

