import CancelOutlinedIcon from '@mui/icons-material/CancelOutlined';
import PlayArrowOutlinedIcon from '@mui/icons-material/PlayArrowOutlined';
import {
  Alert,
  Button,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TableContainer,
  TextField,
  Typography,
} from '@mui/material';

import type {
  PaperOrder,
  PaperOrderSide,
  PaperTradingState,
} from '../paperApi';

import { FieldTooltip } from '@/components/ui/FieldTooltip';
import {
  EmptyState,
  NumericText,
  StatusPill,
  SurfacePanel,
} from '@/components/ui/Workbench';
import { formatCurrency, formatDateTime, formatNumber } from '@/utils/formatters';

interface PaperOrderFormState {
  symbol: string;
  side: PaperOrderSide;
  quantity: string;
  price: string;
  executeNow: boolean;
}

interface PaperTradingSummaryPanelProps {
  state: PaperTradingState;
}

export function PaperTradingSummaryPanel({
  state,
}: PaperTradingSummaryPanelProps) {
  const recoverySeverity =
    state.recoveryStatus === 'ATTENTION'
      ? 'warning'
      : state.recoveryStatus === 'HEALTHY'
        ? 'success'
        : 'info';

  return (
    <Stack spacing={2}>
      <SurfacePanel
        title="Desk posture"
        description="Paper execution stays explicit even when the global environment switch is set to live."
        actions={
          <StatusPill
            label={state.paperMode ? 'Paper active' : 'Paper inactive'}
            tone={state.paperMode ? 'success' : 'warning'}
            variant="filled"
          />
        }
      >
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <StatusPill label={`Cash ${formatCurrency(state.cashBalance)}`} tone="info" />
          <StatusPill label={`${formatNumber(state.positionCount)} positions`} />
          <StatusPill label={`${formatNumber(state.openOrders)} open orders`} tone="warning" />
          <StatusPill label={`${formatNumber(state.filledOrders)} filled`} tone="success" />
          <StatusPill label={`${formatNumber(state.cancelledOrders)} cancelled`} />
        </Stack>
        <Typography variant="body2" color="text.secondary">
          Last order: {state.lastOrderAt ? formatDateTime(state.lastOrderAt) : 'No orders yet'}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Last position update:{' '}
          {state.lastPositionUpdateAt
            ? formatDateTime(state.lastPositionUpdateAt)
            : 'No position changes yet'}
        </Typography>
      </SurfacePanel>

      <SurfacePanel
        title="Recovery and stale state"
        description="Use this block before placing follow-up orders when the desk may be recovering from incidents or stale state."
        tone={recoverySeverity}
        actions={
          <StatusPill
            label={`Recovery ${state.recoveryStatus}`}
            tone={recoverySeverity}
            variant="filled"
          />
        }
      >
        <Alert severity={recoverySeverity}>{state.recoveryMessage}</Alert>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          <StatusPill
            label={`${formatNumber(state.staleOpenOrderCount)} stale orders`}
            tone={state.staleOpenOrderCount > 0 ? 'warning' : 'success'}
          />
          <StatusPill
            label={`${formatNumber(state.stalePositionCount)} stale positions`}
            tone={state.stalePositionCount > 0 ? 'warning' : 'success'}
          />
        </Stack>
      </SurfacePanel>

      <SurfacePanel
        title="Incident context"
        description="Operator-facing signals stay separate from order entry so recovery context is visible without crowding the desk."
      >
        <Typography variant="body2" color="text.secondary">
          {state.incidentSummary}
        </Typography>
        <Stack spacing={1}>
          {state.alerts.length === 0 ? (
            <EmptyState
              title="No paper incidents right now"
              description="New recovery or stale-state signals will surface here."
              tone="info"
            />
          ) : (
            state.alerts.map((alert) => (
              <Alert
                key={alert.code}
                severity={alert.severity === 'WARNING' ? 'warning' : 'info'}
              >
                {alert.summary} {alert.recommendedAction}
              </Alert>
            ))
          )}
        </Stack>
      </SurfacePanel>
    </Stack>
  );
}

interface PaperOrderEntryPanelProps {
  form: PaperOrderFormState;
  busy: boolean;
  onChange: (next: PaperOrderFormState) => void;
  onSubmit: () => void | Promise<void>;
}

