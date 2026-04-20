import {
  Assessment as BacktestIcon,
  CloudDownloadOutlined as MarketDataIcon,
  Dashboard as DashboardIcon,
  Insights as ForwardTestingIcon,
  History as TradesIcon,
  ReceiptLong as PaperIcon,
  Security as RiskIcon,
  Sensors as LiveIcon,
  Settings as SettingsIcon,
  ShowChart as StrategyIcon,
} from '@mui/icons-material';
import {
  Box,
  Drawer,
  List,
  ListItem,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Stack,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { StatusPill } from '../ui/Workbench';

interface SidebarProps {
  open: boolean;
  onClose: () => void;
}

const DRAWER_WIDTH = 296;

const navigationSections = [
  {
    heading: 'Command',
    items: [
      {
        text: 'Dashboard',
        detail: 'Overview and system signals',
        icon: <DashboardIcon />,
        path: '/dashboard',
      },
      {
        text: 'Forward Testing',
        detail: 'Signal observation desk',
        icon: <ForwardTestingIcon />,
        path: '/forward-testing',
      },
      { text: 'Paper', detail: 'Simulated order desk', icon: <PaperIcon />, path: '/paper' },
      { text: 'Live', detail: 'Capability-gated monitor', icon: <LiveIcon />, path: '/live' },
    ],
  },
  {
    heading: 'Research',
    items: [
      { text: 'Strategies', detail: 'Profiles and configs', icon: <StrategyIcon />, path: '/strategies' },
      { text: 'Trades', detail: 'History and exports', icon: <TradesIcon />, path: '/trades' },
      { text: 'Backtest', detail: 'Runs and results', icon: <BacktestIcon />, path: '/backtest' },
      { text: 'Market Data', detail: 'Provider imports', icon: <MarketDataIcon />, path: '/market-data' },
    ],
  },
  {
    heading: 'Operations',
    items: [
      { text: 'Risk', detail: 'Breakers and limits', icon: <RiskIcon />, path: '/risk' },
      { text: 'Settings', detail: 'Connections and preferences', icon: <SettingsIcon />, path: '/settings' },
    ],
  },
];

export const Sidebar: React.FC<SidebarProps> = ({ open, onClose }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const navigate = useNavigate();
  const location = useLocation();

  const handleNavigation = (path: string) => {
    void navigate(path);
    onClose();
  };

  const drawerContent = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ px: 2.5, pt: 3, pb: 2 }}>
        <Stack spacing={1.5}>
          <Box>
            <Typography variant="overline" sx={{ color: 'primary.main' }}>
              AlgoTrading Bot
            </Typography>
            <Typography variant="h6" sx={{ mt: 0.25, letterSpacing: '-0.02em' }}>
              Research Workstation
            </Typography>
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{ mt: 0.75, overflowWrap: 'anywhere' }}
            >
              Navigation for the test-first research, paper, risk, and operator workflow.
            </Typography>
          </Box>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
            <StatusPill label="Default: test" tone="success" variant="filled" />
            <StatusPill label="Paper simulated" tone="info" />
          </Stack>
        </Stack>
      </Box>

      <Box sx={{ flexGrow: 1, overflowY: 'auto', px: 1.25, py: 1.5 }}>
        {navigationSections.map((section) => (
          <Box key={section.heading} sx={{ mb: 2.5 }}>
            <Typography
              variant="overline"
              sx={{
                px: 1.25,
                display: 'block',
                mb: 0.85,
                color: alpha(theme.palette.text.secondary, 0.92),
              }}
            >
              {section.heading}
            </Typography>

            <List disablePadding>
              {section.items.map((item) => {
                const isActive = location.pathname === item.path;

                return (
                  <ListItem key={item.text} disablePadding sx={{ mb: 0.5 }}>
                    <ListItemButton
                      onClick={() => handleNavigation(item.path)}
                      selected={isActive}
                      sx={{
                        px: 1.5,
                        py: 1.35,
                        alignItems: 'center',
                        borderRadius: 0,
                        border: '1px solid',
                        borderColor: isActive
                          ? alpha(theme.palette.primary.main, 0.42)
                          : alpha(theme.palette.text.primary, 0.12),
                        backgroundColor: isActive
                          ? alpha(theme.palette.primary.main, 0.18)
                          : alpha(theme.palette.background.paper, 0.42),
                        boxShadow: isActive
                          ? `inset 3px 0 0 ${theme.palette.primary.main}`
                          : 'none',
                        '&:hover': {
                          backgroundColor: isActive
                            ? alpha(theme.palette.primary.main, 0.22)
                            : alpha(theme.palette.primary.main, 0.08),
                          borderColor: isActive
                            ? alpha(theme.palette.primary.main, 0.48)
                            : alpha(theme.palette.primary.main, 0.2),
                        },
                        '& .MuiListItemIcon-root': {
                          color: isActive
                            ? theme.palette.primary.main
                            : alpha(theme.palette.text.primary, 0.84),
                        },
                      }}
                    >
                      <ListItemIcon sx={{ minWidth: 42, mt: 0.15 }}>
                        {item.icon}
                      </ListItemIcon>
                      <ListItemText
                        primary={item.text}
                        secondary={item.detail}
                        primaryTypographyProps={{
                          fontWeight: 700,
                          color: isActive ? 'text.primary' : alpha(theme.palette.text.primary, 0.96),
                        }}
                        secondaryTypographyProps={{
                          variant: 'body2',
                          color: isActive
                            ? alpha(theme.palette.primary.main, 0.92)
                            : alpha(theme.palette.text.secondary, 0.96),
                          sx: {
                            mt: 0.25,
                            lineHeight: 1.45,
                            overflowWrap: 'anywhere',
                          },
                        }}
                      />
                    </ListItemButton>
                  </ListItem>
                );
              })}
            </List>
          </Box>
        ))}
      </Box>
    </Box>
  );

  return (
    <>
      {isMobile ? (
        <Drawer
          variant="temporary"
          open={open}
          onClose={onClose}
          ModalProps={{ keepMounted: true }}
          sx={{
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
            },
          }}
        >
          {drawerContent}
        </Drawer>
      ) : (
        <Drawer
          variant="persistent"
          open={open}
          sx={{
            width: open ? DRAWER_WIDTH : 0,
            flexShrink: 0,
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
            },
          }}
        >
          {drawerContent}
        </Drawer>
      )}
    </>
  );
};
