import {
  Box,
  Chip,
  Paper,
  Stack,
  Typography,
  type AlertColor,
  type SxProps,
  type Theme,
} from '@mui/material';
import { alpha, useTheme } from '@mui/material/styles';
import type { ReactNode } from 'react';

export type WorkbenchTone = AlertColor | 'default';
export type LegendShape = 'dot' | 'line' | 'diamond' | 'bar' | 'square' | 'up' | 'down';

export interface LegendItem {
  color: string;
  label: ReactNode;
  detail?: ReactNode;
  shape?: LegendShape;
}

const numericFontFamily =
  '"IBM Plex Mono", "Cascadia Mono", "Aptos Mono", "Consolas", monospace';

const resolveToneColor = (theme: Theme, tone: WorkbenchTone = 'default') => {
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

const resolveToneSurface = (theme: Theme, tone: WorkbenchTone = 'default') =>
  alpha(resolveToneColor(theme, tone), theme.palette.mode === 'light' ? 0.08 : 0.14);

const legendShapeSx = (shape: LegendShape, color: string): SxProps<Theme> => {
  switch (shape) {
    case 'line':
      return { width: 18, height: 3, borderRadius: 999, bgcolor: color };
    case 'diamond':
      return {
        width: 11,
        height: 11,
        bgcolor: color,
        transform: 'rotate(45deg)',
        borderRadius: 0.5,
      };
    case 'bar':
      return { width: 6, height: 16, borderRadius: 999, bgcolor: color };
    case 'square':
      return { width: 12, height: 12, borderRadius: 0.75, bgcolor: color };
    case 'up':
      return {
        width: 0,
        height: 0,
        borderLeft: '7px solid transparent',
        borderRight: '7px solid transparent',
        borderBottom: `12px solid ${color}`,
      };
    case 'down':
      return {
        width: 0,
        height: 0,
        borderLeft: '7px solid transparent',
        borderRight: '7px solid transparent',
        borderTop: `12px solid ${color}`,
      };
    default:
      return { width: 10, height: 10, borderRadius: '50%', bgcolor: color };
  }
};

interface NumericTextProps {
  children: ReactNode;
  variant?: 'body2' | 'body1' | 'h6' | 'h5' | 'caption';
  tone?: WorkbenchTone;
  sx?: SxProps<Theme>;
}

export function NumericText({
  children,
  variant = 'body1',
  tone = 'default',
  sx,
}: NumericTextProps) {
  const theme = useTheme();

  return (
    <Typography
      variant={variant}
      sx={{
        fontFamily: numericFontFamily,
        fontVariantNumeric: 'tabular-nums',
        letterSpacing: '-0.02em',
        color: tone === 'default' ? 'text.primary' : resolveToneColor(theme, tone),
        ...sx,
      }}
    >
      {children}
    </Typography>
  );
}

interface SurfacePanelProps {
  title?: ReactNode;
  description?: ReactNode;
  eyebrow?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  tone?: WorkbenchTone;
  elevated?: boolean;
  sx?: SxProps<Theme>;
  contentSx?: SxProps<Theme>;
}

export function SurfacePanel({
  title,
  description,
  eyebrow,
  actions,
  children,
  tone = 'default',
  elevated = false,
  sx,
  contentSx,
}: SurfacePanelProps) {
  const theme = useTheme();
  const accent = resolveToneColor(theme, tone);

  return (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: '20px',
        backgroundColor: elevated
          ? alpha(
              theme.palette.background.paper,
              theme.palette.mode === 'light' ? 0.94 : 0.88
            )
          : 'background.paper',
        borderColor: alpha(accent, tone === 'default' ? 0.18 : 0.2),
        boxShadow: elevated
          ? `0 22px 44px ${alpha(theme.palette.common.black, 0.08)}`
          : 'none',
        ...sx,
      }}
    >
      <Stack spacing={2.25} sx={{ p: { xs: 2, md: 2.5 }, ...contentSx }}>
        {title || description || eyebrow || actions ? (
          <Stack
            direction={{ xs: 'column', md: 'row' }}
            spacing={1.5}
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
          >
            <Box sx={{ maxWidth: 920, minWidth: 0 }}>
              {eyebrow ? (
                <Typography variant="overline" color="text.secondary">
                  {eyebrow}
                </Typography>
              ) : null}
              {title ? (
                <Typography variant="h6" sx={{ mt: eyebrow ? 0.25 : 0 }}>
                  {title}
                </Typography>
              ) : null}
              {description ? (
                <Typography
                  variant="body2"
                  color="text.secondary"
                  sx={{ mt: title || eyebrow ? 0.75 : 0 }}
                >
                  {description}
                </Typography>
              ) : null}
            </Box>
            {actions ? (
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={1}
                alignItems={{ xs: 'stretch', md: 'center' }}
                sx={{ width: { xs: '100%', md: 'auto' }, flexShrink: 0 }}
              >
                {actions}
              </Stack>
            ) : null}
          </Stack>
        ) : null}

        {children}
      </Stack>
    </Paper>
  );
}

