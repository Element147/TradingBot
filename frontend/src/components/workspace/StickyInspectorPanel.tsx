import CloseIcon from '@mui/icons-material/Close';
import {
  type Breakpoint,
  Button,
  Drawer,
  IconButton,
  Stack,
  Typography,
  useMediaQuery,
  useTheme,
  type SxProps,
  type Theme,
} from '@mui/material';
import { useState } from 'react';
import type { ReactNode } from 'react';

import { SurfacePanel } from '../ui/Workbench';

interface StickyInspectorPanelProps {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  mobilePreview?: ReactNode;
  mobileOpenLabel?: string;
  mobileBehavior?: 'inline' | 'drawer';
  desktopBehavior?: 'sticky' | 'inline';
  desktopBreakpoint?: Breakpoint;
  top?: number;
  sx?: SxProps<Theme>;
}

export function StickyInspectorPanel({
  title,
  description,
  actions,
  children,
  mobilePreview,
  mobileOpenLabel = 'Open inspector',
  mobileBehavior = 'inline',
  desktopBehavior = 'sticky',
  desktopBreakpoint = 'xl',
  top = 112,
  sx,
}: StickyInspectorPanelProps) {
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up(desktopBreakpoint));
  const [mobileOpen, setMobileOpen] = useState(false);

  if (!isDesktop && mobileBehavior === 'drawer') {
    return (
      <>
        <SurfacePanel
          elevated
          title={title}
          description={description}
          actions={
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              {actions}
              <Button variant="contained" onClick={() => setMobileOpen(true)}>
                {mobileOpenLabel}
              </Button>
            </Stack>
          }
          sx={sx}
          contentSx={{ gap: 1.75 }}
        >
          <Stack spacing={1.75}>
            {mobilePreview ?? (
              <Typography variant="body2" color="text.secondary">
                Open the inspector to review the selected trade, event context, and PnL details without stretching the main chart stack.
              </Typography>
            )}
          </Stack>
        </SurfacePanel>

        <Drawer
          anchor="bottom"
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          PaperProps={{
            sx: {
              borderTopLeftRadius: 0,
              borderTopRightRadius: 0,
              maxHeight: '82vh',
            },
          }}
        >
          <Stack spacing={2} sx={{ p: 2.5, pb: 3 }}>
            <Stack
              direction="row"
              spacing={1.5}
              justifyContent="space-between"
              alignItems="flex-start"
            >
              <Stack spacing={0.75} sx={{ minWidth: 0 }}>
                <Typography variant="h6">{title}</Typography>
                {description ? (
                  <Typography variant="body2" color="text.secondary">
                    {description}
                  </Typography>
                ) : null}
              </Stack>
              <IconButton aria-label="Close inspector" onClick={() => setMobileOpen(false)}>
                <CloseIcon />
              </IconButton>
            </Stack>

            {actions ? (
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                {actions}
              </Stack>
            ) : null}

            <Stack spacing={1.75}>{children}</Stack>
          </Stack>
        </Drawer>
      </>
    );
  }

  return (
    <SurfacePanel
      elevated
      title={title}
      description={description}
      actions={actions}
      sx={{
        position: isDesktop && desktopBehavior === 'sticky' ? 'sticky' : 'relative',
        top: isDesktop && desktopBehavior === 'sticky' ? top : undefined,
        ...sx,
      }}
      contentSx={{ gap: 1.75 }}
    >
      <Stack spacing={1.75}>{children}</Stack>
      {!isDesktop ? (
        <Typography variant="caption" color="text.secondary">
          Inspector stays inline on smaller screens so chart review and detail reading remain in one scroll path.
        </Typography>
      ) : null}
    </SurfacePanel>
  );
}
