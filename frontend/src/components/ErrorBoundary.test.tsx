import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';

import ErrorBoundary from './ErrorBoundary';

// Component that throws an error for testing
const ThrowError = ({ shouldThrow }: { shouldThrow: boolean }) => {
  if (shouldThrow) {
    throw new Error('Test error');
  }
  return <div>No error</div>;
};

describe('ErrorBoundary', { timeout: 15000 }, () => {
  // Suppress console.error for these tests
  const originalError = console.error;
  beforeEach(() => {
    console.error = vi.fn();
  });

  afterEach(() => {
    console.error = originalError;
  });

  it('renders children when there is no error', () => {
    render(
      <ErrorBoundary>
        <div>Test content</div>
      </ErrorBoundary>
    );

    expect(screen.getByText('Test content')).toBeInTheDocument();
  });

  it('renders error fallback when child component throws', () => {
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    );

    expect(screen.getByText(/rendering error/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reload page/i })).toBeInTheDocument();
  });

  it('does not render error fallback when child component does not throw', () => {
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={false} />
      </ErrorBoundary>
    );

    expect(screen.getByText('No error')).toBeInTheDocument();
    expect(screen.queryByText(/rendering error/i)).not.toBeInTheDocument();
  });

  it('calls onError callback when error is caught', () => {
    const onError = vi.fn();

    render(
      <ErrorBoundary onError={onError}>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    );

    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError).toHaveBeenCalledWith(
      expect.any(Error),
      expect.objectContaining({
        componentStack: expect.any(String),
      })
    );
  });

  it('renders custom fallback when provided', () => {
    const customFallback = <div>Custom error message</div>;

    render(
      <ErrorBoundary fallback={customFallback}>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    );

    expect(screen.getByText('Custom error message')).toBeInTheDocument();
    expect(screen.queryByText(/something went wrong/i)).not.toBeInTheDocument();
  });

  it('resets error state when reset button is clicked', async () => {
    let shouldThrow = true;
    const RecoverableError = () => {
      if (shouldThrow) {
        throw new Error('Recoverable error');
      }

      return <div>Recovered after retry</div>;
    };

    render(
      <ErrorBoundary>
        <RecoverableError />
      </ErrorBoundary>
    );

    expect(screen.getByText(/rendering error/i)).toBeInTheDocument();

    shouldThrow = false;
    fireEvent.click(screen.getByRole('button', { name: /retry rendering/i }));

    await waitFor(() => {
      expect(screen.getByText('Recovered after retry')).toBeInTheDocument();
    });
  });

  it('logs error to console in development', () => {
    const consoleErrorSpy = vi.spyOn(console, 'error');

    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    );

    expect(consoleErrorSpy).toHaveBeenCalled();
  });

  it('displays error details in development mode', () => {
    // In Vitest, import.meta.env.DEV is already true by default
    // We just need to verify the error details are shown
    render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    );

    // Should show error details section (only visible in dev mode)
    expect(screen.getByText(/error details/i)).toBeInTheDocument();
    // Use getAllByText since "test error" appears multiple times (error message and stack trace)
    const errorTexts = screen.getAllByText(/test error/i);
    expect(errorTexts.length).toBeGreaterThan(0);
  });

  it('handles multiple errors correctly', () => {
    const { rerender } = render(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    );

    // First error
    expect(screen.getByText(/rendering error/i)).toBeInTheDocument();

    const retryButton = screen.getByRole('button', { name: /retry rendering/i });
    fireEvent.click(retryButton);

    // Rerender with another error
    rerender(
      <ErrorBoundary>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    );

    // Should still show error fallback
    expect(screen.getByText(/rendering error/i)).toBeInTheDocument();
  });

  it('preserves error information in state', () => {
    const onError = vi.fn();

    render(
      <ErrorBoundary onError={onError}>
        <ThrowError shouldThrow={true} />
      </ErrorBoundary>
    );

    const [error, errorInfo] = onError.mock.calls[0];
    expect(error.message).toBe('Test error');
    expect(errorInfo.componentStack).toBeDefined();
  });
});
