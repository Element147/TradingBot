import type {
  BacktestActionMarker,
  BacktestActionType,
  BacktestIndicatorSeries,
  BacktestSymbolTelemetry,
  BacktestTradeSeriesItem,
  BacktestTradeSide,
} from './backtestTypes';

export type BacktestMarkerFilter = 'ALL' | 'LONG' | 'SHORT' | 'EXITS' | 'FORCED';
export type WorkspaceMarkerCategory = 'ENTRY' | 'EXIT' | 'FORCED';

export interface WorkspaceTrade {
  id: string;
  index: number;
  symbol: string;
  side: BacktestTradeSide;
  entryTime: string;
  exitTime: string;
  entryPrice: number;
  exitPrice: number;
  quantity: number;
  entryValue: number;
  exitValue: number;
  returnPct: number;
  pnlValue: number;
  durationMinutes: number;
  entryAction: BacktestActionType;
  exitAction: BacktestActionType;
  entryLabel: string;
  exitLabel: string;
  entryMarkerId: string;
  exitMarkerId: string;
}

export interface WorkspaceMarker {
  id: string;
  tradeId: string;
  timestamp: string;
  action: BacktestActionType;
  price: number;
  label: string;
  side: BacktestTradeSide;
  category: WorkspaceMarkerCategory;
  isForced: boolean;
}

export interface WorkspaceOverlayLegendItem {
  key: string;
  label: string;
  pane: BacktestIndicatorSeries['pane'];
  color: string;
}

const ACTION_COLORS: Record<BacktestActionType, string> = {
  BUY: '#1f8a5a',
  SELL: '#1f66d1',
  SHORT: '#c24a3e',
  COVER: '#d38a1d',
};

const OVERLAY_COLORS = [
  '#1f8a5a',
  '#c7771a',
  '#2d6fdd',
  '#8c5ed8',
  '#d1465d',
  '#1297ab',
];

const isForcedLabel = (label: string) =>
  /forced|stop|breaker|liquidat|closeout/i.test(label);

const actionLookupKey = (
  action: BacktestActionType,
  timestamp: string,
  price: number
) => `${action}|${timestamp}|${price.toFixed(6)}`;

const findActionLabel = (
  actionLookup: Map<string, BacktestActionMarker>,
  action: BacktestActionType,
  timestamp: string,
  price: number,
  fallback: string
) => {
  const exactMatch = actionLookup.get(actionLookupKey(action, timestamp, price));
  return exactMatch?.label ?? fallback;
};

export const buildWorkspaceTrades = (
  tradeSeries: BacktestTradeSeriesItem[],
  symbol: string,
  actions: BacktestActionMarker[]
): WorkspaceTrade[] => {
  const actionLookup = buildActionLookup(actions);

  return tradeSeries
    .filter((trade) => trade.symbol === symbol)
    .map((trade, index) => {
      const tradeId = `${trade.symbol}-${trade.entryTime}-${index}`;
      const entryAction = trade.side === 'SHORT' ? 'SHORT' : 'BUY';
      const exitAction = trade.side === 'SHORT' ? 'COVER' : 'SELL';
      const entryLabel = findActionLabel(
        actionLookup,
        entryAction,
        trade.entryTime,
        trade.entryPrice,
        trade.side === 'SHORT' ? 'Short entry' : 'Long entry'
      );
      const exitLabel = findActionLabel(
        actionLookup,
        exitAction,
        trade.exitTime,
        trade.exitPrice,
        trade.side === 'SHORT' ? 'Cover short' : 'Exit long'
      );

      return {
        id: tradeId,
        index,
        symbol: trade.symbol,
        side: trade.side,
        entryTime: trade.entryTime,
        exitTime: trade.exitTime,
        entryPrice: trade.entryPrice,
        exitPrice: trade.exitPrice,
        quantity: trade.quantity,
        entryValue: trade.entryValue,
        exitValue: trade.exitValue,
        returnPct: trade.returnPct,
        pnlValue: trade.exitValue - trade.entryValue,
        durationMinutes: Math.max(
          0,
          Math.round(
            (new Date(trade.exitTime).getTime() - new Date(trade.entryTime).getTime()) / 60000
          )
        ),
        entryAction,
        exitAction,
        entryLabel,
        exitLabel,
        entryMarkerId: `${tradeId}:entry`,
        exitMarkerId: `${tradeId}:exit`,
      };
    });
};

export const buildWorkspaceMarkers = (trades: WorkspaceTrade[]): WorkspaceMarker[] =>
  trades.flatMap((trade) => {
    const entryForced = isForcedLabel(trade.entryLabel);
    const exitForced = isForcedLabel(trade.exitLabel);

    return [
      {
        id: trade.entryMarkerId,
        tradeId: trade.id,
        timestamp: trade.entryTime,
        action: trade.entryAction,
        price: trade.entryPrice,
        label: trade.entryLabel,
        side: trade.side,
        category: entryForced ? 'FORCED' : 'ENTRY',
        isForced: entryForced,
      },
      {
        id: trade.exitMarkerId,
        tradeId: trade.id,
        timestamp: trade.exitTime,
        action: trade.exitAction,
        price: trade.exitPrice,
        label: trade.exitLabel,
        side: trade.side,
        category: exitForced ? 'FORCED' : 'EXIT',
        isForced: exitForced,
      },
    ];
  });

