/**
 * Domain Types
 * 
 * Core domain model types for the trading application.
 * These types represent the business entities and their relationships.
 */

/**
 * Position - An open trade that has not yet been closed
 */
export type PositionSide = 'LONG' | 'SHORT';

export interface Position {
  id: string;
  strategyId: string;
  strategyName: string;
  symbol: string;
  side: PositionSide;
  entryPrice: string;
  currentPrice: string;
  quantity: string;
  entryTime: string;
  unrealizedPnL: string;
  unrealizedPnLPercentage: string;
  status: 'OPEN';
}

/**
 * Trade - A completed buy/sell transaction
 */
export interface Trade {
  id: string;
  strategyId: string;
  strategyName: string;
  symbol: string;
  side: PositionSide;
  entryPrice: string;
  exitPrice: string;
  quantity: string;
  entryTime: string;
  exitTime: string;
  duration: string;
  profitLoss: string;
  profitLossPercentage: string;
  fees: string;
  slippage: string;
  status: 'CLOSED' | 'CANCELLED';
}
