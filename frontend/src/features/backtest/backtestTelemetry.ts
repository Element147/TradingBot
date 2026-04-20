import type {
  BacktestComparisonResponse,
  BacktestIndicatorSeries,
  BacktestIndicatorPane,
  BacktestSymbolTelemetry,
} from './backtestTypes';

export interface TelemetryChartRow {
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  exposurePct: number;
  regime: string;
  actionLabels: string;
  [key: string]: number | string;
}

export const getTelemetryIndicatorsByPane = (
  series: BacktestSymbolTelemetry,
  pane: BacktestIndicatorPane
): BacktestIndicatorSeries[] => series.indicators.filter((indicator) => indicator.pane === pane);

export const getPreferredTelemetrySymbol = (
  symbol: string,
  availableSymbols: string[]
): string | null => {
  if (availableSymbols.length === 0) {
    return null;
  }

  const matchingSymbol = availableSymbols.find((entry) => entry === symbol);
  if (matchingSymbol) {
    return matchingSymbol;
  }

  return availableSymbols[0] ?? null;
};

export const buildTelemetryChartRows = (series: BacktestSymbolTelemetry): TelemetryChartRow[] => {
  const actionMap = new Map<string, string[]>();
  series.actions.forEach((action) => {
    const existing = actionMap.get(action.timestamp) ?? [];
    existing.push(`${action.action}: ${action.label}`);
    actionMap.set(action.timestamp, existing);
  });

  const indicatorValueMaps = series.indicators.reduce<Record<string, Map<string, number | null>>>(
    (accumulator, indicator) => {
      accumulator[indicator.key] = new Map(
        indicator.points.map((point) => [point.timestamp, point.value])
      );
      return accumulator;
    },
    {}
  );

  return series.points.map((point) => {
    const baseRow: TelemetryChartRow = {
      timestamp: point.timestamp,
      open: point.open,
      high: point.high,
      low: point.low,
      close: point.close,
      volume: point.volume,
      exposurePct: point.exposurePct,
      regime: point.regime,
      actionLabels: (actionMap.get(point.timestamp) ?? []).join(' | '),
    };

    series.indicators.forEach((indicator) => {
      const value = indicatorValueMaps[indicator.key]?.get(point.timestamp) ?? null;
      baseRow[indicator.key] = value ?? Number.NaN;
    });

    return baseRow;
  });
};

export const buildComparisonChartRows = (comparison: BacktestComparisonResponse) =>
  comparison.items.map((item) => ({
    label: `#${item.id} ${item.strategyId}`,
    shortLabel: `#${item.id}`,
    totalReturnPercent: item.totalReturnPercent,
    finalBalanceDelta: item.finalBalanceDelta,
    maxDrawdown: item.maxDrawdown,
    sharpeRatio: item.sharpeRatio,
  }));
