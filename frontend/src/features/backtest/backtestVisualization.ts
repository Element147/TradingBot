import type { BacktestDetails, BacktestEquityPoint, BacktestTradeSeriesItem } from './backtestApi';
import type { MonthlyReturnCell } from './MonthlyReturnsHeatmap';
import type { HistogramBin } from './TradeDistributionHistogram';

import type { DrawdownPoint } from '@/components/charts/DrawdownChart';
import type { EquityCurvePoint } from '@/components/charts/EquityCurve';

export const createEquityCurve = (
  details: BacktestDetails,
  equityCurve: BacktestEquityPoint[]
): EquityCurvePoint[] => {
  if (equityCurve.length > 0) {
    return equityCurve.map((point) => ({
      timestamp: point.timestamp,
      equity: point.equity,
    }));
  }

  return [
    { timestamp: details.startDate, equity: details.initialBalance },
    { timestamp: details.endDate, equity: details.finalBalance },
  ];
};

export const createDrawdownCurve = (equity: EquityCurvePoint[]): DrawdownPoint[] => {
  let peak = equity[0]?.equity ?? 0;
  return equity.map((point) => {
    peak = Math.max(peak, point.equity);
    const drawdownPct = peak === 0 ? 0 : ((peak - point.equity) / peak) * 100;
    return {
      timestamp: point.timestamp,
      drawdownPct,
    };
  });
};

export const createMonthlyReturns = (equity: EquityCurvePoint[]): MonthlyReturnCell[] => {
  const grouped = new Map<string, { start: number; end: number }>();
  equity.forEach((point) => {
    const date = new Date(point.timestamp);
    const key = `${date.getUTCFullYear()}-${date.getUTCMonth() + 1}`;
    const existing = grouped.get(key);
    if (!existing) {
      grouped.set(key, { start: point.equity, end: point.equity });
      return;
    }
    existing.end = point.equity;
  });

  return Array.from(grouped.entries()).map(([key, value]) => {
    const [yearStr, monthStr] = key.split('-');
    const returnPct = value.start === 0 ? 0 : ((value.end - value.start) / value.start) * 100;
    return {
      year: Number(yearStr),
      month: Number(monthStr),
      returnPct,
    };
  });
};

export const createTradeDistribution = (tradeSeries: BacktestTradeSeriesItem[]): HistogramBin[] => {
  const bins = [
    { rangeLabel: '< -5%', count: 0 },
    { rangeLabel: '-5% to -2%', count: 0 },
    { rangeLabel: '-2% to 0%', count: 0 },
    { rangeLabel: '0% to 2%', count: 0 },
    { rangeLabel: '2% to 5%', count: 0 },
    { rangeLabel: '> 5%', count: 0 },
  ];

  tradeSeries.forEach((trade) => {
    const value = trade.returnPct;
    if (value < -5) {
      bins[0].count += 1;
    } else if (value < -2) {
      bins[1].count += 1;
    } else if (value < 0) {
      bins[2].count += 1;
    } else if (value < 2) {
      bins[3].count += 1;
    } else if (value <= 5) {
      bins[4].count += 1;
    } else {
      bins[5].count += 1;
    }
  });

  return bins;
};
