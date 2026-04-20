import { Box } from '@mui/material';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  Brush,
} from 'recharts';

import { ChartContainer } from './ChartContainer';

export interface DrawdownPoint {
  timestamp: string;
  drawdownPct: number;
}

interface DrawdownChartProps {
  points: DrawdownPoint[];
  maxDrawdownLimitPct?: number;
}

export function DrawdownChart({ points, maxDrawdownLimitPct = 25 }: DrawdownChartProps) {
  const rows = points.map((point) => [point.timestamp, point.drawdownPct.toFixed(2)]);

  return (
    <ChartContainer
      title="Drawdown Curve"
      tooltipText="Shows how far equity falls from prior peaks. Lower drawdown is safer; sustained or deep drawdowns can indicate strategy fragility."
      description="Percent decline from previous equity peaks."
      headers={['Timestamp', 'Drawdown %']}
      rows={rows}
      csvFileName="drawdown-curve.csv"
      pngFileName="drawdown-curve.png"
      chart={
        <Box sx={{ width: '100%', height: { xs: 320, md: 420 } }}>
          <ResponsiveContainer>
            <AreaChart data={points}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="timestamp" tickFormatter={(value) => String(value ?? '').slice(0, 10)} />
              <YAxis tickFormatter={(value) => `${Number(value ?? 0).toFixed(1)}%`} />
              <Tooltip
                formatter={(value) => `${Number(value ?? 0).toFixed(2)}%`}
                labelFormatter={(label) => new Date(String(label ?? '')).toLocaleString()}
              />
              <ReferenceLine y={maxDrawdownLimitPct} stroke="#d32f2f" strokeDasharray="4 4" />
              <Area
                type="monotone"
                dataKey="drawdownPct"
                stroke="#ed6c02"
                fill="#ffe0b2"
                strokeWidth={2}
              />
              <Brush dataKey="timestamp" height={22} />
            </AreaChart>
          </ResponsiveContainer>
        </Box>
      }
    />
  );
}
