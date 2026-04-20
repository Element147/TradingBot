import { Stack, Typography } from '@mui/material';

import {
  StatusPill,
  SurfacePanel,
} from '@/components/ui/Workbench';
import {
  formatAuditActionLabel,
  formatAuditTargetLabel,
} from '@/features/settings/auditPresentation';
import { useGetAuditEventsQuery } from '@/features/settings/exchangeApi';
import { formatDateTime } from '@/utils/formatters';

export function OperatorAuditCard() {
  const { data, isLoading, isError } = useGetAuditEventsQuery(
    { limit: 6 },
    { pollingInterval: 30000, skipPollingIfUnfocused: true }
  );

  const events = data?.events ?? [];
  const summary = data?.summary;
  const groupedEvents = events.reduce<
    Array<{
      id: number | string;
      action: string;
      actor: string;
      environment: string;
      targetLabel: string;
      outcome: string;
      createdAt: string;
      repeatCount: number;
    }>
  >((groups, event) => {
    const targetLabel = formatAuditTargetLabel(event);
    const lastGroup = groups.at(-1);

    if (
      lastGroup &&
      lastGroup.action === event.action &&
      lastGroup.targetLabel === targetLabel &&
      lastGroup.outcome === event.outcome
    ) {
      lastGroup.repeatCount += 1;
      return groups;
    }

    groups.push({
      id: event.id,
      action: event.action,
      actor: event.actor,
      environment: event.environment,
      targetLabel,
      outcome: event.outcome,
      createdAt: event.createdAt,
      repeatCount: 1,
    });
    return groups;
  }, []);

  return (
    <SurfacePanel
      title="Operator Audit"
      description="Recent role-sensitive changes stay visible here before you open the deeper audit tools in Settings."
      sx={{ height: '100%' }}
    >
      {summary ? (
        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
          <StatusPill
            label={`${summary.visibleEventCount} recent events`}
            tone="info"
            variant="filled"
          />
          <StatusPill label={`${summary.successCount} success`} tone="success" />
          <StatusPill label={`${summary.failedCount} failed`} tone="warning" />
        </Stack>
      ) : null}

      {isLoading ? (
        <Typography variant="body2" color="text.secondary">
          Loading audit timeline...
        </Typography>
      ) : isError ? (
        <Typography variant="body2" color="error.main">
          Unable to load audit events.
        </Typography>
      ) : events.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          No operator audit events recorded yet.
        </Typography>
      ) : (
        <Stack spacing={1}>
          {groupedEvents.slice(0, 4).map((event) => (
            <Stack
              key={event.id}
              spacing={0.45}
              sx={{
                pt: 1,
                borderTop: '1px solid',
                borderColor: 'divider',
              }}
            >
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                spacing={0.75}
                justifyContent="space-between"
                alignItems={{ xs: 'flex-start', sm: 'center' }}
              >
                <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                  <Typography variant="subtitle2" sx={{ overflowWrap: 'anywhere' }}>
                    {formatAuditActionLabel(event.action)}
                  </Typography>
                  {event.repeatCount > 1 ? (
                    <StatusPill
                      label={`${event.repeatCount}x`}
                      tone="info"
                    />
                  ) : null}
                </Stack>
                <StatusPill
                  label={event.outcome}
                  tone={event.outcome === 'FAILED' ? 'warning' : 'success'}
                  variant="filled"
                />
              </Stack>
              <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
                {`${event.actor} | ${event.targetLabel}`}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {`${event.environment} | Latest ${formatDateTime(event.createdAt)}`}
              </Typography>
            </Stack>
          ))}
        </Stack>
      )}

      <Typography variant="caption" color="text.secondary">
        Open Settings &gt; Audit Trail for filters, expanded evidence, and copy actions.
      </Typography>
    </SurfacePanel>
  );
}
