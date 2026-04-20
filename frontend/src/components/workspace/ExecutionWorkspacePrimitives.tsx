import {
  Box,
  ButtonBase,
  type Breakpoint,
  Grid,
  List,
  ListItem,
  ListItemButton,
  Stack,
  Typography,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import type { Theme } from '@mui/material/styles';
import type { ReactNode } from 'react';

import {
  EmptyState,
  MetricCard,
  NumericText,
  StatusPill,
  SurfacePanel,
  type WorkbenchTone,
} from '../ui/Workbench';

import { StickyInspectorPanel } from './StickyInspectorPanel';

const numericFontFamily =
  '"IBM Plex Mono", "Cascadia Mono", "Aptos Mono", "Consolas", monospace';

function resolveToneColor(theme: Theme, tone: WorkbenchTone = 'default') {
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
}

export interface ExecutionStatusItem {
  label: ReactNode;
  value: ReactNode;
  detail?: ReactNode;
  tone?: WorkbenchTone;
}

interface ExecutionStatusRailProps {
  title: ReactNode;
  description?: ReactNode;
  items: ExecutionStatusItem[];
  actions?: ReactNode;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
}

export function ExecutionStatusRail({
  title,
  description,
  items,
  actions,
  emptyTitle = 'No execution context selected',
  emptyDescription = 'Select a strategy or active algorithm to review posture, data freshness, and capability cues in one place.',
}: ExecutionStatusRailProps) {
  const theme = useTheme();

  return (
    <SurfacePanel
      title={title}
      description={description}
      actions={actions}
      contentSx={{ gap: 1.25 }}
    >
      {items.length === 0 ? (
        <EmptyState title={emptyTitle} description={emptyDescription} tone="info" />
      ) : (
        <Stack
          direction={{ xs: 'column', lg: 'row' }}
          spacing={1}
          role="list"
          aria-label="Execution status rail"
          sx={{ flexWrap: 'wrap', minWidth: 0 }}
        >
          {items.map((item, index) => {
            const accent = resolveToneColor(theme, item.tone);

            return (
              <Box
                key={`execution-status-${index}`}
                role="listitem"
                sx={{
                  minWidth: { xs: '100%', lg: 0 },
                  flex: { xs: '1 1 auto', lg: '1 1 220px' },
                  minHeight: '100%',
                  px: 1.5,
                  py: 1.25,
                  border: '1px solid',
                  borderColor: alpha(accent ?? theme.palette.primary.main, 0.18),
                  backgroundColor: alpha(accent ?? theme.palette.primary.main, 0.05),
                }}
              >
                <Stack spacing={0.65} sx={{ minWidth: 0 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                    {item.label}
                  </Typography>
                  <Typography
                    variant="body2"
                    sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}
                  >
                    {item.value}
                  </Typography>
                  {item.detail ? (
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{ overflowWrap: 'anywhere' }}
                    >
                      {item.detail}
                    </Typography>
                  ) : null}
                </Stack>
              </Box>
            );
          })}
        </Stack>
      )}
    </SurfacePanel>
  );
}

export interface ExecutionCardMetric {
  label: ReactNode;
  value: ReactNode;
  tone?: WorkbenchTone;
}

interface ExecutionCardProps {
  title: ReactNode;
  subtitle?: ReactNode;
  badges?: ReactNode;
  metrics?: ExecutionCardMetric[];
  detail?: ReactNode;
  selected?: boolean;
  onSelect?: () => void;
  actions?: ReactNode;
  ariaLabel?: string;
}

export function ExecutionCard({
  title,
  subtitle,
  badges,
  metrics = [],
  detail,
  selected = false,
  onSelect,
  actions,
  ariaLabel,
}: ExecutionCardProps) {
  const theme = useTheme();
  const borderColor = selected
    ? alpha(theme.palette.primary.main, 0.3)
    : alpha(theme.palette.text.primary, 0.08);

  const content = (
    <Stack spacing={1.25} sx={{ p: 2, minWidth: 0 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1}
        justifyContent="space-between"
        alignItems={{ xs: 'flex-start', sm: 'flex-start' }}
      >
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography
            variant="subtitle1"
            sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}
          >
            {title}
          </Typography>
          {subtitle ? (
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ mt: 0.4, overflowWrap: 'anywhere' }}
            >
              {subtitle}
            </Typography>
          ) : null}
        </Box>
        {actions ? <Box sx={{ flexShrink: 0 }}>{actions}</Box> : null}
      </Stack>

      {badges ? (
        <Stack
          direction="row"
          spacing={0.75}
          flexWrap="wrap"
          useFlexGap
          sx={{ minWidth: 0, '& > *': { maxWidth: '100%' } }}
        >
          {badges}
        </Stack>
      ) : null}

      {metrics.length > 0 ? (
        <Grid container spacing={1}>
          {metrics.map((metric, index) => (
            <Grid key={`execution-card-metric-${index}`} size={{ xs: 6, lg: 3 }}>
              <Stack spacing={0.4}>
                <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
                  {metric.label}
                </Typography>
                {typeof metric.value === 'string' || typeof metric.value === 'number' ? (
                  <NumericText variant="body2" tone={metric.tone}>
                    {metric.value}
                  </NumericText>
                ) : (
                  <Box>{metric.value}</Box>
                )}
              </Stack>
            </Grid>
          ))}
        </Grid>
      ) : null}

      {detail ? (
        <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
          {detail}
        </Typography>
      ) : null}
    </Stack>
  );

  return (
    <Box
      sx={{
        border: '1px solid',
        borderColor,
        backgroundColor: selected
          ? alpha(theme.palette.primary.main, 0.08)
          : theme.palette.background.paper,
        transition: 'background-color 120ms ease, border-color 120ms ease',
      }}
    >
      {onSelect ? (
        <ButtonBase
          onClick={onSelect}
          aria-pressed={selected}
          aria-label={ariaLabel}
          sx={{
            display: 'block',
            width: '100%',
            textAlign: 'left',
            '&:focus-visible': {
              outline: `2px solid ${theme.palette.primary.main}`,
              outlineOffset: -2,
            },
          }}
        >
          {content}
        </ButtonBase>
      ) : (
        content
      )}
    </Box>
  );
}

