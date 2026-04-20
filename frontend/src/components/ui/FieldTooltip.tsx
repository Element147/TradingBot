import { Box, Tooltip } from '@mui/material';
import type { ReactNode } from 'react';

interface FieldTooltipProps {
  title: string;
  children: ReactNode;
}

/**
 * Wraps form fields with a consistent tooltip that explains intent and consequences.
 */
export function FieldTooltip({ title, children }: FieldTooltipProps) {
  return (
    <Tooltip title={title} arrow placement="top-start" describeChild>
      <Box>{children}</Box>
    </Tooltip>
  );
}
