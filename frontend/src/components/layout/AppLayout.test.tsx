import { ThemeProvider, createTheme } from '@mui/material';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';

import { AppLayout } from './AppLayout';

// Mock child components
vi.mock('./Header', () => ({
  Header: ({ onMenuClick }: { onMenuClick: () => void }) => (
    <div data-testid="header">
      <button onClick={onMenuClick}>Menu</button>
    </div>
  ),
}));

vi.mock('./Sidebar', () => ({
  Sidebar: ({ open, onClose }: { open: boolean; onClose: () => void }) => (
    <div data-testid="sidebar" data-open={open}>
      <button onClick={onClose}>Close</button>
    </div>
  ),
}));

const theme = createTheme();

const renderWithTheme = (component: React.ReactElement) => render(<ThemeProvider theme={theme}>{component}</ThemeProvider>);

describe('AppLayout', () => {
  it('should render sidebar, header, and content area', () => {
    renderWithTheme(
      <AppLayout>
        <div data-testid="test-content">Test Content</div>
      </AppLayout>
    );

    expect(screen.getByTestId('sidebar')).toBeInTheDocument();
    expect(screen.getByTestId('header')).toBeInTheDocument();
    expect(screen.getByTestId('test-content')).toBeInTheDocument();
  });

  it('should render children in the main content area', () => {
    renderWithTheme(
      <AppLayout>
        <div>Child Component 1</div>
        <div>Child Component 2</div>
      </AppLayout>
    );

    expect(screen.getByText('Child Component 1')).toBeInTheDocument();
    expect(screen.getByText('Child Component 2')).toBeInTheDocument();
  });

  it('should toggle sidebar when menu button is clicked', () => {
    renderWithTheme(
      <AppLayout>
        <div>Content</div>
      </AppLayout>
    );

    const sidebar = screen.getByTestId('sidebar');
    const menuButton = screen.getByText('Menu');

    // Initial state - sidebar should be open on desktop
    expect(sidebar).toHaveAttribute('data-open', 'true');

    // Click menu button to close
    fireEvent.click(menuButton);
    expect(sidebar).toHaveAttribute('data-open', 'false');

    // Click again to open
    fireEvent.click(menuButton);
    expect(sidebar).toHaveAttribute('data-open', 'true');
  });

  it('should close sidebar when sidebar close button is clicked on mobile', () => {
    // Mock mobile viewport - matches should be true for down('md')
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: true, // isMobile = true
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    renderWithTheme(
      <AppLayout>
        <div>Content</div>
      </AppLayout>
    );

    const sidebar = screen.getByTestId('sidebar');
    
    // Sidebar starts closed on mobile
    expect(sidebar).toHaveAttribute('data-open', 'false');
    
    // Open sidebar first
    const menuButton = screen.getByText('Menu');
    fireEvent.click(menuButton);
    expect(sidebar).toHaveAttribute('data-open', 'true');

    // Click close button
    const closeButton = screen.getByText('Close');
    fireEvent.click(closeButton);
    expect(sidebar).toHaveAttribute('data-open', 'false');
  });

  it('should have proper layout structure with flex display', () => {
    const { container } = renderWithTheme(
      <AppLayout>
        <div>Content</div>
      </AppLayout>
    );

    const layoutBox = container.firstChild as HTMLElement;
    expect(layoutBox).toHaveStyle({ display: 'flex' });
  });

  it('should render main content area with proper styling', () => {
    renderWithTheme(
      <AppLayout>
        <div data-testid="content">Content</div>
      </AppLayout>
    );

    const content = screen.getByTestId('content');
    expect(content).toBeInTheDocument();
  });

  it('should start with sidebar open on desktop', () => {
    // Mock desktop viewport - matches should be false for down('md')
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: false, // isMobile = false, so sidebar starts open
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    renderWithTheme(
      <AppLayout>
        <div>Content</div>
      </AppLayout>
    );

    const sidebar = screen.getByTestId('sidebar');
    expect(sidebar).toHaveAttribute('data-open', 'true');
  });

  it('should start with sidebar closed on mobile', () => {
    // Mock mobile viewport - matches should be true for down('md')
    window.matchMedia = vi.fn().mockImplementation((query: string) => ({
      matches: true, // isMobile = true, so sidebar starts closed
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }));

    renderWithTheme(
      <AppLayout>
        <div>Content</div>
      </AppLayout>
    );

    const sidebar = screen.getByTestId('sidebar');
    expect(sidebar).toHaveAttribute('data-open', 'false');
  });
});