export const condenseMarkersForChart = (
  markers: WorkspaceMarker[],
  selectedMarkerId: string | null,
  maxMarkers = 480
): WorkspaceMarker[] => {
  if (markers.length <= maxMarkers) {
    return markers;
  }

  const selectedMarkerIndex =
    selectedMarkerId === null ? -1 : markers.findIndex((marker) => marker.id === selectedMarkerId);
  const prioritizedIndices = new Set<number>();

  if (markers.length > 0) {
    prioritizedIndices.add(0);
    prioritizedIndices.add(markers.length - 1);
  }
  if (selectedMarkerIndex >= 0) {
    prioritizedIndices.add(selectedMarkerIndex);
  }

  markers.forEach((marker, index) => {
    if (marker.isForced && prioritizedIndices.size < maxMarkers) {
      prioritizedIndices.add(index);
    }
  });

  const remainingSlots = Math.max(maxMarkers - prioritizedIndices.size, 0);
  const sampleStep = Math.max(Math.ceil(markers.length / Math.max(remainingSlots, 1)), 1);
  for (let index = 0; index < markers.length && prioritizedIndices.size < maxMarkers; index += sampleStep) {
    prioritizedIndices.add(index);
  }
  for (let index = 0; index < markers.length && prioritizedIndices.size < maxMarkers; index += 1) {
    prioritizedIndices.add(index);
  }

  return Array.from(prioritizedIndices)
    .sort((left, right) => left - right)
    .map((index) => markers[index])
    .filter((marker): marker is WorkspaceMarker => marker !== undefined);
};

export const markerMatchesFilter = (
  marker: WorkspaceMarker,
  filter: BacktestMarkerFilter
) => {
  switch (filter) {
    case 'LONG':
      return marker.side === 'LONG' && marker.category === 'ENTRY';
    case 'SHORT':
      return marker.side === 'SHORT' && marker.category === 'ENTRY';
    case 'EXITS':
      return marker.category === 'EXIT' || marker.category === 'FORCED';
    case 'FORCED':
      return marker.isForced || marker.category === 'FORCED';
    default:
      return true;
  }
};

export const findTradeByMarkerId = (
  trades: WorkspaceTrade[],
  markerId: string | null
) => {
  if (!markerId) {
    return null;
  }

  return trades.find(
    (trade) => trade.entryMarkerId === markerId || trade.exitMarkerId === markerId
  ) ?? null;
};

export const findMarkerById = (
  markers: WorkspaceMarker[],
  markerId: string | null
) => {
  if (!markerId) {
    return null;
  }

  return markers.find((marker) => marker.id === markerId) ?? null;
};

export const deriveWorkspaceFocusTime = (
  marker: WorkspaceMarker | null,
  trade: WorkspaceTrade | null
) => marker?.timestamp ?? trade?.entryTime ?? null;

export const buildOverlayLegend = (series: BacktestSymbolTelemetry): WorkspaceOverlayLegendItem[] =>
  series.indicators.map((indicator, index) => ({
    key: indicator.key,
    label: indicator.label,
    pane: indicator.pane,
    color: OVERLAY_COLORS[index % OVERLAY_COLORS.length],
  }));

export const buildOverlayColorLookup = (series: BacktestSymbolTelemetry) =>
  series.indicators.reduce<Map<string, string>>((lookup, indicator, index) => {
    lookup.set(indicator.key, OVERLAY_COLORS[index % OVERLAY_COLORS.length]);
    return lookup;
  }, new Map());

export const getOverlayColor = (overlayKey: string, series: BacktestSymbolTelemetry) =>
  buildOverlayColorLookup(series).get(overlayKey) ?? OVERLAY_COLORS[0];

export const actionVisuals = {
  BUY: {
    color: ACTION_COLORS.BUY,
    label: 'BUY',
    legendShape: 'up' as const,
  },
  SELL: {
    color: ACTION_COLORS.SELL,
    label: 'SELL',
    legendShape: 'square' as const,
  },
  SHORT: {
    color: ACTION_COLORS.SHORT,
    label: 'SHORT',
    legendShape: 'down' as const,
  },
  COVER: {
    color: ACTION_COLORS.COVER,
    label: 'COVER',
    legendShape: 'diamond' as const,
  },
};

export const getDefaultVisibleOverlayKeys = (series: BacktestSymbolTelemetry) =>
  series.indicators
    .filter((indicator) => indicator.pane === 'PRICE')
    .slice(0, 3)
    .map((indicator) => indicator.key);

export const summarizeMarkerFilter = (
  filter: BacktestMarkerFilter,
  totalMarkers: number
) => {
  switch (filter) {
    case 'LONG':
      return `${totalMarkers} long-entry markers`;
    case 'SHORT':
      return `${totalMarkers} short-entry markers`;
    case 'EXITS':
      return `${totalMarkers} exit markers`;
    case 'FORCED':
      return `${totalMarkers} forced or flagged exits`;
    default:
      return `${totalMarkers} markers in view`;
  }
};

export const buildActionLookup = (actions: BacktestActionMarker[]) =>
  actions.reduce<Map<string, BacktestActionMarker>>((lookup, action) => {
    lookup.set(actionLookupKey(action.action, action.timestamp, action.price), action);
    return lookup;
  }, new Map());