export interface InvestigationLogEntry {
  id: string;
  timestamp: ReactNode;
  title: ReactNode;
  detail: ReactNode;
  tone?: WorkbenchTone;
  tags?: ReactNode[];
}

interface InvestigationLogPanelProps {
  title?: ReactNode;
  description?: ReactNode;
  entries: InvestigationLogEntry[];
  loading?: boolean;
  actions?: ReactNode;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
}

export function InvestigationLogPanel({
  title = 'Investigation log',
  description = 'Keep operator notes, incidents, and follow-up actions in one chronological evidence trail.',
  entries,
  loading = false,
  actions,
  emptyTitle = 'No investigation entries yet',
  emptyDescription = 'Add the first note or incident annotation once the selected strategy or algorithm needs follow-up.',
}: InvestigationLogPanelProps) {
  const theme = useTheme();

  return (
    <SurfacePanel title={title} description={description} actions={actions}>
      {loading ? (
        <Stack spacing={1.25} role="status" aria-live="polite">
          <Typography variant="body2" color="text.secondary">
            Loading investigation history...
          </Typography>
          {[0, 1, 2].map((item) => (
            <Box
              key={`investigation-log-skeleton-${item}`}
              sx={{
                minHeight: 78,
                border: '1px solid',
                borderColor: 'divider',
                backgroundColor: alpha(theme.palette.primary.main, 0.04),
              }}
            />
          ))}
        </Stack>
      ) : entries.length === 0 ? (
        <EmptyState title={emptyTitle} description={emptyDescription} tone="info" />
      ) : (
        <List disablePadding aria-label="Investigation log entries">
          {entries.map((entry) => (
            <ListItem
              key={entry.id}
              disablePadding
              sx={{
                borderTop: '1px solid',
                borderColor: 'divider',
                '&:first-of-type': {
                  borderTop: 'none',
                },
              }}
            >
              <ListItemButton
                disableRipple
                sx={{
                  px: 0,
                  py: 1.35,
                  alignItems: 'flex-start',
                  minWidth: 0,
                  '&:hover': {
                    backgroundColor: 'transparent',
                  },
                }}
              >
                <Stack spacing={0.75} sx={{ width: '100%', minWidth: 0 }}>
                  <Stack
                    direction={{ xs: 'column', lg: 'row' }}
                    spacing={1}
                    justifyContent="space-between"
                    alignItems={{ xs: 'flex-start', lg: 'center' }}
                    sx={{ minWidth: 0 }}
                  >
                    <Box sx={{ minWidth: 0, flex: 1 }}>
                      <Typography
                        variant="subtitle2"
                        sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}
                      >
                        {entry.title}
                      </Typography>
                    </Box>
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{
                        fontFamily: numericFontFamily,
                        overflowWrap: 'anywhere',
                        maxWidth: '100%',
                        flexShrink: 0,
                      }}
                    >
                      {entry.timestamp}
                    </Typography>
                  </Stack>
                  <Typography
                    variant="body2"
                    color="text.secondary"
                    sx={{ overflowWrap: 'anywhere' }}
                  >
                    {entry.detail}
                  </Typography>
                  {entry.tags && entry.tags.length > 0 ? (
                    <Stack
                      direction="row"
                      spacing={0.75}
                      flexWrap="wrap"
                      useFlexGap
                      sx={{ '& > *': { maxWidth: '100%' } }}
                    >
                      {entry.tags.map((tag, index) => (
                        <StatusPill
                          key={`investigation-tag-${entry.id}-${index}`}
                          label={tag}
                          tone={entry.tone ?? 'info'}
                        />
                      ))}
                    </Stack>
                  ) : null}
                </Stack>
              </ListItemButton>
            </ListItem>
          ))}
        </List>
      )}
    </SurfacePanel>
  );
}

