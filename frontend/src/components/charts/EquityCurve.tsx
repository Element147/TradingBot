import { Box, Button, Stack, Typography } from '@mui/material';
import { useMemo, useState } from 'react';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  Brush,
} from 'recharts';

import { ChartContainer } from './ChartContainer';

export interface EquityCurvePoint {
  timestamp: string;
  equity: number;
}

const timeframeFilters = ['1d', '1w', '1m', '3m', '1y', 'all'] as const;
type Timeframe = (typeof timeframeFilters)[number];

const filterByTimeframe = (points: EquityCurvePoint[], timeframe: Timeframe): EquityCurvePoint[] => {
  if (timeframe === 'all' || points.length === 0) {
    return points;
  }

  const last = new Date(points[points.length - 1].timestamp).getTime();
  const windows: Record<Exclude<Timeframe, 'all'>, number> = {
    '1d': 24 * 60 * 60 * 1000,
    '1w': 7 * 24 * 60 * 60 * 1000,
    '1m': 30 * 24 * 60 * 60 * 1000,
    '3m': 90 * 24 * 60 * 60 * 1000,
    '1y': 365 * 24 * 60 * 60 * 1000,
  };
  const minTimestamp = last - windows[timeframe];
  return points.filter((point) => new Date(point.timestamp).getTime() >= minTimestamp);
};

interface EquityCurveProps {
  title?: string;
  points: EquityCurvePoint[];
}

export function EquityCurve({ title = 'Equity Curve', points }: EquityCurveProps) {
  const [timeframe, setTimeframe] = useState<Timeframe>('all');
  const filtered = useMemo(() => filterByTimeframe(points, timeframe), [points, timeframe]);

  const rows = filtered.map((point) => [point.timestamp, point.equity.toFixed(2)]);

  return (
    <ChartContainer
      title={title}
      tooltipText="Tracks account equity over time. Rising slope means compounding gains, while long flat or declining periods suggest weak or unstable strategy behavior."
      description="Account balance over time with timeframe filtering."
      headers={['Timestamp', 'Equity']}
      rows={rows}
      csvFileName="equity-curve.csv"
      pngFileName="equity-curve.png"
      chart={
        <Box>
          <Stack direction="row" spacing={1} sx={{ mb: 1, flexWrap: 'wrap' }}>
            {timeframeFilters.map((value) => (
              <Button
                key={value}
                size="small"
                variant={timeframe === value ? 'contained' : 'outlined'}
                onClick={() => setTimeframe(value)}
              >
                {value}
              </Button>
            ))}
          </Stack>
          <Box sx={{ width: '100%', height: { xs: 320, md: 420 } }}>
            <ResponsiveContainer>
              <LineChart data={filtered}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis
                  dataKey="timestamp"
                  tickFormatter={(value) => String(value ?? '').slice(0, 10)}
                  minTickGap={40}
                />
                <YAxis domain={['dataMin - 50', 'dataMax + 50']} />
                <Tooltip
                  formatter={(value) => Number(value ?? 0).toFixed(2)}
                  labelFormatter={(label) => new Date(String(label ?? '')).toLocaleString()}
                />
                <Line type="monotone" dataKey="equity" stroke="#2e7d32" strokeWidth={2} dot={false} />
                <Brush dataKey="timestamp" height={22} />
              </LineChart>
            </ResponsiveContainer>
          </Box>
          <Typography variant="caption" color="text.secondary">
            Use brush handles to zoom/pan. Choose &quot;all&quot; to reset zoom context.
          </Typography>
        </Box>
      }
    />
  );
}
