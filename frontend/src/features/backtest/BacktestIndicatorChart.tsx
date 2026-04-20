import { Box } from '@mui/material';
import {
  Brush,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { buildTelemetryChartRows, getTelemetryIndicatorsByPane } from './backtestTelemetry';
import type { BacktestSymbolTelemetry } from './backtestTypes';

import { ChartContainer } from '@/components/charts/ChartContainer';

const oscillatorColors = ['#7c3aed', '#ea580c', '#0891b2', '#16a34a'];

interface BacktestIndicatorChartProps {
  series: BacktestSymbolTelemetry;
}

export function BacktestIndicatorChart({ series }: BacktestIndicatorChartProps) {
  const oscillatorIndicators = getTelemetryIndicatorsByPane(series, 'OSCILLATOR');
  const chartRows = buildTelemetryChartRows(series);

  if (oscillatorIndicators.length === 0) {
    return null;
  }

  const rows = chartRows.map((row) => [
    row.timestamp,
    ...oscillatorIndicators.map((indicator) => {
      const value = row[indicator.key];
      return typeof value === 'number' && !Number.isNaN(value) ? value.toFixed(4) : '';
    }),
  ]);

  return (
    <ChartContainer
      title={`Indicator Telemetry - ${series.symbol}`}
      tooltipText="Secondary indicators used by the strategy logic, separated from the main price chart so overlays stay readable."
      description="Oscillator and risk filters reconstructed from the selected strategy's indicator set."
      headers={['Timestamp', ...oscillatorIndicators.map((indicator) => indicator.label)]}
      rows={rows}
      csvFileName={`backtest-indicators-${series.symbol.replaceAll('/', '-')}.csv`}
      pngFileName={`backtest-indicators-${series.symbol.replaceAll('/', '-')}.png`}
      chart={
        <Box sx={{ width: '100%', height: { xs: 300, md: 360 } }}>
          <ResponsiveContainer>
            <LineChart data={chartRows}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis
                dataKey="timestamp"
                tickFormatter={(value) => String(value ?? '').slice(0, 10)}
                minTickGap={32}
              />
              <YAxis />
              <Tooltip
                formatter={(value, name) => {
                  if (typeof value !== 'number' || Number.isNaN(value)) {
                    return ['n/a', name];
                  }
                  return [value.toFixed(4), name];
                }}
                labelFormatter={(label) => new Date(String(label ?? '')).toLocaleString()}
              />
              <Legend />
              {oscillatorIndicators.map((indicator, index) => (
                <Line
                  key={indicator.key}
                  type="monotone"
                  dataKey={indicator.key}
                  name={indicator.label}
                  connectNulls={false}
                  stroke={oscillatorColors[index % oscillatorColors.length]}
                  strokeWidth={1.7}
                  dot={false}
                />
              ))}
              <Brush dataKey="timestamp" height={22} />
            </LineChart>
          </ResponsiveContainer>
        </Box>
      }
    />
  );
}
