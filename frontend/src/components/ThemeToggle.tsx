import { Brightness4, Brightness7 } from '@mui/icons-material';
import { IconButton, Tooltip } from '@mui/material';
import { useDispatch, useSelector } from 'react-redux';

import { setTheme, selectTheme } from '@/features/settings/settingsSlice';

/**
 * ThemeToggle Component
 * 
 * Provides a button to toggle between light and dark themes
 * Theme preference is persisted to localStorage automatically
 * 
 * Features:
 * - Icon changes based on current theme
 * - Tooltip shows action (e.g., "Switch to dark mode")
 * - Immediate theme application without page reload
 * - Accessible with keyboard navigation
 */
const ThemeToggle: React.FC = () => {
  const dispatch = useDispatch();
  const theme = useSelector(selectTheme);

  const handleToggle = () => {
    const newTheme = theme === 'light' ? 'dark' : 'light';
    dispatch(setTheme(newTheme));
  };

  return (
    <Tooltip title={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}>
      <IconButton
        onClick={handleToggle}
        color="inherit"
        aria-label={`Switch to ${theme === 'light' ? 'dark' : 'light'} mode`}
        sx={{
          ml: 1,
        }}
      >
        {theme === 'light' ? <Brightness4 /> : <Brightness7 />}
      </IconButton>
    </Tooltip>
  );
};

export default ThemeToggle;
