import DownloadIcon from '@mui/icons-material/Download';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import TableChartIcon from '@mui/icons-material/TableChart';
import TimelineIcon from '@mui/icons-material/Timeline';
import { Box, Button, Card, CardContent, Stack, Tooltip, Typography } from '@mui/material';
import { toPng } from 'html-to-image';
import { useRef, useState } from 'react';

interface ChartContainerProps {
  title: string;
  tooltipText?: string;
  description?: string;
  csvFileName: string;
  pngFileName: string;
  headers: string[];
  rows: Array<Array<string | number>>;
  chart: React.ReactNode;
}

const toCsv = (headers: string[], rows: Array<Array<string | number>>): string => {
  const encode = (value: string | number) => `"${String(value).replaceAll('"', '""')}"`;
  const csvRows = [headers.map(encode).join(','), ...rows.map((row) => row.map(encode).join(','))];
  return csvRows.join('\n');
};

export function ChartContainer({
  title,
  tooltipText,
  description,
  csvFileName,
  pngFileName,
  headers,
  rows,
  chart,
}: ChartContainerProps) {
  const chartRef = useRef<HTMLDivElement | null>(null);
  const [tableMode, setTableMode] = useState(false);

  const exportCsv = () => {
    const csv = toCsv(headers, rows);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = csvFileName;
    link.click();
    URL.revokeObjectURL(url);
  };

  const exportPng = async () => {
    if (!chartRef.current) {
      return;
    }

    const dataUrl = await toPng(chartRef.current, {
      pixelRatio: 2,
      cacheBust: true,
      backgroundColor: '#ffffff',
    });
    const link = document.createElement('a');
    link.href = dataUrl;
    link.download = pngFileName;
    link.click();
  };

  return (
    <Card>
      <CardContent>
        <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2} sx={{ mb: 2 }}>
          <Box>
            <Stack direction="row" spacing={0.75} alignItems="center">
              <Typography variant="h6">{title}</Typography>
              {tooltipText ? (
                <Tooltip title={tooltipText} arrow>
                  <InfoOutlinedIcon fontSize="small" color="action" sx={{ cursor: 'help' }} />
                </Tooltip>
              ) : null}
            </Stack>
            {description ? (
              <Typography variant="body2" color="text.secondary">
                {description}
              </Typography>
            ) : null}
          </Box>
          <Stack direction="row" spacing={1}>
            <Button
              variant={tableMode ? 'outlined' : 'contained'}
              startIcon={<TimelineIcon />}
              onClick={() => setTableMode(false)}
            >
              Chart
            </Button>
            <Button
              variant={tableMode ? 'contained' : 'outlined'}
              startIcon={<TableChartIcon />}
              onClick={() => setTableMode(true)}
            >
              Table
            </Button>
            <Button variant="outlined" startIcon={<DownloadIcon />} onClick={exportCsv}>
              CSV
            </Button>
            {!tableMode ? (
              <Button variant="outlined" startIcon={<DownloadIcon />} onClick={() => void exportPng()}>
                PNG
              </Button>
            ) : null}
          </Stack>
        </Stack>

        {tableMode ? (
          <Box sx={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr>
                  {headers.map((header) => (
                    <th
                      key={header}
                      style={{
                        textAlign: 'left',
                        padding: '8px',
                        borderBottom: '1px solid #ddd',
                      }}
                    >
                      {header}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((row, rowIndex) => (
                  <tr key={`${title}-row-${rowIndex}`}>
                    {row.map((value, colIndex) => (
                      <td
                        key={`${title}-row-${rowIndex}-col-${colIndex}`}
                        style={{ padding: '8px', borderBottom: '1px solid #eee' }}
                      >
                        {value}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </Box>
        ) : (
          <Box ref={chartRef}>{chart}</Box>
        )}
      </CardContent>
    </Card>
  );
}
