import { Alert, Grid } from '@mui/material';
import { useMemo } from 'react';

import {
  useGetBacktestEquityCurveQuery,
  useGetBacktestTradeSeriesQuery,
  type BacktestDetails,
} from './backtestApi';
import {
  createDrawdownCurve,
  createEquityCurve,
  createMonthlyReturns,
  createTradeDistribution,
} from './backtestVisualization';
import { MonthlyReturnsHeatmap } from './MonthlyReturnsHeatmap';
import { TradeDistributionHistogram } from './TradeDistributionHistogram';

import { DrawdownChart } from '@/components/charts/DrawdownChart';
import { EquityCurve } from '@/components/charts/EquityCurve';
import { EmptyState } from '@/components/ui/Workbench';

interface BacktestAnalyticsPanelProps {
  details: BacktestDetails;
}

export default function BacktestAnalyticsPanel({ details }: BacktestAnalyticsPanelProps) {
  const {
    data: equitySeries = [],
    isLoading: equityLoading,
    isFetching: equityFetching,
  } = useGetBacktestEquityCurveQuery(details.id);
  const {
    data: tradeSeries = [],
    isLoading: tradesLoading,
    isFetching: tradesFetching,
  } = useGetBacktestTradeSeriesQuery(details.id);

  const equityCurve = useMemo(() => createEquityCurve(details, equitySeries), [details, equitySeries]);
  const drawdownCurve = useMemo(() => createDrawdownCurve(equityCurve), [equityCurve]);
  const monthlyReturns = useMemo(() => createMonthlyReturns(equityCurve), [equityCurve]);
  const tradeDistribution = useMemo(() => createTradeDistribution(tradeSeries), [tradeSeries]);

  if (equityLoading || tradesLoading) {
    return (
      <Alert severity="info">
        Loading analytics panels. Equity, drawdown, monthly returns, and trade distribution are
        requested only when this section is opened.
      </Alert>
    );
  }

  if (equityFetching || tradesFetching) {
    return <Alert severity="info">Refreshing analytics with the latest persisted run evidence.</Alert>;
  }

  if (equityCurve.length === 0) {
    return (
      <EmptyState
        title="No analytics were recorded"
        description="This run does not have persisted equity or trade analytics to display."
        tone="info"
      />
    );
  }

  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, lg: 6 }}>
        <EquityCurve points={equityCurve} />
      </Grid>
      <Grid size={{ xs: 12, lg: 6 }}>
        <DrawdownChart points={drawdownCurve} maxDrawdownLimitPct={25} />
      </Grid>
      <Grid size={{ xs: 12, lg: 6 }}>
        <MonthlyReturnsHeatmap data={monthlyReturns} />
      </Grid>
      <Grid size={{ xs: 12, lg: 6 }}>
        <TradeDistributionHistogram bins={tradeDistribution} />
      </Grid>
    </Grid>
  );
}
