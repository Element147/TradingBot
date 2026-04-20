import { Grid } from '@mui/material';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { buildComparisonChartRows } from './backtestTelemetry';
import type { BacktestComparisonResponse } from './backtestTypes';

import { ChartContainer } from '@/components/charts/ChartContainer';

interface BacktestComparisonChartsProps {
  comparison: BacktestComparisonResponse;
}

export function BacktestComparisonCharts({ comparison }: BacktestComparisonChartsProps) {
  const chartRows = buildComparisonChartRows(comparison);
  const returnRows = chartRows.map((row) => [
    row.label,
    row.totalReturnPercent.toFixed(2),
    row.finalBalanceDelta.toFixed(2),
  ]);
  const riskRows = chartRows.map((row) => [
    row.label,
    row.maxDrawdown.toFixed(2),
    row.sharpeRatio.toFixed(2),
  ]);

  return (
    <Grid container spacing={2} sx={{ mb: 2 }}>
      <Grid size={{ xs: 12, xl: 6 }}>
        <ChartContainer
          title="Comparison Returns"
          tooltipText="Return and balance delta by run. Use this view to spot which configuration actually improved versus the baseline."
          description="Return percent and ending-balance delta across the selected runs."
          headers={['Run', 'Return %', 'Balance Delta']}
          rows={returnRows}
          csvFileName={`comparison-returns-${comparison.baselineBacktestId}.csv`}
          pngFileName={`comparison-returns-${comparison.baselineBacktestId}.png`}
          chart={
            <ResponsiveContainer width="100%" height={320}>
              <BarChart data={chartRows}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="shortLabel" />
                <YAxis yAxisId="left" tickFormatter={(value) => `${Number(value ?? 0).toFixed(0)}%`} />
                <YAxis yAxisId="right" orientation="right" tickFormatter={(value) => `${Number(value ?? 0).toFixed(0)}`} />
                <Tooltip />
                <Legend />
                <Bar yAxisId="left" dataKey="totalReturnPercent" name="Return %" fill="#0f766e" />
                <Bar yAxisId="right" dataKey="finalBalanceDelta" name="Balance Delta" fill="#d97706" />
              </BarChart>
            </ResponsiveContainer>
          }
        />
      </Grid>
      <Grid size={{ xs: 12, xl: 6 }}>
        <ChartContainer
          title="Comparison Risk"
          tooltipText="Drawdown and Sharpe side by side so return improvements are judged together with downside and stability."
          description="Max drawdown and Sharpe ratio across the selected runs."
          headers={['Run', 'Max Drawdown %', 'Sharpe']}
          rows={riskRows}
          csvFileName={`comparison-risk-${comparison.baselineBacktestId}.csv`}
          pngFileName={`comparison-risk-${comparison.baselineBacktestId}.png`}
          chart={
            <ResponsiveContainer width="100%" height={320}>
              <LineChart data={chartRows}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="shortLabel" />
                <YAxis yAxisId="left" tickFormatter={(value) => `${Number(value ?? 0).toFixed(0)}%`} />
                <YAxis yAxisId="right" orientation="right" />
                <Tooltip />
                <Legend />
                <Line
                  yAxisId="left"
                  type="monotone"
                  dataKey="maxDrawdown"
                  name="Max Drawdown %"
                  stroke="#dc2626"
                  strokeWidth={2}
                />
                <Line
                  yAxisId="right"
                  type="monotone"
                  dataKey="sharpeRatio"
                  name="Sharpe"
                  stroke="#2563eb"
                  strokeWidth={2}
                />
              </LineChart>
            </ResponsiveContainer>
          }
        />
      </Grid>
    </Grid>
  );
}
