import { Box, Stack, Typography } from '@mui/material';
import {
  Area,
  AreaChart,
  Brush,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { buildTelemetryChartRows } from './backtestTelemetry';
import type { BacktestRegime, BacktestSymbolTelemetry } from './backtestTypes';

import { ChartContainer } from '@/components/charts/ChartContainer';

const regimeColors: Record<BacktestRegime, string> = {
  WARMUP: '#94a3b8',
  RANGE: '#d97706',
  TREND_UP: '#15803d',
  TREND_DOWN: '#dc2626',
};

interface BacktestExposureChartProps {
  series: BacktestSymbolTelemetry;
}

function RegimeMarker({
  cx = 0,
  cy = 0,
  payload,
}: {
  cx?: number;
  cy?: number;
  payload?: { regime?: BacktestRegime };
}) {
  if (!Number.isFinite(cx) || !Number.isFinite(cy)) {
    return null;
  }

  const color = regimeColors[payload?.regime ?? 'WARMUP'];
  return <rect x={cx - 5} y={cy - 5} width={10} height={10} rx={2} fill={color} />;
}

export function BacktestExposureChart({ series }: BacktestExposureChartProps) {
  const chartRows = buildTelemetryChartRows(series);
  const regimeRows = chartRows.map((row) => ({
    timestamp: row.timestamp,
    regime: row.regime as BacktestRegime,
    regimeBand: -112,
  }));
  const rows = chartRows.map((row) => [
    row.timestamp,
    row.exposurePct.toFixed(2),
    row.regime,
    row.actionLabels,
  ]);
  const regimesPresent = Array.from(new Set(chartRows.map((row) => row.regime as BacktestRegime)));

  return (
    <ChartContainer
      title={`Exposure and Regime - ${series.symbol}`}
      tooltipText="Exposure shows when capital was deployed long or short. The regime strip summarizes a generic trend-vs-range classification built from ADX and a 200-period EMA."
      description="Positive values indicate long exposure, negative values indicate short exposure, and the bottom strip shows the reconstructed market regime."
      headers={['Timestamp', 'Exposure %', 'Regime', 'Actions']}
      rows={rows}
      csvFileName={`backtest-exposure-${series.symbol.replaceAll('/', '-')}.csv`}
      pngFileName={`backtest-exposure-${series.symbol.replaceAll('/', '-')}.png`}
      chart={
        <Box>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 1 }}>
            {regimesPresent.map((regime) => (
              <Stack key={regime} direction="row" spacing={0.75} alignItems="center">
                <Box
                  sx={{
                    width: 10,
                    height: 10,
                    borderRadius: 0.5,
                    bgcolor: regimeColors[regime],
                  }}
                />
                <Typography variant="caption" color="text.secondary">
                  {regime}
                </Typography>
              </Stack>
            ))}
          </Stack>
          <Box sx={{ width: '100%', height: { xs: 320, md: 360 } }}>
            <ResponsiveContainer>
              <AreaChart data={chartRows}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis
                  dataKey="timestamp"
                  tickFormatter={(value) => String(value ?? '').slice(0, 10)}
                  minTickGap={32}
                />
                <YAxis domain={[-120, 120]} tickFormatter={(value) => `${Number(value ?? 0).toFixed(0)}%`} />
                <Tooltip
                  formatter={(value, name, item) => {
                    if (name === 'regimeBand') {
                      return [item.payload.regime, 'Regime'];
                    }
                    return [`${Number(value ?? 0).toFixed(2)}%`, 'Exposure'];
                  }}
                  labelFormatter={(label) => new Date(String(label ?? '')).toLocaleString()}
                />
                <ReferenceLine y={0} stroke="#64748b" strokeDasharray="4 4" />
                <Area
                  type="stepAfter"
                  dataKey="exposurePct"
                  stroke="#2563eb"
                  fill="#bfdbfe"
                  strokeWidth={2}
                />
                <Scatter
                  data={regimeRows}
                  dataKey="regimeBand"
                  fill="#94a3b8"
                  shape={(props) => <RegimeMarker {...props} />}
                />
                <Brush dataKey="timestamp" height={22} />
              </AreaChart>
            </ResponsiveContainer>
          </Box>
        </Box>
      }
    />
  );
}
