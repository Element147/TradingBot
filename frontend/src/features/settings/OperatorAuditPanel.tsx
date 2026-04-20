import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import RefreshIcon from '@mui/icons-material/Refresh';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';

import {
  formatAuditActionLabel,
  formatAuditTargetLabel,
  getAuditEnvironmentColor,
  getAuditOutcomeColor,
  splitAuditDetails,
} from './auditPresentation';
import { useGetAuditEventsQuery } from './exchangeApi';

import { formatDateTime } from '@/utils/formatters';

const LIMIT_OPTIONS = [25, 50, 100, 250];

export function OperatorAuditPanel() {
  const [limit, setLimit] = useState(100);
  const [environment, setEnvironment] = useState('');
  const [outcome, setOutcome] = useState('');
  const [targetType, setTargetType] = useState('');
  const [search, setSearch] = useState('');
  const [feedback, setFeedback] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useGetAuditEventsQuery(
    {
      limit,
      environment,
      outcome,
      targetType,
      search: search.trim(),
    },
    {
      pollingInterval: 30000,
      skipPollingIfUnfocused: true,
    }
  );

  const events = useMemo(() => data?.events ?? [], [data?.events]);
  const summary = data?.summary;

  const targetTypes = useMemo(
    () =>
      Array.from(new Set(events.map((event) => event.targetType).filter((value) => value.length > 0))).sort(),
    [events]
  );

  const activeFilters = [
    environment ? `Env: ${environment}` : null,
    outcome ? `Outcome: ${outcome}` : null,
    targetType ? `Target: ${targetType}` : null,
    search.trim() ? `Search: ${search.trim()}` : null,
  ].filter((value): value is string => Boolean(value));

  const copyEventDetails = async (event: { id: number; details: string | null }) => {
    const text = event.details?.trim() || `Audit event #${event.id} has no detail payload.`;
    try {
      await navigator.clipboard.writeText(text);
      setFeedback(`Copied details for audit event #${event.id}.`);
    } catch {
      setFeedback(`Clipboard copy failed for audit event #${event.id}.`);
    }
  };

  return (
    <Card>
      <CardContent>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={1}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', md: 'center' }}
          sx={{ mb: 2 }}
        >
          <Box>
            <Typography variant="h6">Operator Audit Trail</Typography>
            <Typography variant="body2" color="text.secondary">
              Filter, review, and copy recent operator actions and system outcomes.
            </Typography>
          </Box>
          <Stack direction="row" spacing={1}>
            <Button
              variant="text"
              onClick={() => {
                setEnvironment('');
                setOutcome('');
                setTargetType('');
                setSearch('');
                setFeedback(null);
              }}
              disabled={!activeFilters.length}
            >
              Clear Filters
            </Button>
            <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => void refetch()} disabled={isLoading}>
              Refresh Audit Trail
            </Button>
          </Stack>
        </Stack>

        {feedback ? (
          <Alert severity={feedback.startsWith('Copied') ? 'success' : 'warning'} sx={{ mb: 2 }} onClose={() => setFeedback(null)}>
            {feedback}
          </Alert>
        ) : null}

        <Grid container spacing={1.5} sx={{ mb: 2 }}>
          <Grid size={{ xs: 12, md: 3 }}>
            <TextField
              select
              label="Window Size"
              value={String(limit)}
              onChange={(event) => setLimit(Number(event.target.value))}
              fullWidth
            >
              {LIMIT_OPTIONS.map((option) => (
                <MenuItem key={option} value={String(option)}>
                  Last {option}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, md: 3 }}>
            <TextField
              select
              label="Environment"
              value={environment}
              onChange={(event) => setEnvironment(event.target.value)}
              fullWidth
            >
              <MenuItem value="">All environments</MenuItem>
              <MenuItem value="test">test</MenuItem>
              <MenuItem value="paper">paper</MenuItem>
              <MenuItem value="live">live</MenuItem>
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, md: 3 }}>
            <TextField
              select
              label="Outcome"
              value={outcome}
              onChange={(event) => setOutcome(event.target.value)}
              fullWidth
            >
              <MenuItem value="">All outcomes</MenuItem>
              <MenuItem value="SUCCESS">SUCCESS</MenuItem>
              <MenuItem value="FAILED">FAILED</MenuItem>
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, md: 3 }}>
            <TextField
              select
              label="Target Type"
              value={targetType}
              onChange={(event) => setTargetType(event.target.value)}
              fullWidth
            >
              <MenuItem value="">All targets</MenuItem>
              {targetTypes.map((value) => (
                <MenuItem key={value} value={value}>
                  {value}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12 }}>
            <TextField
              label="Search Actor, Action, Target, or Details"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              fullWidth
            />
          </Grid>
        </Grid>

        {summary ? (
          <Grid container spacing={1.5} sx={{ mb: 2 }}>
            <Grid size={{ xs: 12, md: 3 }}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="caption" color="text.secondary">
                    Current Audit Window
                  </Typography>
                  <Typography variant="h6">
                    {summary.visibleEventCount} / {summary.totalMatchingEvents}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="caption" color="text.secondary">
                    Outcomes
                  </Typography>
                  <Typography variant="body1">
                    {summary.successCount} success / {summary.failedCount} failed
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="caption" color="text.secondary">
                    Coverage
                  </Typography>
                  <Typography variant="body1">
                    {summary.uniqueActors} actors / {summary.uniqueActions} actions
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="caption" color="text.secondary">
                    Environments
                  </Typography>
                  <Typography variant="body1">
                    test {summary.testEventCount} | paper {summary.paperEventCount} | live {summary.liveEventCount}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        ) : null}

        {activeFilters.length > 0 ? (
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mb: 2 }}>
            {activeFilters.map((filter) => (
              <Chip key={filter} size="small" label={filter} />
            ))}
          </Stack>
        ) : null}

        {isError ? <Alert severity="error">Unable to load operator audit events.</Alert> : null}
        {isLoading ? <Typography>Loading audit events...</Typography> : null}

        {!isLoading && !isError && events.length === 0 ? (
          <Alert severity="info">No operator audit events match the current filters.</Alert>
        ) : null}

        {!isLoading && !isError && events.length > 0 ? (
          <Stack spacing={1.25}>
            {events.map((event) => (
              <Accordion key={event.id} disableGutters>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Stack
                    direction={{ xs: 'column', md: 'row' }}
                    spacing={1}
                    justifyContent="space-between"
                    alignItems={{ xs: 'flex-start', md: 'center' }}
                    sx={{ width: '100%', pr: 1 }}
                  >
                    <Box>
                      <Typography variant="subtitle2">{formatAuditActionLabel(event.action)}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {event.actor} | {formatAuditTargetLabel(event)} | {formatDateTime(event.createdAt)}
                      </Typography>
                    </Box>
                    <Stack direction="row" spacing={1}>
                      <Chip size="small" label={event.environment} color={getAuditEnvironmentColor(event.environment)} />
                      <Chip size="small" label={event.outcome} color={getAuditOutcomeColor(event.outcome)} />
                    </Stack>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails>
                  <Stack spacing={1}>
                    <Typography variant="body2" color="text.secondary">
                      Event #{event.id} | Target type {event.targetType}
                    </Typography>
                    {splitAuditDetails(event.details).length > 0 ? (
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        {splitAuditDetails(event.details).map((detail) => (
                          <Chip key={`${event.id}-${detail}`} size="small" variant="outlined" label={detail} />
                        ))}
                      </Stack>
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        No additional details were recorded for this event.
                      </Typography>
                    )}
                    <Box>
                      <Button
                        size="small"
                        startIcon={<ContentCopyIcon />}
                        onClick={() => void copyEventDetails(event)}
                      >
                        Copy Details
                      </Button>
                    </Box>
                  </Stack>
                </AccordionDetails>
              </Accordion>
            ))}
          </Stack>
        ) : null}
      </CardContent>
    </Card>
  );
}
