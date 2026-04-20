import RefreshIcon from '@mui/icons-material/Refresh';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import type { Dispatch, SetStateAction } from 'react';

import type {
  ExchangeBalanceResponse,
  ExchangeConnectionProfile,
  ExchangeConnectionStatus,
  ExchangeOrder,
  SystemInfo,
} from './exchangeApi';
import type { NotificationSettings } from './settingsSlice';

import { FieldTooltip } from '@/components/ui/FieldTooltip';
import type { EnvironmentMode } from '@/features/environment/environmentSlice';
import { getApiErrorMessage } from '@/services/api';

const parseNumberInput = (value: string, fallback: number) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

type DisplayDraft = {
  theme: 'light' | 'dark';
  currency: 'USD' | 'BTC';
  timezone: string;
  textScale: number;
  environmentMode: EnvironmentMode;
};

interface NotificationSettingsPanelProps {
  notificationDraft: NotificationSettings;
  onNotificationDraftChange: Dispatch<SetStateAction<NotificationSettings>>;
  hasNotificationChanges: boolean;
  onSave: () => void;
  onReset: () => void;
}

export function NotificationSettingsPanel({
  notificationDraft,
  onNotificationDraftChange,
  hasNotificationChanges,
  onSave,
  onReset,
}: NotificationSettingsPanelProps) {
  return (
    <Card>
      <CardContent>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Notification Settings
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          Notification edits stay in draft until you save them.
        </Alert>
        <Stack spacing={2}>
          <FieldTooltip title="Enable/disable email alerts. Disabling can delay awareness of critical risk events.">
            <FormControlLabel
              control={
                <Switch
                  checked={notificationDraft.emailAlerts}
                  onChange={(event) =>
                    onNotificationDraftChange((prev) => ({
                      ...prev,
                      emailAlerts: event.target.checked,
                    }))
                  }
                />
              }
              label="Email Alerts"
            />
          </FieldTooltip>
          <FieldTooltip title="Enable Telegram notifications. Useful for rapid alerts when away from dashboard.">
            <FormControlLabel
              control={
                <Switch
                  checked={notificationDraft.telegramAlerts}
                  onChange={(event) =>
                    onNotificationDraftChange((prev) => ({
                      ...prev,
                      telegramAlerts: event.target.checked,
                    }))
                  }
                />
              }
              label="Telegram Alerts"
            />
          </FieldTooltip>
          <FieldTooltip title="Profit/loss notification trigger. Too tight can create alert noise; too wide can hide drift.">
            <TextField
              label="Profit/Loss Threshold (%)"
              type="number"
              value={notificationDraft.profitLossThreshold}
              onChange={(event) =>
                onNotificationDraftChange((prev) => ({
                  ...prev,
                  profitLossThreshold: parseNumberInput(
                    event.target.value,
                    prev.profitLossThreshold
                  ),
                }))
              }
              helperText="Threshold for PnL alert generation."
            />
          </FieldTooltip>
          <FieldTooltip title="Drawdown alert trigger. Lower values warn earlier but may be more frequent.">
            <TextField
              label="Drawdown Threshold (%)"
              type="number"
              value={notificationDraft.drawdownThreshold}
              onChange={(event) =>
                onNotificationDraftChange((prev) => ({
                  ...prev,
                  drawdownThreshold: parseNumberInput(
                    event.target.value,
                    prev.drawdownThreshold
                  ),
                }))
              }
              helperText="Percent drawdown level that emits warning notifications."
            />
          </FieldTooltip>
          <FieldTooltip title="Risk alert trigger. Higher values can delay warning of concentrated exposure.">
            <TextField
              label="Risk Threshold (%)"
              type="number"
              value={notificationDraft.riskThreshold}
              onChange={(event) =>
                onNotificationDraftChange((prev) => ({
                  ...prev,
                  riskThreshold: parseNumberInput(event.target.value, prev.riskThreshold),
                }))
              }
              helperText="Percent utilization threshold for risk alerts."
            />
          </FieldTooltip>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <Button
              variant="contained"
              startIcon={<SaveOutlinedIcon />}
              onClick={onSave}
              disabled={!hasNotificationChanges}
            >
              Save Notifications
            </Button>
            <Button variant="outlined" onClick={onReset}>
              Revert Draft
            </Button>
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

interface DisplaySettingsPanelProps {
  displayDraft: DisplayDraft;
  onDisplayDraftChange: Dispatch<SetStateAction<DisplayDraft>>;
  timezoneOptions: string[];
  hasDisplayChanges: boolean;
  onSave: () => void;
  onReset: () => void;
}

export function DisplaySettingsPanel({
  displayDraft,
  onDisplayDraftChange,
  timezoneOptions,
  hasDisplayChanges,
  onSave,
  onReset,
}: DisplaySettingsPanelProps) {
  return (
    <Card>
      <CardContent>
        <Typography variant="h6" sx={{ mb: 2 }}>
          Display Preferences
        </Typography>
        <Alert severity="info" sx={{ mb: 2 }}>
          Display and environment changes apply only after you save them.
        </Alert>
        <Stack spacing={2}>
          <FieldTooltip title="Visual theme preference only. Does not change trading logic or risk behavior.">
            <FormControl fullWidth>
              <InputLabel id="theme-label">Theme</InputLabel>
              <Select
                labelId="theme-label"
                value={displayDraft.theme}
                label="Theme"
                onChange={(event) =>
                  onDisplayDraftChange((prev) => ({
                    ...prev,
                    theme: event.target.value,
                  }))
                }
              >
                <MenuItem value="light">Light</MenuItem>
                <MenuItem value="dark">Dark</MenuItem>
              </Select>
            </FormControl>
          </FieldTooltip>

          <FieldTooltip title="Formatting preference for displayed values. Does not convert account base currency.">
            <FormControl fullWidth>
              <InputLabel id="currency-label">Currency Display</InputLabel>
              <Select
                labelId="currency-label"
                value={displayDraft.currency}
                label="Currency Display"
                onChange={(event) =>
                  onDisplayDraftChange((prev) => ({
                    ...prev,
                    currency: event.target.value,
                  }))
                }
              >
                <MenuItem value="USD">USD (fiat view)</MenuItem>
                <MenuItem value="BTC">BTC (crypto unit view)</MenuItem>
              </Select>
            </FormControl>
          </FieldTooltip>

          <FieldTooltip title="Timezone used for date/time rendering. Wrong timezone can cause analysis mistakes.">
            <FormControl fullWidth>
              <InputLabel id="timezone-label">Timezone</InputLabel>
              <Select
                labelId="timezone-label"
                value={displayDraft.timezone}
                label="Timezone"
                onChange={(event) =>
                  onDisplayDraftChange((prev) => ({
                    ...prev,
                    timezone: event.target.value,
                  }))
                }
              >
                {timezoneOptions.map((option) => (
                  <MenuItem key={option} value={option}>
                    {option}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </FieldTooltip>

          <FieldTooltip title="UI text size multiplier. Larger scale improves accessibility but may reduce data density.">
            <TextField
              label="Text Scale"
              type="number"
              value={displayDraft.textScale}
              inputProps={{ min: 1, max: 2, step: 0.1 }}
              onChange={(event) =>
                onDisplayDraftChange((prev) => ({
                  ...prev,
                  textScale: parseNumberInput(event.target.value, prev.textScale),
                }))
              }
              helperText="Supports accessibility scaling up to 200%."
            />
          </FieldTooltip>

          <FieldTooltip title="Operational environment selector. Save before it changes dashboard, risk, and live-readiness surfaces. Research routes keep owning their own execution context.">
            <FormControl fullWidth>
              <InputLabel id="environment-label">Operational Environment</InputLabel>
              <Select
                labelId="environment-label"
                value={displayDraft.environmentMode}
                label="Operational Environment"
                onChange={(event) =>
                  onDisplayDraftChange((prev) => ({
                    ...prev,
                    environmentMode: event.target.value as EnvironmentMode,
                  }))
                }
              >
                <MenuItem value="test">Test / Paper Ops (recommended)</MenuItem>
                <MenuItem value="live">Live data view</MenuItem>
              </Select>
            </FormControl>
          </FieldTooltip>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <Button
              variant="contained"
              startIcon={<SaveOutlinedIcon />}
              onClick={onSave}
              disabled={!hasDisplayChanges}
            >
              Save Display Settings
            </Button>
            <Button variant="outlined" color="warning" onClick={onReset}>
              Reset Draft To Defaults
            </Button>
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  );
}

interface DatabaseSettingsPanelProps {
  systemInfo: SystemInfo | undefined;
  isSystemInfoError: boolean;
  isBackingUp: boolean;
  onTriggerBackup: () => void;
}

export function DatabaseSettingsPanel({
  systemInfo,
  isSystemInfoError,
  isBackingUp,
  onTriggerBackup,
}: DatabaseSettingsPanelProps) {
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, lg: 7 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 2 }}>
              System Information
            </Typography>
            {isSystemInfoError ? (
              <Alert severity="warning">
                Unable to load system information. Use the standard run scripts and health endpoint as a fallback.
              </Alert>
            ) : (
              <Stack spacing={1}>
                <Typography variant="body2">
                  Version: {systemInfo?.applicationVersion ?? 'local-dev'}
                </Typography>
                <Typography variant="body2">
                  Last Deployment: {systemInfo?.lastDeploymentDate ?? 'n/a'}
                </Typography>
                <Typography variant="body2">
                  Database Status: {systemInfo?.databaseStatus ?? 'unknown'}
                </Typography>
              </Stack>
            )}
          </CardContent>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, lg: 5 }}>
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 2 }}>
              Database Management
            </Typography>
            <Button variant="contained" onClick={onTriggerBackup} disabled={isBackingUp}>
              Trigger Backup
            </Button>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );
}

