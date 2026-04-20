import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import PlayArrowOutlinedIcon from '@mui/icons-material/PlayArrowOutlined';
import { Button, Stack, Typography } from '@mui/material';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';

import type { PaperOrder } from '../paperApi';

import {
  InteractiveTable,
  useInteractiveTableState,
} from '@/components/ui/InteractiveTable';
import { NumericText, StatusPill } from '@/components/ui/Workbench';
import { formatCurrency, formatDateTime, formatNumber } from '@/utils/formatters';

interface PaperOrdersTablePanelProps {
  orders: PaperOrder[];
  busy: boolean;
  onFill: (orderId: number) => void | Promise<void>;
  onCancel: (orderId: number) => void | Promise<void>;
}

const sideTone = (side: PaperOrder['side']): 'success' | 'error' =>
  side === 'BUY' || side === 'COVER' ? 'success' : 'error';

const statusTone = (
  status: PaperOrder['status']
): 'success' | 'warning' | 'default' => {
  if (status === 'FILLED') {
    return 'success';
  }
  if (status === 'NEW') {
    return 'warning';
  }
  return 'default';
};

export function PaperOrdersTablePanel({
  orders,
  busy,
  onFill,
  onCancel,
}: PaperOrdersTablePanelProps) {
  const tableStateControls = useInteractiveTableState({
    tableId: 'paper-orders',
    initialPageSize: 10,
  });

  const columns = useMemo<ColumnDef<PaperOrder>[]>(
    () => [
      {
        accessorKey: 'id',
        header: 'ID',
        size: 82,
        minSize: 82,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Paper order identifier.',
        },
        cell: ({ row }) => <NumericText variant="body2">#{row.original.id}</NumericText>,
      },
      {
        accessorKey: 'symbol',
        header: 'Symbol',
        size: 150,
        minSize: 136,
        meta: {
          filterVariant: 'text',
          filterPlaceholder: 'Symbol',
          headerDescription: 'Provider-compatible paper symbol.',
        },
      },
      {
        accessorKey: 'side',
        header: 'Side',
        size: 130,
        minSize: 120,
        meta: {
          filterVariant: 'select',
          filterOptions: [
            { label: 'Buy', value: 'BUY' },
            { label: 'Sell', value: 'SELL' },
            { label: 'Short', value: 'SHORT' },
            { label: 'Cover', value: 'COVER' },
          ],
          headerDescription: 'Order side used by the paper engine.',
        },
        cell: ({ row }) => (
          <StatusPill label={row.original.side} tone={sideTone(row.original.side)} variant="filled" />
        ),
      },
      {
        accessorKey: 'status',
        header: 'Status',
        size: 130,
        minSize: 116,
        meta: {
          filterVariant: 'select',
          filterOptions: [
            { label: 'New', value: 'NEW' },
            { label: 'Filled', value: 'FILLED' },
            { label: 'Cancelled', value: 'CANCELLED' },
          ],
          headerDescription: 'Current paper-order status.',
        },
        cell: ({ row }) => (
          <StatusPill
            label={row.original.status}
            tone={statusTone(row.original.status)}
            variant="filled"
          />
        ),
      },
      {
        accessorKey: 'quantity',
        header: 'Qty',
        size: 110,
        minSize: 96,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Requested quantity.',
        },
        cell: ({ row }) => <NumericText variant="body2">{formatNumber(row.original.quantity, 4)}</NumericText>,
      },
      {
        accessorKey: 'price',
        header: 'Price',
        size: 120,
        minSize: 106,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Requested price.',
        },
        cell: ({ row }) => <NumericText variant="body2">{formatCurrency(row.original.price, 4)}</NumericText>,
      },
      {
        accessorKey: 'fillPrice',
        header: 'Fill',
        size: 120,
        minSize: 106,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Executed fill price if already filled.',
        },
        cell: ({ row }) => (
          <NumericText variant="body2">
            {row.original.fillPrice === null ? '-' : formatCurrency(row.original.fillPrice, 4)}
          </NumericText>
        ),
      },
      {
        accessorKey: 'fees',
        header: 'Fees',
        size: 116,
        minSize: 104,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Simulated fees.',
        },
        cell: ({ row }) => (
          <NumericText variant="body2">
            {row.original.fees === null ? '-' : formatCurrency(row.original.fees, 4)}
          </NumericText>
        ),
      },
      {
        accessorKey: 'slippage',
        header: 'Slippage',
        size: 126,
        minSize: 114,
        meta: {
          align: 'right',
          filterVariant: 'none',
          headerDescription: 'Simulated slippage.',
        },
        cell: ({ row }) => (
          <NumericText variant="body2">
            {row.original.slippage === null ? '-' : formatCurrency(row.original.slippage, 4)}
          </NumericText>
        ),
      },
      {
        accessorKey: 'createdAt',
        header: 'Created',
        size: 170,
        minSize: 150,
        meta: {
          filterVariant: 'none',
          headerDescription: 'Order creation timestamp.',
        },
        cell: ({ row }) => <Typography variant="body2">{formatDateTime(row.original.createdAt)}</Typography>,
      },
      {
        id: 'actions',
        header: 'Actions',
        enableSorting: false,
        enableColumnFilter: false,
        size: 176,
        minSize: 164,
        meta: {
          filterVariant: 'none',
          headerDescription: 'Fill or cancel NEW orders.',
        },
        cell: ({ row }) => (
          <Stack
            direction={{ xs: 'column', xl: 'row' }}
            spacing={0.75}
            onClick={(event) => event.stopPropagation()}
          >
            <Button
              size="small"
              startIcon={<PlayArrowOutlinedIcon />}
              disabled={busy || row.original.status !== 'NEW'}
              onClick={() => void onFill(row.original.id)}
            >
              Fill
            </Button>
            <Button
              size="small"
              color="inherit"
              startIcon={<CancelOutlinedIcon />}
              disabled={busy || row.original.status !== 'NEW'}
              onClick={() => void onCancel(row.original.id)}
            >
              Cancel
            </Button>
          </Stack>
        ),
      },
    ],
    [busy, onCancel, onFill]
  );

  return (
    <InteractiveTable
      title="Paper orders"
      description="Paper-order history now uses the same compact grid shell as research tables so fills, cancellations, and stale orders are easier to scan."
      data={orders}
      columns={columns}
      stateControls={tableStateControls}
      emptyTitle="No paper orders yet"
      emptyDescription="Submit one above to test fills, cancellations, and order-state transitions."
      loading={false}
      globalFilterPlaceholder="Order ID, symbol, side, or status"
      stats={
        <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
          <StatusPill label={`${orders.length} orders`} tone={orders.length > 0 ? 'info' : 'default'} variant="filled" />
          <StatusPill label={`${orders.filter((order) => order.status === 'NEW').length} open`} tone="warning" />
          <StatusPill label={`${orders.filter((order) => order.status === 'FILLED').length} filled`} tone="success" />
        </Stack>
      }
      getRowId={(row) => String(row.id)}
      maxHeight={620}
    />
  );
}