export interface LiveMetricItem {
  label: ReactNode;
  value: ReactNode;
  detail?: ReactNode;
  tone?: WorkbenchTone;
  kicker?: ReactNode;
}

interface LiveMetricStripProps {
  title?: ReactNode;
  description?: ReactNode;
  items: LiveMetricItem[];
}

export function LiveMetricStrip({
  title = 'Live metric strip',
  description = 'Keep the highest-signal execution or monitoring metrics visible without turning the route into a dense dashboard wall.',
  items,
}: LiveMetricStripProps) {
  return (
    <SurfacePanel title={title} description={description} contentSx={{ gap: 1.5 }}>
      {items.length === 0 ? (
        <EmptyState
          title="No live metrics available"
          description="Metrics appear here once the selected execution context has current performance, position, or incident data to summarize."
          tone="info"
        />
      ) : (
        <Grid container spacing={1.25}>
          {items.map((item, index) => (
            <Grid key={`live-metric-${index}`} size={{ xs: 12, sm: 6, xl: 3 }}>
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
      )}
    </SurfacePanel>
  );
}

export interface ActiveAlgorithmDetailSection {
  id: string;
  title: ReactNode;
  content: ReactNode;
}

interface ActiveAlgorithmDetailDrawerProps {
  title: ReactNode;
  description?: ReactNode;
  statusChips?: ReactNode;
  summary?: ReactNode;
  sections?: ActiveAlgorithmDetailSection[];
  loading?: boolean;
  emptyTitle?: ReactNode;
  emptyDescription?: ReactNode;
  actions?: ReactNode;
  mobileOpenLabel?: string;
  desktopBehavior?: 'sticky' | 'inline';
  desktopBreakpoint?: Breakpoint;
}

export function ActiveAlgorithmDetailDrawer({
  title,
  description,
  statusChips,
  summary,
  sections = [],
  loading = false,
  emptyTitle = 'No active algorithm selected',
  emptyDescription = 'Choose one active algorithm to review current position state, recent signals, risk posture, and operator follow-up context.',
  actions,
  mobileOpenLabel = 'Open active algorithm detail',
  desktopBehavior = 'sticky',
  desktopBreakpoint = 'xl',
}: ActiveAlgorithmDetailDrawerProps) {
  const detailContent = loading ? (
    <Stack spacing={1.25} role="status" aria-live="polite">
      <Typography variant="body2" color="text.secondary">
        Loading active algorithm detail...
      </Typography>
      {[0, 1, 2].map((item) => (
        <Box
          key={`active-algorithm-detail-skeleton-${item}`}
          sx={{
            minHeight: 92,
            border: '1px solid',
            borderColor: 'divider',
            backgroundColor: 'action.hover',
          }}
        />
      ))}
    </Stack>
  ) : !summary && sections.length === 0 ? (
    <EmptyState title={emptyTitle} description={emptyDescription} tone="info" />
  ) : (
    <Stack spacing={1.5}>
      {statusChips ? (
        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
          {statusChips}
        </Stack>
      ) : null}
      {summary ? <Box>{summary}</Box> : null}
      {sections.map((section) => (
        <SurfacePanel
          key={section.id}
          title={section.title}
          contentSx={{ gap: 1 }}
          sx={{ borderStyle: 'dashed' }}
        >
          {section.content}
        </SurfacePanel>
      ))}
    </Stack>
  );

  return (
    <StickyInspectorPanel
      title={title}
      description={description}
      actions={actions}
      mobileBehavior="drawer"
      mobileOpenLabel={mobileOpenLabel}
      desktopBehavior={desktopBehavior}
      desktopBreakpoint={desktopBreakpoint}
      sx={{ minWidth: 0 }}
      mobilePreview={
        <Stack spacing={1.25}>
          {statusChips ? (
            <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
              {statusChips}
            </Stack>
          ) : null}
          <Typography variant="body2" color="text.secondary">
            Open the detail drawer to inspect selected algorithm state, recent signals, risk, and operator notes without losing the main chart or list context.
          </Typography>
        </Stack>
      }
    >
      {detailContent}
    </StickyInspectorPanel>
  );
}