export function PaperOrderEntryPanel({
  form,
  busy,
  onChange,
  onSubmit,
}: PaperOrderEntryPanelProps) {
  return (
    <SurfacePanel
      title="Order entry"
      description="Paper orders stay simulated. Review the side carefully so SELL and COVER are used only against matching open exposure."
      tone="info"
      elevated
      actions={
        <StatusPill
          label={form.executeNow ? 'Immediate execution' : 'Queue order only'}
          tone={form.executeNow ? 'success' : 'default'}
          variant="filled"
        />
      }
    >
      <Grid container spacing={2}>
        <Grid size={{ xs: 12, md: 6 }}>
          <FieldTooltip title="Provider-compatible paper symbol, for example BTC/USDT or AAPL.">
            <TextField
              fullWidth
              label="Symbol"
              value={form.symbol}
              onChange={(event) => onChange({ ...form, symbol: event.target.value })}
            />
          </FieldTooltip>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <FormControl fullWidth>
            <InputLabel id="paper-order-side-label">Side</InputLabel>
            <Select
              labelId="paper-order-side-label"
              value={form.side}
              label="Side"
              onChange={(event) =>
                onChange({
                  ...form,
                  side: event.target.value as PaperOrderSide,
                })
              }
            >
              <MenuItem value="BUY">BUY</MenuItem>
              <MenuItem value="SELL">SELL</MenuItem>
              <MenuItem value="SHORT">SHORT</MenuItem>
              <MenuItem value="COVER">COVER</MenuItem>
            </Select>
          </FormControl>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <FieldTooltip title="Paper quantity must be positive and reflect realistic simulated size.">
            <TextField
              fullWidth
              label="Quantity"
              type="number"
              value={form.quantity}
              onChange={(event) => onChange({ ...form, quantity: event.target.value })}
              inputProps={{ min: 0.00000001, step: 'any' }}
            />
          </FieldTooltip>
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <FieldTooltip title="Reference limit or mark price used by the paper engine before fees and slippage.">
            <TextField
              fullWidth
              label="Price"
              type="number"
              value={form.price}
              onChange={(event) => onChange({ ...form, price: event.target.value })}
              inputProps={{ min: 0.00000001, step: 'any' }}
            />
          </FieldTooltip>
        </Grid>
      </Grid>

      <FormControlLabel
        control={
          <Switch
            checked={form.executeNow}
            onChange={(event) =>
              onChange({ ...form, executeNow: event.target.checked })
            }
          />
        }
        label="Execute immediately"
      />

      <Button
        variant="contained"
        startIcon={<PlayArrowOutlinedIcon />}
        onClick={() => void onSubmit()}
        disabled={busy}
      >
        Submit paper order
      </Button>
    </SurfacePanel>
  );
}

interface PaperOrdersPanelProps {
  orders: PaperOrder[];
  busy: boolean;
  onFill: (orderId: number) => void | Promise<void>;
  onCancel: (orderId: number) => void | Promise<void>;
}

export function PaperOrdersPanel({
  orders,
  busy,
  onFill,
  onCancel,
}: PaperOrdersPanelProps) {
  return (
    <SurfacePanel
      title="Paper orders"
      description="Review open, filled, and cancelled simulated orders. Only NEW orders can still be filled or cancelled."
      actions={busy ? <StatusPill label="Updating order state" tone="warning" /> : undefined}
    >
      {orders.length === 0 ? (
        <EmptyState
          title="No paper orders yet"
          description="Submit one above to test fills, cancellations, and order-state transitions."
          tone="info"
        />
      ) : (
        <TableContainer>
          <Table size="small" sx={{ minWidth: 1040 }}>
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Symbol</TableCell>
                <TableCell>Side</TableCell>
                <TableCell>Status</TableCell>
                <TableCell align="right">Qty</TableCell>
                <TableCell align="right">Price</TableCell>
                <TableCell align="right">Fill</TableCell>
                <TableCell align="right">Fees</TableCell>
                <TableCell align="right">Slippage</TableCell>
                <TableCell>Created</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {orders.map((order) => (
                <TableRow key={order.id} hover>
                  <TableCell>#{order.id}</TableCell>
                  <TableCell>{order.symbol}</TableCell>
                  <TableCell>
                    <StatusPill
                      label={order.side}
                      tone={
                        order.side === 'BUY' || order.side === 'COVER'
                          ? 'success'
                          : 'error'
                      }
                      variant="filled"
                    />
                  </TableCell>
                  <TableCell>{order.status}</TableCell>
                  <TableCell align="right">
                    <NumericText variant="body2">
                      {formatNumber(order.quantity, 4)}
                    </NumericText>
                  </TableCell>
                  <TableCell align="right">
                    <NumericText variant="body2">
                      {formatCurrency(order.price, 4)}
                    </NumericText>
                  </TableCell>
                  <TableCell align="right">
                    <NumericText variant="body2">
                      {order.fillPrice === null ? '-' : formatCurrency(order.fillPrice, 4)}
                    </NumericText>
                  </TableCell>
                  <TableCell align="right">
                    <NumericText variant="body2">
                      {order.fees === null ? '-' : formatCurrency(order.fees, 4)}
                    </NumericText>
                  </TableCell>
                  <TableCell align="right">
                    <NumericText variant="body2">
                      {order.slippage === null ? '-' : formatCurrency(order.slippage, 4)}
                    </NumericText>
                  </TableCell>
                  <TableCell>{formatDateTime(order.createdAt)}</TableCell>
                  <TableCell align="right">
                    <Stack direction="row" spacing={1} justifyContent="flex-end">
                      <Button
                        size="small"
                        startIcon={<PlayArrowOutlinedIcon />}
                        disabled={busy || order.status !== 'NEW'}
                        onClick={() => void onFill(order.id)}
                      >
                        Fill
                      </Button>
                      <Button
                        size="small"
                        color="inherit"
                        startIcon={<CancelOutlinedIcon />}
                        disabled={busy || order.status !== 'NEW'}
                        onClick={() => void onCancel(order.id)}
                      >
                        Cancel
                      </Button>
                    </Stack>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </SurfacePanel>
  );
}
