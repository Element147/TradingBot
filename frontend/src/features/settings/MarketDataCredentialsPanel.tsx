import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Link,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';

import {
  useDeleteMarketDataProviderCredentialMutation,
  useGetMarketDataProviderCredentialsQuery,
  type MarketDataProviderCredentialSetting,
  useSaveMarketDataProviderCredentialMutation,
} from '@/features/marketData/marketDataApi';
import { getApiErrorMessage } from '@/services/api';

type CredentialDraft = {
  apiKey: string;
  note: string;
};

const sourceColor = (
  source: MarketDataProviderCredentialSetting['credentialSource']
): 'default' | 'success' | 'warning' | 'error' => {
  switch (source) {
    case 'DATABASE':
      return 'success';
    case 'ENVIRONMENT':
      return 'warning';
    case 'DATABASE_LOCKED':
      return 'error';
    default:
      return 'default';
  }
};

const sourceLabel = (source: MarketDataProviderCredentialSetting['credentialSource']) => {
  switch (source) {
    case 'DATABASE':
      return 'Stored in DB';
    case 'ENVIRONMENT':
      return 'Env fallback';
    case 'DATABASE_LOCKED':
      return 'DB locked';
    case 'NONE':
      return 'Missing';
    default:
      return 'Not required';
  }
};

