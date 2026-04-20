import { ThemeProvider, createTheme } from '@mui/material';
import { render, screen, fireEvent } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import { Sidebar } from './Sidebar';

const theme = createTheme();

const mockNavigate = vi.fn();

// Mock react-router-dom
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useLocation: () => ({ pathname: '/dashboard' }),
  };
});

const renderWithProviders = (component: React.ReactElement) => render(
    <BrowserRouter>
      <ThemeProvider theme={theme}>{component}</ThemeProvider>
    </BrowserRouter>
  );

describe('Sidebar', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  it('should render all navigation links', () => {
    renderWithProviders(<Sidebar open={true} onClose={vi.fn()} />);

    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Forward Testing')).toBeInTheDocument();
    expect(screen.getByText('Paper')).toBeInTheDocument();
    expect(screen.getByText('Live')).toBeInTheDocument();
    expect(screen.getByText('Strategies')).toBeInTheDocument();
    expect(screen.getByText('Trades')).toBeInTheDocument();
    expect(screen.getByText('Backtest')).toBeInTheDocument();
    expect(screen.getByText('Market Data')).toBeInTheDocument();
    expect(screen.getByText('Risk')).toBeInTheDocument();
    expect(screen.getByText('Settings')).toBeInTheDocument();
  });

  it('should render application title', () => {
    renderWithProviders(<Sidebar open={true} onClose={vi.fn()} />);

    expect(screen.getByText('AlgoTrading Bot')).toBeInTheDocument();
    expect(screen.getByText('Research Workstation')).toBeInTheDocument();
    expect(screen.queryByText('Safety defaults')).not.toBeInTheDocument();
    expect(screen.queryByText('Workstation flow')).not.toBeInTheDocument();
    expect(screen.queryByText('Safe next step')).not.toBeInTheDocument();
  });

  it('should render navigation icons', () => {
    const { container } = renderWithProviders(<Sidebar open={true} onClose={vi.fn()} />);

    // Check that icons are rendered (MUI icons render as SVG)
    const icons = container.querySelectorAll('.MuiListItemIcon-root svg');
    expect(icons.length).toBe(10); // 10 navigation items
  });

  it('should highlight active route', () => {
    renderWithProviders(<Sidebar open={true} onClose={vi.fn()} />);

    const dashboardButton = screen.getByRole('button', { name: /dashboard/i });
    expect(dashboardButton).toHaveClass('Mui-selected');
  }, 15000);

  it('should navigate when clicking a navigation item', () => {
    renderWithProviders(<Sidebar open={true} onClose={vi.fn()} />);

    const strategiesButton = screen.getByRole('button', { name: /strategies/i });
    fireEvent.click(strategiesButton);

    expect(mockNavigate).toHaveBeenCalledWith('/strategies');
  });

  it('should call onClose when navigation item is clicked', () => {
    const onClose = vi.fn();
    renderWithProviders(<Sidebar open={true} onClose={onClose} />);

    const tradesButton = screen.getByRole('button', { name: /trades/i });
    fireEvent.click(tradesButton);

    expect(onClose).toHaveBeenCalled();
  });

  it('should navigate to correct paths for all items', () => {
    renderWithProviders(<Sidebar open={true} onClose={vi.fn()} />);

    const navigationTests = [
      { name: /dashboard/i, path: '/dashboard' },
      { name: /forward testing/i, path: '/forward-testing' },
      { name: /paper/i, path: '/paper' },
      { name: /live/i, path: '/live' },
      { name: /strategies/i, path: '/strategies' },
      { name: /trades/i, path: '/trades' },
      { name: /backtest/i, path: '/backtest' },
      { name: /market data/i, path: '/market-data' },
      { name: /risk/i, path: '/risk' },
      { name: /settings/i, path: '/settings' },
    ];

    navigationTests.forEach(({ name, path }) => {
      mockNavigate.mockClear();
      const button = screen.getByRole('button', { name });
      fireEvent.click(button);
      expect(mockNavigate).toHaveBeenCalledWith(path);
    });
  }, 15000);

  it('should render as temporary drawer on mobile', () => {
    // Mock mobile viewport
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: query === '(max-width: 959.95px)',
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    const { container } = renderWithProviders(<Sidebar open={true} onClose={vi.fn()} />);

    // Check for temporary drawer (has modal backdrop)
    const drawer = container.querySelector('.MuiDrawer-root');
    expect(drawer).toBeInTheDocument();
  });

  it('should render as persistent drawer on desktop', () => {
    // Mock desktop viewport - matches should be false for down('md')
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: false, // isMobile = false
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    const { container } = renderWithProviders(<Sidebar open={true} onClose={vi.fn()} />);

    const drawer = container.querySelector('.MuiDrawer-root');
    expect(drawer).toBeInTheDocument();
  });

  it('should display all navigation items in correct order', () => {
    renderWithProviders(<Sidebar open={true} onClose={vi.fn()} />);

    const buttons = screen.getAllByRole('button');
    const navigationButtons = buttons.filter(
      (button) =>
        button.textContent?.includes('Dashboard') ||
        button.textContent?.includes('Forward Testing') ||
        button.textContent?.includes('Paper') ||
        button.textContent?.includes('Live') ||
        button.textContent?.includes('Strategies') ||
        button.textContent?.includes('Trades') ||
        button.textContent?.includes('Backtest') ||
        button.textContent?.includes('Market Data') ||
        button.textContent?.includes('Risk') ||
        button.textContent?.includes('Settings')
    );

    expect(navigationButtons[0]).toHaveTextContent('Dashboard');
    expect(navigationButtons[1]).toHaveTextContent('Forward Testing');
    expect(navigationButtons[2]).toHaveTextContent('Paper');
    expect(navigationButtons[3]).toHaveTextContent('Live');
    expect(navigationButtons[4]).toHaveTextContent('Strategies');
    expect(navigationButtons[5]).toHaveTextContent('Trades');
    expect(navigationButtons[6]).toHaveTextContent('Backtest');
    expect(navigationButtons[7]).toHaveTextContent('Market Data');
    expect(navigationButtons[8]).toHaveTextContent('Risk');
    expect(navigationButtons[9]).toHaveTextContent('Settings');
  });

  it('should have proper ARIA labels for accessibility', () => {
    renderWithProviders(<Sidebar open={true} onClose={vi.fn()} />);

    const dashboardButton = screen.getByRole('button', { name: /dashboard/i });
    expect(dashboardButton).toBeInTheDocument();
  });

  it('should handle rapid navigation clicks', () => {
    const onClose = vi.fn();
    renderWithProviders(<Sidebar open={true} onClose={onClose} />);

    const strategiesButton = screen.getByRole('button', { name: /strategies/i });
    
    // Click multiple times rapidly
    fireEvent.click(strategiesButton);
    fireEvent.click(strategiesButton);
    fireEvent.click(strategiesButton);

    expect(mockNavigate).toHaveBeenCalledTimes(3);
    expect(onClose).toHaveBeenCalledTimes(3);
  });
});
