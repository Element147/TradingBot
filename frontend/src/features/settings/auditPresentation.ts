import type { ChipProps } from '@mui/material';

import type { OperatorAuditEvent } from './exchangeApi';

export const formatAuditActionLabel = (action: string): string =>
  action
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');

export const formatAuditTargetLabel = (event: OperatorAuditEvent): string =>
  event.targetId ? `${event.targetType}:${event.targetId}` : event.targetType;

export const getAuditOutcomeColor = (outcome: string): ChipProps['color'] =>
  outcome.toUpperCase() === 'SUCCESS' ? 'success' : 'error';

export const getAuditEnvironmentColor = (environment: string): ChipProps['color'] => {
  const normalized = environment.toLowerCase();
  if (normalized === 'paper') {
    return 'info';
  }
  if (normalized === 'live') {
    return 'warning';
  }
  return 'default';
};

export const splitAuditDetails = (details: string | null): string[] =>
  (details ?? '')
    .split(',')
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
