export type BacktestPerformanceBudgetKey =
  | 'routeLoadMs'
  | 'chartMountMs'
  | 'largeQueryRenderMs';

export type BacktestPerformanceBudget = {
  key: BacktestPerformanceBudgetKey;
  label: string;
  limitMs: number;
  description: string;
};

export type BacktestPerformanceMeasurement = {
  key: BacktestPerformanceBudgetKey;
  durationMs: number;
};

export type BacktestPerformanceBudgetResult = BacktestPerformanceBudget & {
  durationMs: number;
  pass: boolean;
  marginMs: number;
};

export const backtestPerformanceBudgets: BacktestPerformanceBudget[] = [
  {
    key: 'routeLoadMs',
    label: 'Route load time',
    limitMs: 850,
    description:
      'Time to the workspace heading appearing in the repeatable jsdom profile scenario.',
  },
  {
    key: 'chartMountMs',
    label: 'Chart mount time',
    limitMs: 40,
    description:
      'Time for the workspace chart wrapper to finish setup in the repeatable mocked chart harness.',
  },
  {
    key: 'largeQueryRenderMs',
    label: 'Large-query render time',
    limitMs: 1400,
    description:
      'Time for the trade review surface to render a representative large result set and expose its table.',
  },
];

export const evaluateBacktestPerformanceBudgets = (
  measurements: BacktestPerformanceMeasurement[]
): BacktestPerformanceBudgetResult[] =>
  backtestPerformanceBudgets.map((budget) => {
    const measurement = measurements.find((entry) => entry.key === budget.key);
    const durationMs = measurement?.durationMs ?? Number.POSITIVE_INFINITY;

    return {
      ...budget,
      durationMs,
      pass: durationMs <= budget.limitMs,
      marginMs: budget.limitMs - durationMs,
    };
  });
