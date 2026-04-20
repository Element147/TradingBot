import type { TradeHistoryItem } from './tradesApi';

export type TradeSortField =
  | 'id'
  | 'pair'
  | 'signal'
  | 'positionSide'
  | 'entryTime'
  | 'exitTime'
  | 'entryPrice'
  | 'exitPrice'
  | 'positionSize'
  | 'pnl'
  | 'feesActual'
  | 'slippageActual';

export interface TradeStats {
  totalTrades: number;
  totalPnl: number;
  totalFees: number;
  totalSlippage: number;
  winRate: number;
  averageWin: number;
  averageLoss: number;
  profitFactor: number;
}

const numeric = (value: number | null | undefined): number => value ?? 0;

const csvValue = (value: string | number | null): string =>
  `"${String(value ?? '').replaceAll('"', '""')}"`;

export const sortTrades = (
  trades: TradeHistoryItem[],
  field: TradeSortField,
  direction: 'asc' | 'desc'
): TradeHistoryItem[] => {
  const sorted = [...trades].sort((left, right) => {
    const leftValue = left[field];
    const rightValue = right[field];

    if (typeof leftValue === 'number' || typeof rightValue === 'number') {
      return numeric(leftValue as number | null) - numeric(rightValue as number | null);
    }

    return String(leftValue ?? '').localeCompare(String(rightValue ?? ''));
  });

  if (direction === 'desc') {
    sorted.reverse();
  }

  return sorted;
};

export const calculateTradeStats = (trades: TradeHistoryItem[]): TradeStats => {
  const wins = trades.filter((trade) => trade.pnl > 0).map((trade) => trade.pnl);
  const losses = trades.filter((trade) => trade.pnl < 0).map((trade) => Math.abs(trade.pnl));
  const grossProfit = wins.reduce((sum, pnl) => sum + pnl, 0);
  const grossLoss = losses.reduce((sum, pnl) => sum + pnl, 0);

  return {
    totalTrades: trades.length,
    totalPnl: trades.reduce((sum, trade) => sum + trade.pnl, 0),
    totalFees: trades.reduce((sum, trade) => sum + trade.feesActual, 0),
    totalSlippage: trades.reduce((sum, trade) => sum + trade.slippageActual, 0),
    winRate: trades.length === 0 ? 0 : (wins.length / trades.length) * 100,
    averageWin: wins.length === 0 ? 0 : grossProfit / wins.length,
    averageLoss: losses.length === 0 ? 0 : grossLoss / losses.length,
    profitFactor: grossLoss === 0 ? 0 : grossProfit / grossLoss,
  };
};

export const calculateRMultiple = (trade: TradeHistoryItem): number | null => {
  if (
    trade.exitPrice === null ||
    trade.stopLoss === null ||
    trade.entryPrice === trade.stopLoss
  ) {
    return null;
  }

  if (trade.positionSide === 'SHORT') {
    return (trade.entryPrice - trade.exitPrice) / (trade.stopLoss - trade.entryPrice);
  }

  return (trade.exitPrice - trade.entryPrice) / (trade.entryPrice - trade.stopLoss);
};

export const buildTradesCsv = (trades: TradeHistoryItem[]): string => {
  const headers = [
    'id',
    'pair',
    'signal',
    'positionSide',
    'entryTime',
    'exitTime',
    'entryPrice',
    'exitPrice',
    'positionSize',
    'riskAmount',
    'pnl',
    'feesActual',
    'slippageActual',
    'stopLoss',
    'takeProfit',
  ];

  const rows = trades.map((trade) => [
    trade.id,
    trade.pair,
    trade.signal,
    trade.positionSide,
    trade.entryTime,
    trade.exitTime,
    trade.entryPrice.toFixed(2),
    trade.exitPrice?.toFixed(2) ?? '',
    trade.positionSize.toFixed(6),
    trade.riskAmount.toFixed(2),
    trade.pnl.toFixed(2),
    trade.feesActual.toFixed(2),
    trade.slippageActual.toFixed(2),
    trade.stopLoss?.toFixed(2) ?? '',
    trade.takeProfit?.toFixed(2) ?? '',
  ]);

  return [headers.map(csvValue).join(','), ...rows.map((row) => row.map(csvValue).join(','))].join(
    '\n'
  );
};

export const isIsoTimestamp = (value: string): boolean =>
  !Number.isNaN(new Date(value).getTime()) && /^\d{4}-\d{2}-\d{2}T/.test(value);