export function MarketDataCredentialsPanel() {
  const { data: credentialSettings = [] } = useGetMarketDataProviderCredentialsQuery();
  const [saveCredential, { isLoading: isSaving }] = useSaveMarketDataProviderCredentialMutation();
  const [deleteCredential, { isLoading: isDeleting }] = useDeleteMarketDataProviderCredentialMutation();
  const [feedback, setFeedback] = useState<{ severity: 'success' | 'error'; message: string } | null>(null);
  const [drafts, setDrafts] = useState<Record<string, CredentialDraft>>({});

  const encryptionReady = useMemo(
    () => credentialSettings.every((setting) => setting.storageEncryptionConfigured),
    [credentialSettings]
  );

  const updateDraft = (providerId: string, field: keyof CredentialDraft, value: string) => {
    setDrafts((current) => ({
      ...current,
      [providerId]: {
        apiKey: current[providerId]?.apiKey ?? '',
        note: current[providerId]?.note ?? '',
        [field]: value,
      },
    }));
  };

  const handleSave = async (setting: MarketDataProviderCredentialSetting) => {
    const draft = drafts[setting.providerId] ?? { apiKey: '', note: setting.note ?? '' };
    try {
      await saveCredential({
        providerId: setting.providerId,
        apiKey: draft.apiKey.trim() || undefined,
        note: draft.note.trim() || undefined,
      }).unwrap();
      setDrafts((current) => ({
        ...current,
        [setting.providerId]: {
          apiKey: '',
          note: draft.note,
        },
      }));
      setFeedback({
        severity: 'success',
        message: `${setting.providerLabel} credential saved securely in the database.`,
      });
    } catch (error) {
      setFeedback({ severity: 'error', message: getApiErrorMessage(error) });
    }
  };

  const handleDelete = async (setting: MarketDataProviderCredentialSetting) => {
    try {
      await deleteCredential(setting.providerId).unwrap();
      setDrafts((current) => ({
        ...current,
        [setting.providerId]: {
          apiKey: '',
          note: '',
        },
      }));
      setFeedback({
        severity: 'success',
        message: `${setting.providerLabel} stored database credential removed.`,
      });
    } catch (error) {
      setFeedback({ severity: 'error', message: getApiErrorMessage(error) });
    }
  };

  return (
    <Card>
      <CardContent>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h6" sx={{ mb: 0.5 }}>
              Market Data Provider Credentials
            </Typography>
            <Typography variant="body2" color="text.secondary">
              Save provider API keys in PostgreSQL with backend-side encryption. Notes are stored with the setting so
              you can remember account purpose, limits, or rotation details.
            </Typography>
          </Box>

          {feedback ? (
            <Alert severity={feedback.severity} onClose={() => setFeedback(null)}>
              {feedback.message}
            </Alert>
          ) : null}

          {!encryptionReady ? (
            <Alert severity="warning">
              Set <code>ALGOTRADING_MARKET_DATA_CREDENTIALS_MASTER_KEY</code> on the backend before saving encrypted
              provider API keys from the UI.
            </Alert>
          ) : null}

          {credentialSettings.length === 0 ? (
            <Alert severity="info">No market-data providers with API keys are available.</Alert>
          ) : (
            credentialSettings.map((setting) => {
              const draft = {
                apiKey: drafts[setting.providerId]?.apiKey ?? '',
                note: drafts[setting.providerId]?.note ?? setting.note ?? '',
              };
              const hasChanges = draft.apiKey.trim().length > 0 || draft.note !== (setting.note ?? '');

              return (
                <Card key={setting.providerId} variant="outlined">
                  <CardContent>
                    <Stack spacing={1.5}>
                      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={1}>
                        <Box>
                          <Typography variant="subtitle1">{setting.providerLabel}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {setting.accountNotes}
                          </Typography>
                        </Box>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                          <Chip
                            size="small"
                            label={sourceLabel(setting.credentialSource)}
                            color={sourceColor(setting.credentialSource)}
                          />
                          {setting.hasStoredCredential ? <Chip size="small" label="DB secret saved" /> : null}
                          {setting.hasEnvironmentCredential ? (
                            <Chip size="small" variant="outlined" label="Env key present" />
                          ) : null}
                        </Stack>
                      </Stack>

                      <Typography variant="caption" color="text.secondary">
                        Environment fallback: {setting.apiKeyEnvironmentVariable ?? 'n/a'}
                        {setting.updatedAt ? ` | Last updated: ${new Date(setting.updatedAt).toLocaleString()}` : ''}
                      </Typography>

                      {setting.credentialSource === 'DATABASE_LOCKED' ? (
                        <Alert severity="error">
                          A stored database credential exists, but the backend cannot decrypt it until the master key is
                          configured again.
                        </Alert>
                      ) : null}

                      <TextField
                        label={`${setting.providerLabel} API Key`}
                        type="password"
                        value={draft.apiKey}
                        onChange={(event) => updateDraft(setting.providerId, 'apiKey', event.target.value)}
                        placeholder={
                          setting.hasStoredCredential
                            ? 'Enter a new key only when rotating the stored secret'
                            : 'Paste the provider API key to store it encrypted in the database'
                        }
                        helperText="Plaintext is only used for this save request. The backend stores only encrypted ciphertext."
                      />

                      <TextField
                        label="Note"
                        value={draft.note}
                        multiline
                        minRows={2}
                        onChange={(event) => updateDraft(setting.providerId, 'note', event.target.value)}
                        helperText="Optional note for account plan, intended symbols, free-tier limits, or rotation reminders."
                      />

                      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                        <Button
                          variant="contained"
                          startIcon={<SaveOutlinedIcon />}
                          onClick={() => void handleSave(setting)}
                          disabled={isSaving || (!hasChanges && !setting.hasStoredCredential)}
                        >
                          Save Provider Setting
                        </Button>
                        <Button
                          variant="outlined"
                          color="error"
                          startIcon={<DeleteOutlineIcon />}
                          onClick={() => void handleDelete(setting)}
                          disabled={isDeleting || !setting.hasStoredCredential}
                        >
                          Remove Stored Key
                        </Button>
                        <Button
                          component={Link}
                          href={setting.docsUrl}
                          target="_blank"
                          rel="noreferrer"
                          endIcon={<OpenInNewIcon />}
                          size="small"
                        >
                          Docs
                        </Button>
                        <Button
                          component={Link}
                          href={setting.signupUrl}
                          target="_blank"
                          rel="noreferrer"
                          endIcon={<OpenInNewIcon />}
                          size="small"
                        >
                          Account / Key
                        </Button>
                      </Stack>
                    </Stack>
                  </CardContent>
                </Card>
              );
            })
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}
