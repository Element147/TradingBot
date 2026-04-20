import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { OperatorAuditCard } from './OperatorAuditCard';

vi.mock('@/features/settings/exchangeApi', () => ({
  useGetAuditEventsQuery: () => ({
    data: {
      summary: {
        visibleEventCount: 4,
        totalMatchingEvents: 4,
        successCount: 1,
        failedCount: 3,
        uniqueActors: 1,
        uniqueActions: 2,
        testEventCount: 0,
        paperEventCount: 4,
        liveEventCount: 0,
        latestEventAt: '2026-03-12T10:00:00',
      },
      events: [
        {
          id: 1,
          actor: 'admin',
          action: 'BACKTEST_RUN_STARTED',
          environment: 'paper',
          targetType: 'BACKTEST',
          targetId: '42',
          outcome: 'SUCCESS',
          details: 'strategy=BOLLINGER_BANDS, datasetId=7',
          createdAt: '2026-03-12T10:00:00',
        },
        {
          id: 2,
          actor: 'system',
          action: 'SYSTEM_BACKUP_FAILED',
          environment: 'paper',
          targetType: 'SYSTEM',
          targetId: null,
          outcome: 'FAILED',
          details: 'pg_dump unavailable',
          createdAt: '2026-03-12T09:30:00',
        },
        {
          id: 3,
          actor: 'system',
          action: 'SYSTEM_BACKUP_FAILED',
          environment: 'paper',
          targetType: 'SYSTEM',
          targetId: null,
          outcome: 'FAILED',
          details: 'pg_dump unavailable',
          createdAt: '2026-03-12T09:25:00',
        },
        {
          id: 4,
          actor: 'admin',
          action: 'SYSTEM_BACKUP_FAILED',
          environment: 'paper',
          targetType: 'SYSTEM',
          targetId: null,
          outcome: 'FAILED',
          details: 'disk unavailable',
          createdAt: '2026-03-12T09:20:00',
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
}));

describe('OperatorAuditCard', () => {
  it('renders recent audit summary and events', () => {
    render(<OperatorAuditCard />);

    expect(screen.getByText('Operator Audit')).toBeInTheDocument();
    expect(screen.getByText(/4 recent events/)).toBeInTheDocument();
    expect(screen.getByText('Backtest Run Started')).toBeInTheDocument();
    expect(screen.getByText('System Backup Failed')).toBeInTheDocument();
    expect(screen.getByText('3x')).toBeInTheDocument();
  });
});
