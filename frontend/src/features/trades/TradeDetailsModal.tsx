import {
  Alert,
  Box,
  CircularProgress,
  Dialog,
  DialogContent,
  DialogTitle,
  Grid,
  Typography,
} from '@mui/material';

import { useGetTradeDetailsQuery } from './tradesApi';
import { calculateRMultiple } from './tradeUtils';

import { formatCurrency } from '@/utils/formatters';

interface TradeDetailsModalProps {
  tradeId: number | null;
  accountId?: number;
  open: boolean;
  onClose: () => void;
}

export function TradeDetailsModal({ tradeId, accountId, open, onClose }: TradeDetailsModalProps) {
  const { data: trade, isFetching, isError } = useGetTradeDetailsQuery(
    { id: tradeId ?? 0, accountId },
    { skip: !open || tradeId === null }
  );

  if (!open || tradeId === null) {
    return null;
  }

  const rMultiple = trade ? calculateRMultiple(trade) : null;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>Trade Detail #{tradeId}</DialogTitle>
      <DialogContent>
        {isFetching ? (
          <Box display="flex" justifyContent="center" py={4}>
            <CircularProgress />
          </Box>
        ) : null}

        {isError ? <Alert severity="error">Failed to load trade details.</Alert> : null}

        {!isFetching && !isError && !trade ? (
          <Alert severity="warning">Trade details are unavailable.</Alert>
        ) : null}

        {!isFetching && !isError && trade ? (
          <Grid container spacing={1}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Typography variant="body2">Pair: {trade.pair}</Typography>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Typography variant="body2">Signal: {trade.signal}</Typography>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Typography variant="body2">Position Side: {trade.positionSide}</Typography>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Typography variant="body2">Entry Time: {trade.entryTime}</Typography>
          </Grid>
          <Grid size={{ xs: 12, md: 6 }}>
            <Typography variant="body2">Exit Time: {trade.exitTime ?? '-'}</Typography>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <Typography variant="body2">Entry Price: {trade.entryPrice.toFixed(4)}</Typography>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <Typography variant="body2">Exit Price: {trade.exitPrice?.toFixed(4) ?? '-'}</Typography>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <Typography variant="body2">Position Size: {trade.positionSize.toFixed(6)}</Typography>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <Typography variant="body2">Risk Amount: {formatCurrency(trade.riskAmount)}</Typography>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <Typography variant="body2">PnL: {formatCurrency(trade.pnl)}</Typography>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <Typography variant="body2">Fees: {formatCurrency(trade.feesActual)}</Typography>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <Typography variant="body2">Slippage: {formatCurrency(trade.slippageActual)}</Typography>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <Typography variant="body2">Stop Loss: {trade.stopLoss?.toFixed(4) ?? '-'}</Typography>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Typography variant="body2">Take Profit: {trade.takeProfit?.toFixed(4) ?? '-'}</Typography>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Typography variant="body2">
              R-Multiple: {rMultiple === null ? '-' : rMultiple.toFixed(2)}
            </Typography>
          </Grid>
          </Grid>
        ) : null}
      </DialogContent>
    </Dialog>
  );
}
