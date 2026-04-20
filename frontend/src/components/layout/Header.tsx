import {
  Logout as LogoutIcon,
  Menu as MenuIcon,
  NotificationsOutlined as NotificationsIcon,
  Settings as SettingsIcon,
} from '@mui/icons-material';
import {
  AppBar,
  Avatar,
  Badge,
  Box,
  Divider,
  IconButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Stack,
  Toolbar,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import React, { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from '../../app/hooks';
import {
  logout,
  selectIsAuthenticated,
  selectUser,
} from '../../features/auth/authSlice';
import { DEV_AUTH_BYPASS_ENABLED } from '../../features/auth/devAuth';
import { selectEnvironmentMode } from '../../features/environment/environmentSlice';
import { resolveRouteExecutionContext } from '../../features/execution/executionContext';
import { useGetRiskStatusQuery } from '../../features/risk/riskApi';
import { useGetSavedExchangeConnectionsQuery } from '../../features/settings/exchangeApi';
import {
  selectIsConnected,
  selectSubscribedChannels,
} from '../../features/websocket/websocketSlice';
import ThemeToggle from '../ThemeToggle';
import { StatusPill } from '../ui/Workbench';

interface HeaderProps {
  onMenuClick: () => void;
}

const routeMeta: Record<string, { title: string; subtitle: string }> = {
  '/dashboard': {
    title: 'Dashboard',
    subtitle:
      'Review workstation posture, paper state, and the next safe action without chasing multiple competing panels.',
  },
  '/forward-testing': {
    title: 'Forward Testing',
    subtitle:
      'Observe paper-safe signal flow, chart evidence, and operator follow-up without opening a live execution path.',
  },
  '/paper': {
    title: 'Paper Trading',
    subtitle:
      'Work from one simulated execution desk where order entry stays primary and recovery state stays separate.',
  },
  '/live': {
    title: 'Live Monitoring',
    subtitle:
      'Review explicit live-context posture, exchange health, and strategy evidence while the route remains fail-closed until approved capabilities exist.',
  },
  '/strategies': {
    title: 'Strategies',
    subtitle:
      'Understand the template first, then edit saved paper-safe configs before you start or stop anything.',
  },
  '/trades': {
    title: 'Trades',
    subtitle:
      'Filter, inspect, and export fills with stronger row focus and clearer numeric review.',
  },
  '/backtest': {
    title: 'Backtest',
    subtitle:
      'Use the research workspace to launch runs, review evidence, and link chart events back to trades and datasets.',
  },
  '/market-data': {
    title: 'Market Data',
    subtitle:
      'Move cleanly from provider setup to import scope and then job output without losing retry context.',
  },
  '/risk': {
    title: 'Risk',
    subtitle:
      'Put protective posture first, then isolate overrides and operator follow-up in a clearly marked danger area.',
  },
  '/settings': {
    title: 'Settings',
    subtitle:
      'Work through calmer sections for preferences, connections, audit review, and sensitive operator tooling.',
  },
};

export const Header: React.FC<HeaderProps> = ({ onMenuClick }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const user = useAppSelector(selectUser);
  const isAuthenticated = useAppSelector(selectIsAuthenticated);
  const environmentMode = useAppSelector(selectEnvironmentMode);
  const websocketConnected = useAppSelector(selectIsConnected);
  const subscribedChannels = useAppSelector(selectSubscribedChannels);
  const { data: riskStatus } = useGetRiskStatusQuery(undefined, {
    skip: !isAuthenticated,
    pollingInterval: 30000,
    skipPollingIfUnfocused: true,
  });
  const { data: savedConnections } = useGetSavedExchangeConnectionsQuery(undefined, {
    skip: !isAuthenticated,
  });

  const activeExchangeConnection =
    savedConnections?.connections.find(
      (connection) => connection.id === savedConnections.activeConnectionId
    ) ?? null;
  const routeExecutionContext = resolveRouteExecutionContext(location.pathname);
  const telemetryEnvironment = routeExecutionContext?.environment ?? environmentMode;
  const telemetryConnected =
    websocketConnected &&
    subscribedChannels.some((channel) => channel.startsWith(`${telemetryEnvironment}.`));
  const currentRoute = routeMeta[location.pathname] ?? routeMeta['/dashboard'];
  const userRoleLabel = user?.role ? user.role.toUpperCase() : 'USER';
  const exchangeLabel = activeExchangeConnection
    ? `${activeExchangeConnection.exchange.toUpperCase()} ${
        activeExchangeConnection.testnet ? 'TESTNET' : 'LIVE'
      }`
    : 'No exchange profile';

  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const [notificationAnchorEl, setNotificationAnchorEl] =
    useState<null | HTMLElement>(null);

  const handleUserMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };

  const handleUserMenuClose = () => {
    setAnchorEl(null);
  };

  const handleNotificationMenuOpen = (event: React.MouseEvent<HTMLElement>) => {
    setNotificationAnchorEl(event.currentTarget);
  };

  const handleNotificationMenuClose = () => {
    setNotificationAnchorEl(null);
  };

  const handleLogout = () => {
    if (DEV_AUTH_BYPASS_ENABLED) {
      handleUserMenuClose();
      return;
    }

    dispatch(logout());
    handleUserMenuClose();
    void navigate('/login');
  };

  const handleSettings = () => {
    handleUserMenuClose();
    void navigate('/settings');
  };

  return (
    <AppBar position="sticky" elevation={0} color="inherit">
      <Toolbar
        sx={{
          px: { xs: 1.75, sm: 2.5, lg: 3 },
          py: 1.5,
          alignItems: 'stretch',
          minHeight: 88,
        }}
      >
        <Stack spacing={1.25} sx={{ width: '100%' }}>
          <Stack
            direction="row"
            spacing={1.5}
            alignItems="flex-start"
            sx={{ flexWrap: { xs: 'wrap', md: 'nowrap' } }}
          >
            <IconButton
              edge="start"
              color="inherit"
              aria-label="Toggle navigation menu"
              onClick={onMenuClick}
              sx={{ flexShrink: 0, mt: 0.25 }}
            >
              <MenuIcon />
            </IconButton>

            <Box sx={{ minWidth: 0, flexGrow: 1 }}>
              <Typography variant="overline" color="text.secondary">
                Research Workstation
              </Typography>
              <Typography variant={isMobile ? 'h6' : 'h5'} sx={{ lineHeight: 1.08 }}>
                {currentRoute.title}
              </Typography>
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ mt: 0.45, maxWidth: 920 }}
              >
                {currentRoute.subtitle}
              </Typography>
            </Box>

            <Stack
              direction="row"
              spacing={1}
              alignItems="center"
              sx={{
                flexShrink: 0,
                ml: { xs: 0, md: 'auto' },
              }}
            >
              <Tooltip title={`Role: ${userRoleLabel}`} arrow>
                <Box>
                  <ThemeToggle />
                </Box>
              </Tooltip>

              <IconButton
                color="inherit"
                aria-label="Open notifications"
                onClick={handleNotificationMenuOpen}
              >
                <Badge badgeContent={0} color="error">
                  <NotificationsIcon />
                </Badge>
              </IconButton>

              <IconButton
                edge="end"
                color="inherit"
                aria-label="Open account menu"
                onClick={handleUserMenuOpen}
                sx={{
                  borderColor: alpha(theme.palette.text.primary, 0.12),
                  backgroundColor: alpha(theme.palette.background.paper, 0.85),
                }}
              >
                <Avatar
                  sx={{
                    width: 32,
                    height: 32,
                    bgcolor: alpha(theme.palette.primary.main, 0.14),
                    color: theme.palette.primary.main,
                    fontSize: '0.875rem',
                  }}
                >
                  {user?.username?.charAt(0).toUpperCase() || 'U'}
                </Avatar>
              </IconButton>
            </Stack>
          </Stack>

          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            {routeExecutionContext ? (
              <StatusPill
                tone={routeExecutionContext.context === 'live' ? 'error' : 'info'}
                label={`Context: ${routeExecutionContext.label}`}
                variant="filled"
              />
            ) : null}
            <StatusPill
              tone={environmentMode === 'live' ? 'error' : 'success'}
              label={`Operations: ${environmentMode.toUpperCase()}`}
              variant="filled"
            />
            <StatusPill
              tone={telemetryConnected ? 'success' : 'warning'}
              label={`Telemetry: ${telemetryConnected ? 'Live stream' : 'Polling fallback'}`}
            />
            <StatusPill
              tone={
                riskStatus
                  ? riskStatus.circuitBreakerActive
                    ? 'error'
                    : 'success'
                  : 'default'
              }
              label={`Risk: ${
                riskStatus
                  ? riskStatus.circuitBreakerActive
                    ? 'Breaker active'
                    : 'Guarded'
                  : 'Loading'
              }`}
            />
            <StatusPill
              tone={activeExchangeConnection?.testnet === false ? 'warning' : 'info'}
              label={`Exchange: ${exchangeLabel}`}
            />
          </Stack>
        </Stack>

        <Menu
          anchorEl={anchorEl}
          open={Boolean(anchorEl)}
          onClose={handleUserMenuClose}
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'right',
          }}
          transformOrigin={{
            vertical: 'top',
            horizontal: 'right',
          }}
          PaperProps={{
            sx: { minWidth: 240, mt: 1 },
          }}
        >
          <Box sx={{ px: 2, py: 1.5 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              {user?.username || 'User'}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {user?.email || 'user@example.com'}
            </Typography>
            <Typography
              variant="caption"
              color="text.secondary"
              sx={{ display: 'block', mt: 0.5 }}
            >
              {DEV_AUTH_BYPASS_ENABLED
                ? 'Local debug auth bypass is active. The shell stays in polling fallback until a real session is used.'
                : 'Mode, risk posture, and saved connections stay visible in the shell while you work.'}
            </Typography>
          </Box>

          <Divider />

          <MenuItem onClick={handleSettings}>
            <ListItemIcon>
              <SettingsIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>Settings</ListItemText>
          </MenuItem>

          {DEV_AUTH_BYPASS_ENABLED ? (
            <MenuItem disabled onClick={handleLogout}>
              <ListItemIcon>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>Local debug bypass active</ListItemText>
            </MenuItem>
          ) : (
            <MenuItem onClick={handleLogout}>
              <ListItemIcon>
                <LogoutIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>Logout</ListItemText>
            </MenuItem>
          )}
        </Menu>

        <Menu
          anchorEl={notificationAnchorEl}
          open={Boolean(notificationAnchorEl)}
          onClose={handleNotificationMenuClose}
          anchorOrigin={{
            vertical: 'bottom',
            horizontal: 'right',
          }}
          transformOrigin={{
            vertical: 'top',
            horizontal: 'right',
          }}
          PaperProps={{
            sx: { minWidth: 320, mt: 1 },
          }}
        >
          <Box sx={{ px: 2, py: 1.5 }}>
            <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
              Notifications
            </Typography>
          </Box>

          <Divider />

          <Box sx={{ px: 2, py: 3, textAlign: 'left' }}>
            <Typography variant="body2" color="text.secondary">
              No new notifications.
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Runtime alerts and operator prompts appear here when they are available.
            </Typography>
          </Box>
        </Menu>
      </Toolbar>
    </AppBar>
  );
};
