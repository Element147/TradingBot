import { Box, useMediaQuery, useTheme } from '@mui/material';
import React, { useState } from 'react';

import { Header } from './Header';
import { Sidebar } from './Sidebar';

interface AppLayoutProps {
  children: React.ReactNode;
}

export const AppLayout: React.FC<AppLayoutProps> = ({ children }) => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md')); // < 960px
  const [desktopSidebarOpen, setDesktopSidebarOpen] = useState(true);
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false);
  const sidebarOpen = isMobile ? mobileSidebarOpen : desktopSidebarOpen;

  const handleMenuClick = () => {
    if (isMobile) {
      setMobileSidebarOpen((current) => !current);
      return;
    }

    setDesktopSidebarOpen((current) => !current);
  };

  const handleSidebarClose = () => {
    if (isMobile) {
      setMobileSidebarOpen(false);
    }
  };

  return (
    <Box
      sx={{
        display: 'flex',
        minHeight: '100vh',
        bgcolor: 'background.default',
        backgroundImage: (theme) =>
          `radial-gradient(circle at top right, ${
            theme.palette.mode === 'light'
              ? 'rgba(24, 102, 96, 0.10)'
              : 'rgba(132, 225, 212, 0.10)'
          } 0%, transparent 28%),
           radial-gradient(circle at left 22%, ${
             theme.palette.mode === 'light'
               ? 'rgba(179, 106, 31, 0.09)'
               : 'rgba(255, 200, 112, 0.08)'
           } 0%, transparent 24%)`,
      }}
    >
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>
      <Box component="aside" aria-label="Primary navigation">
        <Sidebar open={sidebarOpen} onClose={handleSidebarClose} />
      </Box>

      <Box
        component="section"
        sx={{
          flexGrow: 1,
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100vh',
          minWidth: 0,
          transition: theme.transitions.create(['margin'], {
            easing: theme.transitions.easing.sharp,
            duration: theme.transitions.duration.leavingScreen,
          }),
        }}
      >
        <Box component="header">
          <Header onMenuClick={handleMenuClick} />
        </Box>
        
        <Box
          id="main-content"
          component="main"
          role="main"
          sx={{
            flexGrow: 1,
            px: { xs: 1.5, sm: 2.5, lg: 3.5 },
            pb: { xs: 3, sm: 4, lg: 5 },
            pt: { xs: 1.5, sm: 2, lg: 2.5 },
            position: 'relative',
            minWidth: 0,
          }}
        >
          <Box
            sx={{
              width: '100%',
              mx: 'auto',
              minWidth: 0,
              display: 'flex',
              flexDirection: 'column',
              gap: theme.spacing(3),
            }}
          >
            {children}
          </Box>
        </Box>
      </Box>
    </Box>
  );
};
