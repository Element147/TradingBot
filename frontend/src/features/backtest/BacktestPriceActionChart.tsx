import { Box } from '@mui/material';
import {
  Brush,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { buildTelemetryChartRows, getTelemetryIndicatorsByPane } from './backtestTelemetry';
import type { BacktestActionType, BacktestSymbolTelemetry } from './backtestTypes';

import { ChartContainer } from '@/components/charts/ChartContainer';

const priceIndicatorColors = ['#2f7d76', '#d97706', '#2563eb', '#8b5cf6', '#dc2626'];

const actionStyles: Record<
  BacktestActionType,
  { fill: string; stroke: string; label: string }
> = {
  BUY: { fill: '#15803d', stroke: '#0f5b2c', label: 'B' },
  SELL: { fill: '#dc2626', stroke: '#7f1d1d', label: 'S' },
  SHORT: { fill: '#7c3aed', stroke: '#4c1d95', label: 'Sh' },
  COVER: { fill: '#0f766e', stroke: '#134e4a', label: 'C' },
};

interface BacktestPriceActionChartProps {
  series: BacktestSymbolTelemetry;
}

function ActionMarker({
  cx = 0,
  cy = 0,
  fill = '#2563eb',
  stroke = '#1d4ed8',
  action = 'BUY',
}: {
  cx?: number;
  cy?: number;
  fill?: string;
  stroke?: string;
  action?: string;
}) {
  if (!Number.isFinite(cx) || !Number.isFinite(cy)) {
    return null;
  }

  const style = actionStyles[action as BacktestActionType] ?? actionStyles.BUY;

  return (
    <g transform={`translate(${cx}, ${cy})`}>
      <circle r={10} fill={fill} stroke={stroke} strokeWidth={1.5} />
      <text
        x={0}
        y={3}
        textAnchor="middle"
        fontSize="8"
        fontWeight={700}
        fill="#f8fafc"
      >
        {style.label}
      </text>
    </g>
  );
}

export function BacktestPriceActionChart({ series }: BacktestPriceActionChartProps) {
  const chartRows = buildTelemetryChartRows(series);
  const priceIndicators = getTelemetryIndicatorsByPane(series, 'PRICE');
  const scatterData = (action: BacktestActionType) =>
    series.actions
      .filter((marker) => marker.action === action)
      .map((marker) => ({
        timestamp: marker.timestamp,
        price: marker.price,
        action: marker.action,
        label: marker.label,
      }));

  const rows = chartRows.map((row) => [
    row.timestamp,
    row.close.toFixed(4),
    row.exposurePct.toFixed(2),
    row.regime,
    row.actionLabels,
  ]);

  return (
    <ChartContainer
      title={`Price and Actions - ${series.symbol}`}
      tooltipText="Close-price chart with explicit BUY, SELL, SHORT, and COVER markers plus strategy-relevant price overlays."
      description="Action markers show where the recorded trade series entered or exited. Overlays come from the selected strategy's indicator set."
      headers={['Timestamp', 'Close', 'Exposure %', 'Regime', 'Actions']}
      rows={rows}
      csvFileName={`backtest-price-actions-${series.symbol.replaceAll('/', '-')}.csv`}
      pngFileName={`backtest-price-actions-${series.symbol.replaceAll('/', '-')}.png`}
      chart={
        <Box sx={{ width: '100%', height: { xs: 360, md: 460 } }}>
          <ResponsiveContainer>
            <ComposedChart data={chartRows}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis
                dataKey="timestamp"
                tickFormatter={(value) => String(value ?? '').slice(0, 10)}
                minTickGap={32}
              />
              <YAxis
                yAxisId="price"
                domain={['dataMin - 2', 'dataMax + 2']}
                tickFormatter={(value) => Number(value ?? 0).toFixed(2)}
              />
              <Tooltip
                formatter={(value, name) => {
                  if (name === 'close') {
                    return [Number(value ?? 0).toFixed(4), 'Close'];
                  }
                  if (typeof value === 'number' && !Number.isNaN(value)) {
                    return [value.toFixed(4), name];
                  }
                  return [value, name];
                }}
                labelFormatter={(label, payload) => {
                  const base = new Date(String(label ?? '')).toLocaleString();
                  const actionSummary = payload?.[0]?.payload?.actionLabels as string | undefined;
                  return actionSummary ? `${base} | ${actionSummary}` : base;
                }}
              />
              <Legend />
              <ReferenceLine
                yAxisId="price"
                y={chartRows[0]?.close ?? 0}
                stroke="#94a3b8"
                strokeDasharray="4 4"
              />
              <Line
                yAxisId="price"
                type="monotone"
                dataKey="close"
                name="Close"
                stroke="#0f766e"
                strokeWidth={2.4}
                dot={false}
              />
              {priceIndicators.map((indicator, index) => (
                <Line
                  key={indicator.key}
                  yAxisId="price"
                  type="monotone"
                  dataKey={indicator.key}
                  name={indicator.label}
                  connectNulls={false}
                  stroke={priceIndicatorColors[index % priceIndicatorColors.length]}
                  strokeWidth={1.6}
                  dot={false}
                />
              ))}
              {(Object.keys(actionStyles) as BacktestActionType[]).map((action) => {
                const style = actionStyles[action];
                return (
                  <Scatter
                    key={action}
                    yAxisId="price"
                    name={action}
                  data={scatterData(action)}
                  dataKey="price"
                  fill={style.fill}
                  stroke={style.stroke}
                  shape={(props) => (
                    <ActionMarker
                      {...props}
                      action={action}
                      fill={style.fill}
                      stroke={style.stroke}
                    />
                  )}
                />
              );
            })}
              <Brush dataKey="timestamp" height={22} />
            </ComposedChart>
          </ResponsiveContainer>
        </Box>
      }
    />
  );
}