interface ExchangeStatusPanelProps {
  activeExchangeConnection: ExchangeConnectionProfile | null;
  exchangeBalance: ExchangeBalanceResponse | undefined;
  isExchangeBalanceError: boolean;
  exchangeBalanceError: unknown;
  onRefreshBalance: () => void;
  connectionStatus: ExchangeConnectionStatus | undefined;
  isConnectionError: boolean;
  connectionStatusError: unknown;
  isTestingConnection: boolean;
  onTestActiveConnection: () => void;
  exchangeOrders: ExchangeOrder[];
  isExchangeOrdersError: boolean;
  exchangeOrdersError: unknown;
  getExchangeLabel: (exchange: string) => string;
}

export function ExchangeStatusPanel({
  activeExchangeConnection,
  exchangeBalance,
  isExchangeBalanceError,
  exchangeBalanceError,
  onRefreshBalance,
  connectionStatus,
  isConnectionError,
  connectionStatusError,
  isTestingConnection,
  onTestActiveConnection,
  exchangeOrders,
  isExchangeOrdersError,
  exchangeOrdersError,
  getExchangeLabel,
}: ExchangeStatusPanelProps) {
  return (
    <Grid container spacing={2}>
      <Grid size={{ xs: 12, lg: 6 }}>
        <Card>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
              <Typography variant="h6">Exchange Balance</Typography>
              <Button
                variant="outlined"
                size="small"
                startIcon={<RefreshIcon />}
                onClick={onRefreshBalance}
              >
                Refresh
              </Button>
            </Stack>
            {isExchangeBalanceError ? (
              <Alert severity="warning">
                {getApiErrorMessage(
                  exchangeBalanceError,
                  'Unable to load live exchange balance data for the current environment.'
                )}
              </Alert>
            ) : (
              <Stack spacing={1}>
                <Typography variant="body2">
                  Active Profile: {activeExchangeConnection?.name ?? 'No active connection selected'}
                </Typography>
                <Typography variant="body2">
                  Exchange:{' '}
                  {exchangeBalance?.exchange ??
                    (activeExchangeConnection
                      ? getExchangeLabel(activeExchangeConnection.exchange)
                      : null) ??
                    connectionStatus?.exchange ??
                    'Live Exchange'}
                </Typography>
                <Typography variant="body2">Available: {exchangeBalance?.available ?? '-'}</Typography>
                <Typography variant="body2">Locked: {exchangeBalance?.locked ?? '-'}</Typography>
                <Typography variant="body2">Total: {exchangeBalance?.total ?? '-'}</Typography>
                <Typography variant="body2">Last Sync: {exchangeBalance?.lastSync ?? '-'}</Typography>
                {exchangeBalance?.assets?.slice(0, 6).map((asset) => (
                  <Typography key={asset.symbol} variant="caption">
                    {asset.symbol}: {asset.amount} ({asset.valueUSD} USD)
                  </Typography>
                ))}
              </Stack>
            )}
          </CardContent>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, lg: 6 }}>
        <Card sx={{ mb: 2 }}>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 2 }}>
              Connection Status
            </Typography>
            {activeExchangeConnection ? (
              <Stack spacing={1.5} sx={{ mb: 2 }}>
                <Chip
                  label={activeExchangeConnection.testnet ? 'Paper configuration' : 'Live configuration'}
                  color={activeExchangeConnection.testnet ? 'success' : 'error'}
                  variant="outlined"
                />
                <Typography variant="body2">Profile: {activeExchangeConnection.name}</Typography>
                <Typography variant="body2">
                  Exchange: {getExchangeLabel(activeExchangeConnection.exchange)}
                </Typography>
              </Stack>
            ) : (
              <Alert severity="info" sx={{ mb: 2 }}>
                No active connection is selected yet. Save and activate a connection in the API Config tab first.
              </Alert>
            )}
            {isConnectionError ? (
              <Alert severity="warning">
                {getApiErrorMessage(
                  connectionStatusError,
                  'Unable to load connection status. Use the test-connection action to verify credentials.'
                )}
              </Alert>
            ) : (
              <Stack spacing={1}>
                <Typography variant="body2">
                  Last Check Connected: {connectionStatus?.connected ? 'Yes' : 'No'}
                </Typography>
                <Typography variant="body2">Rate Limit: {connectionStatus?.rateLimitUsage ?? '-'}</Typography>
                <Typography variant="body2">Last Sync: {connectionStatus?.lastSync ?? '-'}</Typography>
                {connectionStatus?.error ? <Alert severity="error">{connectionStatus.error}</Alert> : null}
              </Stack>
            )}
            <Button
              variant="outlined"
              sx={{ mt: 2 }}
              onClick={onTestActiveConnection}
              disabled={isTestingConnection || !activeExchangeConnection}
            >
              Test Active Connection
            </Button>
          </CardContent>
        </Card>

        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 2 }}>
              Open Orders
            </Typography>
            <Stack spacing={0.5}>
              {isExchangeOrdersError ? (
                <Alert severity="warning">
                  {getApiErrorMessage(
                    exchangeOrdersError,
                    'Unable to load open orders for the live environment.'
                  )}
                </Alert>
              ) : exchangeOrders.length === 0 ? (
                <Typography variant="body2">No open orders reported.</Typography>
              ) : (
                exchangeOrders.slice(0, 10).map((order) => (
                  <Typography key={order.id} variant="caption">
                    #{order.id} {order.symbol} {order.side} @ {order.entryPrice} ({order.quantity}) [{order.status}]
                  </Typography>
                ))
              )}
            </Stack>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );
}
