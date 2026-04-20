import { Box } from '@mui/material';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

import { ChartContainer } from '@/components/charts/ChartContainer';

export interface HistogramBin {
  rangeLabel: string;
  count: number;
}

interface TradeDistributionHistogramProps {
  bins: HistogramBin[];
}

export function TradeDistributionHistogram({ bins }: TradeDistributionHistogramProps) {
  const rows = bins.map((bin) => [bin.rangeLabel, bin.count]);

  return (
    <ChartContainer
      title="Trade Distribution Histogram"
      tooltipText="Shows where trades cluster by profit/loss bins. A right-skewed distribution with limited left-tail losses is generally healthier."
      description="Distribution of trade outcomes across PnL bins."
      headers={['PnL Range', 'Count']}
      rows={rows}
      csvFileName="trade-distribution.csv"
      pngFileName="trade-distribution.png"
      chart={
        <Box sx={{ width: '100%', height: { xs: 320, md: 420 } }}>
          <ResponsiveContainer>
            <BarChart data={bins}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="rangeLabel" />
              <YAxis allowDecimals={false} />
              <Tooltip />
              <Bar dataKey="count" fill="#0288d1" />
            </BarChart>
          </ResponsiveContainer>
        </Box>
      }
    />
  );
}
