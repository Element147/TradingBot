import Decimal from 'decimal.js';

export interface PositionSizingInput {
  accountBalance: number;
  riskPercent: number;
  stopLossDistancePercent: number;
}

export interface PositionSizingResult {
  units: number;
  notional: number;
  valid: boolean;
  error?: string;
}

export const calculatePositionSizing = (
  input: PositionSizingInput
): PositionSizingResult => {
  const { accountBalance, riskPercent, stopLossDistancePercent } = input;

  if (riskPercent < 0.1 || riskPercent > 5) {
    return { valid: false, units: 0, notional: 0, error: 'Risk % must be between 0.1 and 5' };
  }
  if (stopLossDistancePercent <= 0) {
    return { valid: false, units: 0, notional: 0, error: 'Stop-loss distance must be positive' };
  }
  if (accountBalance <= 0) {
    return { valid: false, units: 0, notional: 0, error: 'Account balance must be positive' };
  }

  const balance = new Decimal(accountBalance);
  const riskFraction = new Decimal(riskPercent).div(100);
  const stopLossFraction = new Decimal(stopLossDistancePercent).div(100);
  const riskAmount = balance.mul(riskFraction);
  const notional = riskAmount.div(stopLossFraction);

  return {
    valid: true,
    units: notional.toDecimalPlaces(8).toNumber(),
    notional: notional.toDecimalPlaces(2).toNumber(),
  };
};
