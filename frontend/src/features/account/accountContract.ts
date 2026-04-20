import { z } from 'zod';

import type { ApiResponse } from '@/services/openapi';
import type { Position, Trade } from '@/types/domain.types';

type RawBalanceResponse = ApiResponse<'/api/account/balance', 'get'>;
type RawPerformanceResponse = ApiResponse<'/api/account/performance', 'get'>;
type RawOpenPositionsResponse = ApiResponse<'/api/positions/open', 'get'>;
type RawRecentTradesResponse = ApiResponse<'/api/trades/recent', 'get'>;

export interface BalanceAsset {
  symbol: string;
  amount: string;
  valueUSD: string;
}

export interface BalanceData {
  total: string;
  available: string;
  locked: string;
  assets: BalanceAsset[];
  lastSync: string;
}

export interface PerformanceMetrics {
  totalProfitLoss: string;
  profitLossPercentage: string;
  winRate: string;
  tradeCount: number;
  cashRatio: string;
}

export type PerformanceTimeframe = 'today' | 'week' | 'month' | 'all';

const balanceAssetSchema = z.object({
  symbol: z.string().min(1),
  amount: z.string().min(1),
  valueUSD: z.string().min(1),
});

const balanceSchema = z.object({
  total: z.string().min(1),
  available: z.string().min(1),
  locked: z.string().min(1),
  assets: z.array(balanceAssetSchema).default([]),
  lastSync: z.string().min(1),
});

const performanceSchema = z.object({
  totalProfitLoss: z.string().min(1),
  profitLossPercentage: z.string().min(1),
  winRate: z.string().min(1),
  tradeCount: z.number().int(),
  cashRatio: z.string().min(1),
});

const openPositionSchema = z.object({
  id: z.union([z.string(), z.number()]),
  strategyId: z.string().optional(),
  strategyName: z.string().optional(),
  symbol: z.string().min(1),
  side: z.string().optional(),
  entryPrice: z.string().min(1),
  currentPrice: z.string().optional(),
  quantity: z.string().optional(),
  positionSize: z.string().optional(),
  entryTime: z.string().min(1),
  unrealizedPnL: z.string().min(1),
  unrealizedPnLPercentage: z.string().min(1),
  status: z.string().optional(),
});

const recentTradeSchema = z.object({
  id: z.union([z.string(), z.number()]),
  strategyId: z.string().optional(),
  strategyName: z.string().optional(),
  symbol: z.string().min(1),
  side: z.string().optional(),
  entryPrice: z.string().min(1),
  exitPrice: z.string().min(1),
  quantity: z.string().optional(),
  positionSize: z.string().optional(),
  entryTime: z.string().min(1),
  exitTime: z.string().min(1),
  duration: z.string().optional(),
  profitLoss: z.string().min(1),
  profitLossPercentage: z.string().min(1),
  fees: z.string().optional(),
  slippage: z.string().optional(),
  status: z.string().optional(),
});

const normalizePositionSide = (side?: string): Position['side'] =>
  side?.toUpperCase() === 'SHORT' ? 'SHORT' : 'LONG';

const normalizeTradeStatus = (status?: string): Trade['status'] =>
  status?.toUpperCase() === 'CANCELLED' ? 'CANCELLED' : 'CLOSED';

export const normalizeBalanceData = (response: RawBalanceResponse): BalanceData =>
  balanceSchema.parse(response);

export const normalizePerformanceMetrics = (
  response: RawPerformanceResponse
): PerformanceMetrics => performanceSchema.parse(response);

export const normalizeOpenPositions = (response: RawOpenPositionsResponse): Position[] =>
  z.array(openPositionSchema).parse(response).map((position) => ({
    id: String(position.id),
    strategyId: position.strategyId ?? 'unknown',
    strategyName: position.strategyName ?? 'N/A',
    symbol: position.symbol,
    side: normalizePositionSide(position.side),
    entryPrice: position.entryPrice,
    currentPrice: position.currentPrice ?? position.entryPrice,
    quantity: position.quantity ?? position.positionSize ?? '0',
    entryTime: position.entryTime,
    unrealizedPnL: position.unrealizedPnL,
    unrealizedPnLPercentage: position.unrealizedPnLPercentage,
    status: 'OPEN',
  }));

export const normalizeRecentTrades = (response: RawRecentTradesResponse): Trade[] =>
  z.array(recentTradeSchema).parse(response).map((trade) => ({
    id: String(trade.id),
    strategyId: trade.strategyId ?? 'unknown',
    strategyName: trade.strategyName ?? 'N/A',
    symbol: trade.symbol,
    side: normalizePositionSide(trade.side),
    entryPrice: trade.entryPrice,
    exitPrice: trade.exitPrice,
    quantity: trade.quantity ?? trade.positionSize ?? '0',
    entryTime: trade.entryTime,
    exitTime: trade.exitTime,
    duration: trade.duration ?? 'N/A',
    profitLoss: trade.profitLoss,
    profitLossPercentage: trade.profitLossPercentage,
    fees: trade.fees ?? '0',
    slippage: trade.slippage ?? '0',
    status: normalizeTradeStatus(trade.status),
  }));
