import { Grid, Paper, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import type { ReactNode } from 'react';

export type KeyValueGridTone = 'default' | 'success' | 'warning' | 'error' | 'info';

export interface KeyValueGridItem {
  label: string;
  value: ReactNode;
  tone?: KeyValueGridTone;
}

interface KeyValueGridProps {
  items: KeyValueGridItem[];
}

export function KeyValueGrid({ items }: KeyValueGridProps) {
  const theme = useTheme();

  const resolveAccent = (tone: KeyValueGridTone = 'default') => {
    switch (tone) {
      case 'success':
        return theme.palette.success.main;
      case 'warning':
        return theme.palette.warning.main;
      case 'error':
        return theme.palette.error.main;
      case 'info':
        return theme.palette.info.main;
      default:
        return theme.palette.primary.main;
    }
  };

  return (
    <Grid container spacing={1}>
      {items.map((item) => (
        <Grid key={item.label} size={{ xs: 12, sm: 6, xl: 3 }}>
          <Paper
            variant="outlined"
            sx={{
              height: '100%',
              p: 1.25,
              borderLeft: `3px solid ${resolveAccent(item.tone)}`,
            }}
          >
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
              {item.label}
            </Typography>
            <Typography
              variant="body2"
              sx={{ mt: 0.25, fontWeight: 700, lineHeight: 1.35, overflowWrap: 'anywhere' }}
            >
              {item.value}
            </Typography>
          </Paper>
        </Grid>
      ))}
    </Grid>
  );
}
