import { Stack, Typography } from '@mui/material';
import { useMemo } from 'react';
import {
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Scatter,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { EmptyState, SurfacePanel } from '@/components/ui/Workbench';
import type { TradeHistoryItem } from '@/features/trades/tradesApi';

interface ForwardSignalTimelineChartProps {
  strategyName: string;
  trades: TradeHistoryItem[];
}

type SignalPoint = {
  timestamp: string;
  price: number;
  signal: string;
  markerPrice: number | null;
};

const formatTimestamp = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));

export function ForwardSignalTimelineChart({
  strategyName,
  trades,
}: ForwardSignalTimelineChartProps) {
  const signalPoints = useMemo<SignalPoint[]>(
    () =>
      trades
        .flatMap((trade) => {
          const events: SignalPoint[] = [
            {
              timestamp: trade.entryTime,
              price: trade.entryPrice,
              signal: `${trade.signal} entry`,
              markerPrice: trade.entryPrice,
            },
          ];

          if (trade.exitTime && trade.exitPrice) {
            events.push({
              timestamp: trade.exitTime,
              price: trade.exitPrice,
              signal: `${trade.positionSide} exit`,
              markerPrice: trade.exitPrice,
            });
          }

          return events;
        })
        .sort(
          (left, right) =>
            new Date(left.timestamp).getTime() - new Date(right.timestamp).getTime()
        ),
    [trades]
  );

  return (
    <SurfacePanel
      title="Signal timeline"
      description={`Recent entry and exit evidence for ${strategyName} stays visible in one investigation chart.`}
    >
      {signalPoints.length === 0 ? (
        <EmptyState
          title="No recent forward-test signals"
          description="Signal markers appear here once the selected strategy has recent trade history in the paper-safe monitoring stream."
          tone="info"
        />
      ) : (
        <Stack spacing={1}>
          <Typography variant="body2" color="text.secondary">
            The line traces observed entry and exit prices. Markers stay tied to recorded trade events rather than client-side strategy recomputation.
          </Typography>
          <ResponsiveContainer width="100%" height={320}>
            <ComposedChart data={signalPoints} margin={{ top: 12, right: 18, bottom: 8, left: 0 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis
                dataKey="timestamp"
                tickFormatter={formatTimestamp}
                minTickGap={28}
              />
              <YAxis domain={['auto', 'auto']} />
              <Tooltip
                formatter={(value, name) => [value ?? 'Unavailable', name]}
                labelFormatter={(value) => formatTimestamp(String(value))}
              />
              <Legend />
              <Line
                type="monotone"
                dataKey="price"
                name="Observed price"
                stroke="#1565c0"
                strokeWidth={2}
                dot={false}
              />
              <Scatter
                data={signalPoints.filter((point) => point.markerPrice !== null)}
                dataKey="markerPrice"
                name="Signal marker"
                fill="#ef6c00"
              />
            </ComposedChart>
          </ResponsiveContainer>
        </Stack>
      )}
    </SurfacePanel>
  );
}
