import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import {
  Alert,
  Box,
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
  Tab,
  Tabs,
  TextField,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import { useEffect, useMemo, useState } from 'react';

import {
  useGetExchangeBalanceQuery,
  useGetExchangeConnectionStatusQuery,
  useGetExchangeOrdersQuery,
  useGetSavedExchangeConnectionsQuery,
  useGetSystemInfoQuery,
  useActivateSavedExchangeConnectionMutation,
  useCreateSavedExchangeConnectionMutation,
  useDeleteSavedExchangeConnectionMutation,
  useTestExchangeConnectionMutation,
  useTriggerBackupMutation,
  useUpdateSavedExchangeConnectionMutation,
  type ExchangeConnectionProfile,
  type ExchangeConnectionProfileRequest,
} from './exchangeApi';
import { MarketDataCredentialsPanel } from './MarketDataCredentialsPanel';
import { OperatorAuditPanel } from './OperatorAuditPanel';
import {
  DatabaseSettingsPanel,
  DisplaySettingsPanel,
  ExchangeStatusPanel,
  NotificationSettingsPanel,
} from './SettingsPanels';
import {
  selectCurrency,
  selectNotificationSettings,
  selectTextScale,
  selectTheme,
  selectTimezone,
  setCurrency,
  setNotificationSettings,
  setTextScale,
  setTheme,
  setTimezone,
  type NotificationSettings,
} from './settingsSlice';

import { useAppDispatch, useAppSelector } from '@/app/hooks';
import { AppLayout } from '@/components/layout/AppLayout';
import {
  PageContent,
  PageIntro,
  type PageMetricItem,
  PageMetricStrip,
} from '@/components/layout/PageContent';
import { FieldTooltip } from '@/components/ui/FieldTooltip';
import { StatusPill, SurfacePanel } from '@/components/ui/Workbench';
import {
  selectEnvironmentMode,
  setConnectedExchange,
  setEnvironmentMode,
  type EnvironmentMode,
} from '@/features/environment/environmentSlice';
import { getApiErrorMessage } from '@/services/api';
import { getErrorMessage } from '@/services/axiosClient';

type SettingsTab = 'api' | 'notifications' | 'display' | 'database' | 'exchange' | 'audit';

const SETTINGS_TAB_META: Record<
  SettingsTab,
  { label: string; description: string }
> = {
  api: {
    label: 'API Config',
    description: 'Save, test, activate, and rotate exchange connection drafts.',
  },
  notifications: {
    label: 'Notifications',
    description: 'Decide which alerts should surface and when they should fire.',
  },
  display: {
    label: 'Display',
    description: 'Control theme, timezone, text scale, and the environment switch.',
  },
  database: {
    label: 'Database',
    description: 'Review runtime status and trigger maintenance actions like backups.',
  },
  exchange: {
    label: 'Exchange',
    description: 'Inspect the active profile, live reads, and connection diagnostics.',
  },
  audit: {
    label: 'Audit Trail',
    description: 'Review operator-visible events and privileged changes.',
  },
};

type DisplayDraft = {
  theme: 'light' | 'dark';
  currency: 'USD' | 'BTC';
  timezone: string;
  textScale: number;
  environmentMode: EnvironmentMode;
};

const EXCHANGE_OPTIONS = [
  { value: 'binance', label: 'Binance' },
  { value: 'coinbase', label: 'Coinbase' },
  { value: 'kraken', label: 'Kraken' },
  { value: 'bybit', label: 'Bybit' },
  { value: 'okx', label: 'OKX' },
  { value: 'kucoin', label: 'KuCoin' },
] as const;

const NEW_CONNECTION_VALUE = '__new_connection__';
const EMPTY_EXCHANGE_CONNECTIONS: ExchangeConnectionProfile[] = [];

const createEmptyConnectionDraft = (preferredExchange = 'binance'): ExchangeConnectionProfile => ({
  id: '',
  name: '',
  exchange: preferredExchange,
  apiKey: '',
  apiSecret: '',
  testnet: true,
  active: false,
});

const getExchangeLabel = (exchange: string) =>
  EXCHANGE_OPTIONS.find((option) => option.value === exchange)?.label ??
  exchange.charAt(0).toUpperCase() + exchange.slice(1);

const areNotificationsEqual = (left: NotificationSettings, right: NotificationSettings) =>
  left.emailAlerts === right.emailAlerts &&
  left.telegramAlerts === right.telegramAlerts &&
  left.profitLossThreshold === right.profitLossThreshold &&
  left.drawdownThreshold === right.drawdownThreshold &&
  left.riskThreshold === right.riskThreshold;

const areConnectionsEqual = (left: ExchangeConnectionProfile, right: ExchangeConnectionProfile) =>
  left.name === right.name &&
  left.exchange === right.exchange &&
  left.apiKey === right.apiKey &&
  left.apiSecret === right.apiSecret &&
  left.testnet === right.testnet;

const createConnectionName = (exchange: string, existingConnections: ExchangeConnectionProfile[]) => {
  const baseLabel = getExchangeLabel(exchange);
  const sameExchangeCount = existingConnections.filter((connection) => connection.exchange === exchange).length;
  return `${baseLabel} Connection ${sameExchangeCount + 1}`;
};

export default function SettingsPage() {
  const muiTheme = useTheme();
  const useVerticalTabs = useMediaQuery(muiTheme.breakpoints.up('lg'));
  const dispatch = useAppDispatch();
  const theme = useAppSelector(selectTheme);
  const currency = useAppSelector(selectCurrency);
  const timezone = useAppSelector(selectTimezone);
  const textScale = useAppSelector(selectTextScale);
  const notifications = useAppSelector(selectNotificationSettings);
  const environmentMode = useAppSelector(selectEnvironmentMode);
  const userRole = useAppSelector((state) => state.auth.user?.role ?? 'trader');
  const isAdmin = userRole === 'admin';

  const [feedback, setFeedback] = useState<{ severity: 'success' | 'error' | 'warning'; message: string } | null>(
    null
  );
  const [tab, setTab] = useState<SettingsTab>(isAdmin ? 'api' : 'display');
  const [selectedConnectionId, setSelectedConnectionId] = useState('');
  const [revealApiKey, setRevealApiKey] = useState(false);
  const [revealSecret, setRevealSecret] = useState(false);
  const [displayDraft, setDisplayDraft] = useState<DisplayDraft>({
    theme,
    currency,
    timezone,
    textScale,
    environmentMode,
  });
  const [notificationDraft, setNotificationDraft] = useState<NotificationSettings>(notifications);
  const [connectionDraft, setConnectionDraft] = useState<ExchangeConnectionProfile>(() =>
    createEmptyConnectionDraft()
  );

  const { data: systemInfo, isError: isSystemInfoError } = useGetSystemInfoQuery();
  const {
    data: exchangeBalance,
    refetch: refetchBalance,
    isError: isExchangeBalanceError,
    error: exchangeBalanceError,
  } = useGetExchangeBalanceQuery(undefined, {
    pollingInterval: environmentMode === 'live' ? 60000 : 0,
    skipPollingIfUnfocused: true,
  });
  const {
    data: exchangeOrders = [],
    isError: isExchangeOrdersError,
    error: exchangeOrdersError,
  } = useGetExchangeOrdersQuery(undefined, {
    pollingInterval: environmentMode === 'live' ? 60000 : 0,
    skipPollingIfUnfocused: true,
  });
  const {
    data: connectionStatus,
    isError: isConnectionError,
    error: connectionStatusError,
  } = useGetExchangeConnectionStatusQuery();
  const {
    data: savedConnectionsData,
    isError: isSavedConnectionsError,
    error: savedConnectionsError,
    refetch: refetchSavedConnections,
  } = useGetSavedExchangeConnectionsQuery();
  const [createSavedConnection, { isLoading: isCreatingConnection }] =
    useCreateSavedExchangeConnectionMutation();
  const [updateSavedConnection, { isLoading: isUpdatingConnection }] =
    useUpdateSavedExchangeConnectionMutation();
  const [activateSavedConnection, { isLoading: isActivatingConnection }] =
    useActivateSavedExchangeConnectionMutation();
  const [deleteSavedConnection, { isLoading: isDeletingConnection }] =
    useDeleteSavedExchangeConnectionMutation();
  const [testConnection, { isLoading: isTestingConnection }] = useTestExchangeConnectionMutation();
  const [triggerBackup, { isLoading: isBackingUp }] = useTriggerBackupMutation();

  const exchangeConnections = savedConnectionsData?.connections ?? EMPTY_EXCHANGE_CONNECTIONS;
  const activeExchangeConnectionId = savedConnectionsData?.activeConnectionId ?? null;
  const activeExchangeConnection =
    exchangeConnections.find((connection) => connection.id === activeExchangeConnectionId) ?? null;

  const timezoneOptions = useMemo(() => {
    const detected = Intl.DateTimeFormat().resolvedOptions().timeZone;
    const options = ['UTC', detected, 'Europe/Prague', 'America/New_York', 'Asia/Tokyo'];
    return Array.from(new Set(options));
  }, []);

  const selectedSavedConnection =
    exchangeConnections.find((connection) => connection.id === selectedConnectionId) ?? null;

  const emptyConnectionDraft = useMemo(
    () => createEmptyConnectionDraft(activeExchangeConnection?.exchange ?? 'binance'),
    [activeExchangeConnection?.exchange]
  );
  const isSavingConnection = isCreatingConnection || isUpdatingConnection;

  useEffect(() => {
    setDisplayDraft({
      theme,
      currency,
      timezone,
      textScale,
      environmentMode,
    });
  }, [theme, currency, timezone, textScale, environmentMode]);

  useEffect(() => {
    setNotificationDraft(notifications);
  }, [notifications]);

  useEffect(() => {
    dispatch(setConnectedExchange(activeExchangeConnection?.exchange ?? null));
  }, [activeExchangeConnection?.exchange, dispatch]);

  useEffect(() => {
    const isCreatingNewConnection = selectedConnectionId === NEW_CONNECTION_VALUE;
    const nextSelectedConnectionId =
      isCreatingNewConnection ||
      (selectedConnectionId &&
        exchangeConnections.some((connection) => connection.id === selectedConnectionId))
        ? selectedConnectionId
        : activeExchangeConnectionId ?? exchangeConnections[0]?.id ?? '';

    if (nextSelectedConnectionId !== selectedConnectionId) {
      setSelectedConnectionId(nextSelectedConnectionId);
      return;
    }

    const nextConnectionDraft = isCreatingNewConnection
      ? emptyConnectionDraft
      : exchangeConnections.find((connection) => connection.id === nextSelectedConnectionId) ??
        emptyConnectionDraft;

    setConnectionDraft((current) => {
      const draftChanged =
        current.id !== nextConnectionDraft.id ||
        current.active !== nextConnectionDraft.active ||
        current.updatedAt !== nextConnectionDraft.updatedAt ||
        !areConnectionsEqual(current, nextConnectionDraft);

      return draftChanged ? { ...nextConnectionDraft } : current;
    });
    setRevealApiKey(false);
    setRevealSecret(false);
  }, [
    activeExchangeConnectionId,
    emptyConnectionDraft,
    exchangeConnections,
    selectedConnectionId,
  ]);

  const hasDisplayChanges =
    displayDraft.theme !== theme ||
    displayDraft.currency !== currency ||
    displayDraft.timezone !== timezone ||
    displayDraft.textScale !== textScale ||
    displayDraft.environmentMode !== environmentMode;

  const hasNotificationChanges = !areNotificationsEqual(notificationDraft, notifications);

  const hasConnectionChanges = selectedSavedConnection
    ? !areConnectionsEqual(selectedSavedConnection, connectionDraft)
    : !areConnectionsEqual(emptyConnectionDraft, connectionDraft);

  const canSaveConnection =
    selectedSavedConnection !== null ||
    connectionDraft.name.trim().length > 0 ||
    connectionDraft.apiKey.trim().length > 0 ||
    connectionDraft.apiSecret.trim().length > 0 ||
    connectionDraft.exchange !== emptyConnectionDraft.exchange ||
    connectionDraft.testnet !== emptyConnectionDraft.testnet;

  const canDeleteSelectedConnection = Boolean(selectedSavedConnection);

  const saveDisplaySettings = () => {
    dispatch(setTheme(displayDraft.theme));
    dispatch(setCurrency(displayDraft.currency));
    dispatch(setTimezone(displayDraft.timezone));
    dispatch(setTextScale(displayDraft.textScale));
    dispatch(setEnvironmentMode(displayDraft.environmentMode));
    setFeedback({ severity: 'success', message: 'Display settings saved and applied.' });
  };

  const saveNotificationSettings = () => {
    dispatch(setNotificationSettings(notificationDraft));
    setFeedback({ severity: 'success', message: 'Notification settings saved.' });
  };

  const saveConnectionProfile = async () => {
    const normalizedExchange = connectionDraft.exchange.trim().toLowerCase() || 'binance';
    const request: ExchangeConnectionProfileRequest = {
      name:
        connectionDraft.name.trim() ||
        selectedSavedConnection?.name ||
        createConnectionName(normalizedExchange, exchangeConnections),
      exchange: normalizedExchange,
      apiKey: connectionDraft.apiKey.trim(),
      apiSecret: connectionDraft.apiSecret.trim(),
      testnet: connectionDraft.testnet,
    };

    try {
      const savedProfile = selectedSavedConnection
        ? await updateSavedConnection({
            id: selectedSavedConnection.id,
            body: request,
          }).unwrap()
        : await createSavedConnection(request).unwrap();

      await refetchSavedConnections();
      setSelectedConnectionId(savedProfile.id);
      setConnectionDraft(savedProfile);
      setRevealApiKey(false);
      setRevealSecret(false);
      setFeedback({
        severity: 'success',
        message: `${savedProfile.name} saved to the database. Use "Set Active" to switch the bot to this connection profile.`,
      });
    } catch (error) {
      setFeedback({
        severity: 'error',
        message: getErrorMessage(error),
      });
    }
  };

  const activateSelectedConnection = async () => {
    if (!selectedSavedConnection) {
      setFeedback({
        severity: 'warning',
        message: 'Save this connection profile before making it active.',
      });
      return;
    }

    try {
      const activeProfile = await activateSavedConnection(selectedSavedConnection.id).unwrap();
      await refetchSavedConnections();
      setSelectedConnectionId(activeProfile.id);
      setFeedback({
        severity: 'success',
        message: `${activeProfile.name} is now the active bot connection.`,
      });
    } catch (error) {
      setFeedback({
        severity: 'error',
        message: getErrorMessage(error),
      });
    }
  };

  const deleteSelectedConnection = async () => {
    if (!selectedSavedConnection) {
      setConnectionDraft(emptyConnectionDraft);
      setRevealApiKey(false);
      setRevealSecret(false);
      return;
    }

    try {
      const deletedName = selectedSavedConnection.name;
      await deleteSavedConnection(selectedSavedConnection.id).unwrap();
      await refetchSavedConnections();
      setSelectedConnectionId(NEW_CONNECTION_VALUE);
      setConnectionDraft(emptyConnectionDraft);
      setRevealApiKey(false);
      setRevealSecret(false);
      setFeedback({
        severity: 'warning',
        message: `${deletedName} was removed from saved connections.`,
      });
    } catch (error) {
      setFeedback({
        severity: 'error',
        message: getErrorMessage(error),
      });
    }
  };

  const runConnectionTest = async (profile: ExchangeConnectionProfile) => {
    try {
      const result = await testConnection({
        exchange: profile.exchange,
        apiKey: profile.apiKey,
        apiSecret: profile.apiSecret,
        testnet: profile.testnet,
      }).unwrap();
      setFeedback({
        severity: result.connected ? 'success' : 'warning',
        message: result.connected
          ? `Connection successful (${result.rateLimitUsage}).`
          : `Connection failed: ${result.error ?? result.rateLimitUsage}.`,
      });
    } catch (error) {
      setFeedback({
        severity: 'error',
        message: getErrorMessage(error),
      });
    }
  };

  const runBackup = async () => {
    try {
      const result = await triggerBackup().unwrap();
      setFeedback({ severity: 'success', message: `Backup triggered: ${result.path} (${result.size}).` });
    } catch (error) {
      setFeedback({
        severity: 'error',
        message: getErrorMessage(error),
      });
    }
  };

  const activeTab: SettingsTab =
    !isAdmin && (tab === 'api' || tab === 'database' || tab === 'audit') ? 'display' : tab;
  const visibleTabs = ([
    isAdmin ? 'api' : null,
    'notifications',
    'display',
    isAdmin ? 'database' : null,
    'exchange',
    isAdmin ? 'audit' : null,
  ].filter(Boolean) as SettingsTab[]);
  const activeTabStatus = (() => {
    switch (activeTab) {
      case 'api':
        return {
          label: hasConnectionChanges ? 'Draft not saved' : 'Saved',
          color: hasConnectionChanges ? 'warning' : 'success',
        } as const;
      case 'notifications':
        return {
          label: hasNotificationChanges ? 'Draft not saved' : 'Saved',
          color: hasNotificationChanges ? 'warning' : 'success',
        } as const;
      case 'display':
        return {
          label: hasDisplayChanges ? 'Draft not saved' : 'Saved',
          color: hasDisplayChanges ? 'warning' : 'success',
        } as const;
      case 'database':
        return {
          label: isBackingUp ? 'Backup running' : 'Ready',
          color: isBackingUp ? 'warning' : 'success',
        } as const;
      case 'exchange':
        return {
          label: activeExchangeConnection ? 'Active profile set' : 'Profile needed',
          color: activeExchangeConnection ? 'success' : 'warning',
        } as const;
      case 'audit':
        return {
          label: 'Read-only review',
          color: 'info',
        } as const;
      default:
        return {
          label: 'Ready',
          color: 'success',
        } as const;
    }
  })();
  const summaryItems: PageMetricItem[] = [
    {
      label: 'Environment',
      value: environmentMode.toUpperCase(),
      detail:
        environmentMode === 'live'
          ? 'Live reads stay explicit and unsupported actions should fail closed.'
          : 'Test remains the safe default for day-to-day work.',
      tone: environmentMode === 'live' ? 'error' : 'success',
    },
    {
      label: 'Role',
      value: userRole.toUpperCase(),
      detail: isAdmin
        ? 'Admin-only sections remain visible in this view.'
        : 'Advanced admin sections stay hidden until the role allows them.',
      tone: isAdmin ? 'info' : 'default',
    },
    {
      label: 'Active Connection',
      value: activeExchangeConnection?.name ?? 'None selected',
      detail: activeExchangeConnection
        ? `${getExchangeLabel(activeExchangeConnection.exchange)} ${
            activeExchangeConnection.testnet ? 'testnet' : 'mainnet'
          } profile`
        : 'Save and activate a connection profile before expecting exchange reads.',
      tone: activeExchangeConnection
        ? activeExchangeConnection.testnet
          ? 'success'
          : 'warning'
        : 'warning',
    },
    {
      label: 'Display Surface',
      value: `${theme} / ${Math.round(textScale * 100)}%`,
      detail: `${currency} formatting in ${timezone}`,
      tone: 'default',
    },
  ];

  return (
    <AppLayout>
      <PageContent maxWidth="research">
        <PageIntro
          eyebrow="Reference surface"
          description="Keep credentials, display preferences, operational environment controls, and operator-facing tooling in one calmer layout where drafts, saved state, and advanced actions are easier to tell apart."
          chips={
            <>
              <Chip label="Draft changes save explicitly" variant="outlined" />
              <Chip label="Operational environment stays visible" variant="outlined" />
              <Chip label="Advanced tools remain available" variant="outlined" />
            </>
          }
        />

        <PageMetricStrip items={summaryItems} />

        {feedback ? (
          <Alert severity={feedback.severity} onClose={() => setFeedback(null)}>
            {feedback.message}
          </Alert>
        ) : null}

        <Grid container spacing={2.5}>
          <Grid size={{ xs: 12, lg: 3 }}>
            <SurfacePanel
              title="Settings sections"
              description="Pick one area, make the draft changes you need, then save that section before moving on."
              elevated
              sx={{ position: { lg: 'sticky' }, top: { lg: 16 } }}
            >
              <Tabs
                value={activeTab}
                onChange={(_, value: SettingsTab) => setTab(value)}
                variant="scrollable"
                allowScrollButtonsMobile
                orientation={useVerticalTabs ? 'vertical' : 'horizontal'}
                sx={{
                  minHeight: 0,
                  '& .MuiTabs-flexContainer': {
                    gap: useVerticalTabs ? 4 : 8,
                  },
                }}
              >
                {visibleTabs.map((tabValue) => (
                  <Tab
                    key={tabValue}
                    value={tabValue}
                    label={
                      <Box sx={{ textAlign: 'left' }}>
                        <Typography variant="subtitle2">
                          {SETTINGS_TAB_META[tabValue].label}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {SETTINGS_TAB_META[tabValue].description}
                        </Typography>
                      </Box>
                    }
                  />
                ))}
              </Tabs>
            </SurfacePanel>
          </Grid>

          <Grid size={{ xs: 12, lg: 9 }}>
            <Stack spacing={2.5}>
              <SurfacePanel
                title={SETTINGS_TAB_META[activeTab].label}
                description={SETTINGS_TAB_META[activeTab].description}
                actions={
                  <StatusPill
                    label={activeTabStatus.label}
                    tone={activeTabStatus.color}
                    variant="filled"
                  />
                }
              >
                <Typography variant="body2" color="text.secondary">
                  Draft changes remain local to this section until you save or activate them.
                </Typography>
              </SurfacePanel>

              {activeTab === 'api' ? (
                <Grid container spacing={2.5}>
            <Grid size={{ xs: 12, lg: 8 }}>
              <Card>
                <CardContent>
                  <Stack spacing={2.5}>
                    <Box>
                      <Typography variant="h6" sx={{ mb: 0.5 }}>
                        API Configuration
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Connection edits stay in draft until you save them. Saved profiles live in the database and the
                        bot uses the currently active saved connection.
                      </Typography>
                    </Box>

                    {isSavedConnectionsError ? (
                      <Alert severity="warning">
                        {getApiErrorMessage(
                          savedConnectionsError,
                          'Unable to load saved exchange connections from the database.'
                        )}
                      </Alert>
                    ) : null}

                    <FieldTooltip title="Choose a saved connection profile or start a new one, similar to selecting a saved database connection.">
                      <FormControl fullWidth>
                        <InputLabel id="saved-connection-label">Saved Connection</InputLabel>
                        <Select
                          labelId="saved-connection-label"
                          value={selectedConnectionId || NEW_CONNECTION_VALUE}
                          label="Saved Connection"
                          onChange={(event) => {
                            const nextValue = event.target.value;
                            setSelectedConnectionId(nextValue);
                          }}
                        >
                          {exchangeConnections.map((connection) => (
                            <MenuItem key={connection.id} value={connection.id}>
                              {connection.name}
                              {connection.id === activeExchangeConnectionId ? ' (Active)' : ''}
                            </MenuItem>
                          ))}
                          <MenuItem value={NEW_CONNECTION_VALUE}>New connection...</MenuItem>
                        </Select>
                      </FormControl>
                    </FieldTooltip>

                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                      <Button
                        variant="outlined"
                        startIcon={<AddCircleOutlineIcon />}
                        onClick={() => {
                          setSelectedConnectionId(NEW_CONNECTION_VALUE);
                          setConnectionDraft(emptyConnectionDraft);
                          setRevealApiKey(false);
                          setRevealSecret(false);
                        }}
                      >
                        New Connection
                      </Button>
                      <Button
                        variant="outlined"
                        color="error"
                        startIcon={<DeleteOutlineIcon />}
                        onClick={() => void deleteSelectedConnection()}
                        disabled={!canDeleteSelectedConnection || isDeletingConnection}
                      >
                        Delete
                      </Button>
                      <Button
                        variant="contained"
                        color="secondary"
                        onClick={() => void activateSelectedConnection()}
                        disabled={
                          !selectedSavedConnection ||
                          selectedConnectionId === activeExchangeConnectionId ||
                          isActivatingConnection
                        }
                      >
                        Set Active
                      </Button>
                    </Stack>

                    <FieldTooltip title="Friendly name shown in the connection dropdown and active-profile summary.">
                      <TextField
                        label="Connection Name"
                        value={connectionDraft.name}
                        onChange={(event) =>
                          setConnectionDraft((prev) => ({ ...prev, name: event.target.value }))
                        }
                        helperText="Saved in the database for your account, similar to a named SQL Developer connection."
                      />
                    </FieldTooltip>

                    <FieldTooltip title="Select which exchange this saved connection profile belongs to. Only Binance connectivity testing is wired in the backend right now.">
                      <FormControl fullWidth>
                        <InputLabel id="exchange-label">Exchange</InputLabel>
                        <Select
                          labelId="exchange-label"
                          value={connectionDraft.exchange}
                          label="Exchange"
                          onChange={(event) =>
                            setConnectionDraft((prev) => ({
                              ...prev,
                              exchange: event.target.value,
                            }))
                          }
                        >
                          {EXCHANGE_OPTIONS.map((option) => (
                            <MenuItem key={option.value} value={option.value}>
                              {option.label}
                            </MenuItem>
                          ))}
                        </Select>
                      </FormControl>
                    </FieldTooltip>

                    <FieldTooltip title="Exchange credential identifier. Revealing keys on shared screens is a security risk.">
                      <TextField
                        label="Exchange API Key"
                        type={revealApiKey ? 'text' : 'password'}
                        value={connectionDraft.apiKey}
                        onChange={(event) =>
                          setConnectionDraft((prev) => ({ ...prev, apiKey: event.target.value }))
                        }
                        helperText="Stored in the database for this account until you edit or remove the connection."
                      />
                    </FieldTooltip>
                    <Button variant="outlined" onClick={() => setRevealApiKey((prev) => !prev)}>
                      {revealApiKey ? 'Hide API Key' : 'Reveal API Key'}
                    </Button>

                    <FieldTooltip title="Secret used for signed exchange requests. Exposure can compromise account safety.">
                      <TextField
                        label="Exchange API Secret"
                        type={revealSecret ? 'text' : 'password'}
                        value={connectionDraft.apiSecret}
                        onChange={(event) =>
                          setConnectionDraft((prev) => ({ ...prev, apiSecret: event.target.value }))
                        }
                        helperText="Stored in the database for this account. Save changes before activating the profile."
                      />
                    </FieldTooltip>
                    <Button variant="outlined" onClick={() => setRevealSecret((prev) => !prev)}>
                      {revealSecret ? 'Hide Secret' : 'Reveal Secret'}
                    </Button>

                    <FieldTooltip title="Use testnet for paper-safe credentials. Turn this off only when you intentionally want a live/mainnet connection profile.">
                      <FormControlLabel
                        control={
                          <Switch
                            checked={connectionDraft.testnet}
                            onChange={(event) =>
                              setConnectionDraft((prev) => ({
                                ...prev,
                                testnet: event.target.checked,
                              }))
                            }
                          />
                        }
                        label={connectionDraft.testnet ? 'Use testnet / paper credentials' : 'Use mainnet / live credentials'}
                      />
                    </FieldTooltip>

                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                      <Button
                        variant="contained"
                        startIcon={<SaveOutlinedIcon />}
                        onClick={() => void saveConnectionProfile()}
                        disabled={!canSaveConnection || !hasConnectionChanges || isSavingConnection}
                      >
                        Save Connection
                      </Button>
                      <Button
                        variant="outlined"
                        onClick={() => void runConnectionTest(connectionDraft)}
                        disabled={isTestingConnection}
                      >
                        Test This Connection
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid size={{ xs: 12, lg: 4 }}>
              <Stack spacing={2.5}>
                <Card>
                  <CardContent>
                    <Typography variant="h6" sx={{ mb: 2 }}>
                      Connection Library
                    </Typography>
                    <Stack spacing={1.5}>
                      <Box>
                        <Typography variant="caption" color="text.secondary">
                          Active profile
                        </Typography>
                        <Typography variant="body1">
                          {activeExchangeConnection?.name ?? 'No active connection selected'}
                        </Typography>
                      </Box>
                      <Chip
                        label={
                          activeExchangeConnection
                            ? activeExchangeConnection.testnet
                              ? 'Paper configuration'
                              : 'Live configuration'
                            : 'No active configuration'
                        }
                        color={
                          activeExchangeConnection
                            ? activeExchangeConnection.testnet
                              ? 'success'
                              : 'error'
                            : 'default'
                        }
                        variant="outlined"
                      />
                      <Typography variant="body2">
                        Exchange:{' '}
                        {activeExchangeConnection
                          ? getExchangeLabel(activeExchangeConnection.exchange)
                          : '-'}
                      </Typography>
                      <Typography variant="body2">
                        Scope:{' '}
                        {activeExchangeConnection
                          ? activeExchangeConnection.testnet
                            ? 'Testnet / paper-safe'
                            : 'Mainnet / live'
                          : '-'}
                      </Typography>
                      <Typography variant="body2">
                        Saved profiles: {exchangeConnections.length}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Saved profiles are loaded from the database on startup. The backend currently supports live
                        connectivity testing for Binance only. Other exchanges can still be saved now and wired later.
                      </Typography>
                    </Stack>
                  </CardContent>
                </Card>

                <MarketDataCredentialsPanel />
              </Stack>
            </Grid>
          </Grid>
              ) : null}

              {activeTab === 'notifications' ? (
                <NotificationSettingsPanel
                  notificationDraft={notificationDraft}
                  onNotificationDraftChange={setNotificationDraft}
                  hasNotificationChanges={hasNotificationChanges}
                  onSave={saveNotificationSettings}
                  onReset={() => setNotificationDraft(notifications)}
                />
              ) : null}

              {activeTab === 'display' ? (
                <DisplaySettingsPanel
                  displayDraft={displayDraft}
                  onDisplayDraftChange={setDisplayDraft}
                  timezoneOptions={timezoneOptions}
                  hasDisplayChanges={hasDisplayChanges}
                  onSave={saveDisplaySettings}
                  onReset={() =>
                    setDisplayDraft({
                      theme: 'light',
                      currency: 'USD',
                      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                      textScale: 1,
                      environmentMode: 'test',
                    })
                  }
                />
              ) : null}

              {activeTab === 'database' ? (
                <DatabaseSettingsPanel
                  systemInfo={systemInfo}
                  isSystemInfoError={isSystemInfoError}
                  isBackingUp={isBackingUp}
                  onTriggerBackup={() => void runBackup()}
                />
              ) : null}

              {activeTab === 'exchange' ? (
                <ExchangeStatusPanel
                  activeExchangeConnection={activeExchangeConnection}
                  exchangeBalance={exchangeBalance}
                  isExchangeBalanceError={isExchangeBalanceError}
                  exchangeBalanceError={exchangeBalanceError}
                  onRefreshBalance={() => void refetchBalance()}
                  connectionStatus={connectionStatus}
                  isConnectionError={isConnectionError}
                  connectionStatusError={connectionStatusError}
                  isTestingConnection={isTestingConnection}
                  onTestActiveConnection={() => {
                    if (activeExchangeConnection) {
                      void runConnectionTest(activeExchangeConnection);
                    }
                  }}
                  exchangeOrders={exchangeOrders}
                  isExchangeOrdersError={isExchangeOrdersError}
                  exchangeOrdersError={exchangeOrdersError}
                  getExchangeLabel={getExchangeLabel}
                />
              ) : null}

              {activeTab === 'audit' ? <OperatorAuditPanel /> : null}
            </Stack>
          </Grid>
        </Grid>
      </PageContent>
    </AppLayout>
  );
}
