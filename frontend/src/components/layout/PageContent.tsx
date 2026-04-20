import { Grid, Stack } from '@mui/material';
import type { AlertColor } from '@mui/material';
import type { ReactNode } from 'react';

import {
  MetricCard,
  SectionHeader,
  SurfacePanel,
} from '../ui/Workbench';

interface PageContentProps {
  children: ReactNode;
  maxWidth?: 'overview' | 'research' | 'forms' | 'full';
}

const MAX_WIDTHS = {
  overview: 1440,
  research: 1680,
  forms: 1200,
  full: undefined,
} as const;

export function PageContent({ children, maxWidth = 'overview' }: PageContentProps) {
  return (
    <Stack
      spacing={{ xs: 2.5, lg: 3 }}
      sx={{
        width: '100%',
        maxWidth: MAX_WIDTHS[maxWidth],
        mx: 'auto',
        minWidth: 0,
      }}
    >
      {children}
    </Stack>
  );
}

interface PageIntroProps {
  eyebrow?: ReactNode;
  title?: ReactNode;
  description: ReactNode;
  chips?: ReactNode;
  actions?: ReactNode;
}

export function PageIntro({
  eyebrow,
  title,
  description,
  chips,
  actions,
}: PageIntroProps) {
  return (
    <SurfacePanel
      elevated
      eyebrow={eyebrow}
      title={title}
      description={description}
      actions={actions}
      contentSx={{ gap: 1.5 }}
    >
      {chips ? (
        <Stack
          direction="row"
          spacing={1}
          flexWrap="wrap"
          useFlexGap
          sx={{ '& > *': { maxWidth: '100%' } }}
        >
          {chips}
        </Stack>
      ) : null}
    </SurfacePanel>
  );
}

export interface PageMetricItem {
  label: string;
  value: ReactNode;
  detail?: ReactNode;
  tone?: AlertColor | 'default';
  kicker?: ReactNode;
}

interface PageMetricStripProps {
  items: PageMetricItem[];
}

export function PageMetricStrip({ items }: PageMetricStripProps) {
  if (items.length === 0) {
    return null;
  }

  return (
    <Grid container spacing={1.5}>
      {items.map((item) => (
        <Grid key={item.label} size={{ xs: 12, sm: 6, xl: 3 }}>
          <MetricCard
            label={item.label}
            value={item.value}
            detail={item.detail}
            tone={item.tone}
            kicker={item.kicker}
          />
        </Grid>
      ))}
    </Grid>
  );
}

interface PageSectionHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}

export function PageSectionHeader({
  title,
  description,
  actions,
}: PageSectionHeaderProps) {
  return (
    <SectionHeader title={title} description={description} actions={actions} />
  );
}
