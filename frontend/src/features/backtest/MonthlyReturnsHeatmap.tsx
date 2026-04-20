import { Box, Typography } from '@mui/material';

import { ChartContainer } from '@/components/charts/ChartContainer';

export interface MonthlyReturnCell {
  year: number;
  month: number;
  returnPct: number;
}

interface MonthlyReturnsHeatmapProps {
  data: MonthlyReturnCell[];
}

const monthLabels = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

const tone = (value: number): string => {
  if (value >= 5) {
    return '#2e7d32';
  }
  if (value > 0) {
    return '#66bb6a';
  }
  if (value <= -5) {
    return '#c62828';
  }
  if (value < 0) {
    return '#ef5350';
  }
  return '#90a4ae';
};

export function MonthlyReturnsHeatmap({ data }: MonthlyReturnsHeatmapProps) {
  const years = Array.from(new Set(data.map((item) => item.year))).sort((a, b) => a - b);
  const rows = data.map((item) => [item.year, monthLabels[item.month - 1], item.returnPct.toFixed(2)]);

  return (
    <ChartContainer
      title="Monthly Returns Heatmap"
      tooltipText="Highlights consistency by month. Frequent red months or extreme swings may signal regime sensitivity and unstable edge."
      description="Year-by-month return map with positive/negative color encoding."
      headers={['Year', 'Month', 'Return %']}
      rows={rows}
      csvFileName="monthly-returns.csv"
      pngFileName="monthly-returns.png"
      chart={
        <Box sx={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'separate', borderSpacing: '4px' }}>
            <thead>
              <tr>
                <th style={{ textAlign: 'left' }}>Year</th>
                {monthLabels.map((month) => (
                  <th key={month} style={{ textAlign: 'center' }}>
                    {month}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {years.map((year) => (
                <tr key={year}>
                  <td style={{ fontWeight: 700 }}>{year}</td>
                  {monthLabels.map((_, index) => {
                    const cell = data.find(
                      (item) => item.year === year && item.month === index + 1
                    );
                    const value = cell?.returnPct ?? 0;
                    return (
                      <td
                        key={`${year}-${index + 1}`}
                        style={{
                          textAlign: 'center',
                          backgroundColor: tone(value),
                          color: '#fff',
                          borderRadius: 6,
                          padding: '6px 8px',
                        }}
                      >
                        {value.toFixed(1)}%
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
          <Typography variant="caption" color="text.secondary">
            Green cells indicate positive return months, red cells indicate negative months.
          </Typography>
        </Box>
      }
    />
  );
}