interface StatusPillProps {
  label: ReactNode;
  tone?: WorkbenchTone;
  variant?: 'outlined' | 'filled';
  sx?: SxProps<Theme>;
}

export function StatusPill({
  label,
  tone = 'default',
  variant = 'outlined',
  sx,
}: StatusPillProps) {
  const theme = useTheme();
  const color = resolveToneColor(theme, tone);

  return (
    <Chip
      label={label}
      variant={variant}
      sx={{
        height: 'auto',
        maxWidth: '100%',
        alignItems: 'flex-start',
        borderRadius: 999,
        borderColor: alpha(color, variant === 'filled' ? 0.52 : 0.28),
        backgroundColor:
          variant === 'filled' ? color : resolveToneSurface(theme, tone),
        color:
          variant === 'filled'
            ? theme.palette.getContrastText(color)
            : tone === 'default'
              ? theme.palette.text.primary
              : color,
        '& .MuiChip-label': {
          display: 'block',
          fontWeight: 700,
          fontSize: '0.75rem',
          whiteSpace: 'normal',
          overflowWrap: 'anywhere',
          lineHeight: 1.25,
          paddingTop: 5,
          paddingBottom: 5,
        },
        ...sx,
      }}
    />
  );
}

interface MetricCardProps {
  label: ReactNode;
  value: ReactNode;
  detail?: ReactNode;
  tone?: WorkbenchTone;
  kicker?: ReactNode;
  sx?: SxProps<Theme>;
}

export function MetricCard({
  label,
  value,
  detail,
  tone = 'default',
  kicker,
  sx,
}: MetricCardProps) {
  const theme = useTheme();
  const accent = resolveToneColor(theme, tone);

  return (
    <Paper
      variant="outlined"
      sx={{
        height: '100%',
        borderRadius: '20px',
        borderColor: alpha(accent, tone === 'default' ? 0.14 : 0.2),
        background: `linear-gradient(180deg, ${resolveToneSurface(theme, tone)} 0%, ${alpha(
          theme.palette.background.paper,
          0.98
        )} 54%)`,
        ...sx,
      }}
    >
      <Stack spacing={1.1} sx={{ p: 2.25, height: '100%' }}>
        <Stack
          direction="row"
          justifyContent="space-between"
          spacing={1}
          alignItems="flex-start"
        >
          <Typography variant="caption" color="text.secondary" sx={{ fontWeight: 700 }}>
            {label}
          </Typography>
          {kicker ? (
            <Typography
              variant="caption"
              sx={{
                color: accent,
                fontWeight: 700,
                letterSpacing: '0.08em',
                textTransform: 'uppercase',
              }}
            >
              {kicker}
            </Typography>
          ) : null}
        </Stack>
        {typeof value === 'string' || typeof value === 'number' ? (
          <NumericText variant="h5" tone={tone}>
            {value}
          </NumericText>
        ) : (
          <Box>{value}</Box>
        )}
        {detail ? (
          <Typography variant="body2" color="text.secondary">
            {detail}
          </Typography>
        ) : null}
      </Stack>
    </Paper>
  );
}

interface EmptyStateProps {
  title: ReactNode;
  description: ReactNode;
  action?: ReactNode;
  secondaryAction?: ReactNode;
  tone?: WorkbenchTone;
  sx?: SxProps<Theme>;
}

export function EmptyState({
  title,
  description,
  action,
  secondaryAction,
  tone = 'default',
  sx,
}: EmptyStateProps) {
  const theme = useTheme();
  const accent = resolveToneColor(theme, tone);

  return (
    <Paper
      variant="outlined"
      sx={{
        borderStyle: 'dashed',
        borderColor: alpha(accent, 0.28),
        backgroundColor: resolveToneSurface(theme, tone),
        borderRadius: '20px',
        ...sx,
      }}
    >
      <Stack spacing={1.5} sx={{ p: { xs: 2.25, md: 3 } }}>
        <Typography variant="h6">{title}</Typography>
        <Typography variant="body2" color="text.secondary">
          {description}
        </Typography>
        {action || secondaryAction ? (
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            {action}
            {secondaryAction}
          </Stack>
        ) : null}
      </Stack>
    </Paper>
  );
}

interface SectionHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  eyebrow?: ReactNode;
}

export function SectionHeader({
  title,
  description,
  actions,
  eyebrow,
}: SectionHeaderProps) {
  return (
    <Stack
      direction={{ xs: 'column', md: 'row' }}
      spacing={1.5}
      justifyContent="space-between"
      alignItems={{ xs: 'flex-start', md: 'center' }}
    >
      <Box sx={{ maxWidth: 860 }}>
        {eyebrow ? (
          <Typography variant="overline" color="text.secondary">
            {eyebrow}
          </Typography>
        ) : null}
        <Typography variant="h6" sx={{ mt: eyebrow ? 0.25 : 0 }}>
          {title}
        </Typography>
        {description ? (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {description}
          </Typography>
        ) : null}
      </Box>
      {actions ? (
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1}
          alignItems={{ xs: 'stretch', md: 'center' }}
          sx={{ width: { xs: '100%', md: 'auto' } }}
        >
          {actions}
        </Stack>
      ) : null}
    </Stack>
  );
}

interface RouteActionBarProps {
  title?: ReactNode;
  description?: ReactNode;
  meta?: ReactNode;
  actions?: ReactNode;
  children?: ReactNode;
  sticky?: boolean;
  top?: number;
}

export function RouteActionBar({
  title,
  description,
  meta,
  actions,
  children,
  sticky = false,
  top = 96,
}: RouteActionBarProps) {
  return (
    <SurfacePanel
      elevated
      sx={{
        position: sticky ? { md: 'sticky' } : 'relative',
        top: sticky ? { md: top } : undefined,
        zIndex: sticky ? 5 : undefined,
      }}
      title={title}
      description={description}
      actions={actions}
    >
      {meta ? (
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {meta}
        </Stack>
      ) : null}
      {children}
    </SurfacePanel>
  );
}

interface TableToolbarProps {
  title: ReactNode;
  description?: ReactNode;
  stats?: ReactNode;
  actions?: ReactNode;
  filters?: ReactNode;
}

export function TableToolbar({
  title,
  description,
  stats,
  actions,
  filters,
}: TableToolbarProps) {
  return (
    <SurfacePanel
      title={title}
      description={description}
      actions={actions}
      contentSx={{ gap: 1.5 }}
    >
      {stats ? <Box>{stats}</Box> : null}
      {filters ? <Box>{filters}</Box> : null}
    </SurfacePanel>
  );
}

interface LegendListProps {
  items: LegendItem[];
  dense?: boolean;
  sx?: SxProps<Theme>;
}

export function LegendList({ items, dense = false, sx }: LegendListProps) {
  return (
    <Stack
      direction="row"
      spacing={dense ? 1 : 1.5}
      flexWrap="wrap"
      useFlexGap
      sx={sx}
    >
      {items.map((item, index) => (
        <Stack
          key={`legend-item-${index}`}
          direction="row"
          spacing={0.8}
          alignItems="center"
          sx={{ minHeight: 22 }}
        >
          <Box sx={legendShapeSx(item.shape ?? 'dot', item.color)} />
          <Typography variant="caption" sx={{ fontWeight: 700 }}>
            {item.label}
          </Typography>
          {item.detail ? (
            <Typography variant="caption" color="text.secondary">
              {item.detail}
            </Typography>
          ) : null}
        </Stack>
      ))}
    </Stack>
  );
}
