import type { MarketDataImportJob, MarketDataProvider } from './marketDataApi';

import { formatDateTime, formatDistanceToNow } from '@/utils/formatters';

export type MarketDataFormState = {
  providerId: string;
  assetType: 'STOCK' | 'CRYPTO';
  symbolsText: string;
  timeframe: string;
  startDate: string;
  endDate: string;
  datasetName: string;
  adjusted: boolean;
  regularSessionOnly: boolean;
};

export const defaultMarketDataForm: MarketDataFormState = {
  providerId: 'binance',
  assetType: 'CRYPTO',
  symbolsText: 'BTC/USDT\nETH/USDT\nSOL/USDT',
  timeframe: '1h',
  startDate: '2024-03-12',
  endDate: '2026-03-12',
  datasetName: '',
  adjusted: false,
  regularSessionOnly: false,
};

export const splitSymbols = (symbolsText: string): string[] =>
  symbolsText
    .split(/[\n,]+/)
    .map((symbol) => symbol.trim())
    .filter((symbol) => symbol.length > 0);

export const resolveAssetType = (
  provider: MarketDataProvider | null,
  assetType: MarketDataFormState['assetType']
): MarketDataFormState['assetType'] =>
  provider?.supportedAssetTypes.includes(assetType)
    ? assetType
    : (provider?.supportedAssetTypes[0] ?? assetType);

export const resolveTimeframe = (provider: MarketDataProvider | null, timeframe: string): string =>
  provider?.supportedTimeframes.includes(timeframe)
    ? timeframe
    : (provider?.supportedTimeframes[0] ?? timeframe);

export const statusColor = (
  status: MarketDataImportJob['status']
): 'default' | 'warning' | 'success' | 'error' => {
  switch (status) {
    case 'RUNNING':
    case 'WAITING_RETRY':
      return 'warning';
    case 'COMPLETED':
      return 'success';
    case 'FAILED':
      return 'error';
    default:
      return 'default';
  }
};

export const providerCredentialMessage = (
  configuredSource: MarketDataProvider['apiKeyConfiguredSource'],
  envVar: string | null
) => {
  switch (configuredSource) {
    case 'DATABASE':
      return 'API key configured from the encrypted database setting.';
    case 'ENVIRONMENT':
      return envVar
        ? `API key configured from ${envVar}.`
        : 'API key configured from the backend environment.';
    case 'DATABASE_LOCKED':
      return 'A stored database key exists, but the backend master key is missing so the provider cannot use it yet.';
    default:
      return envVar
        ? `Set ${envVar} in Settings or backend environment before creating jobs.`
        : 'Configure an API key before creating jobs.';
  }
};

export const formatOptionalDateTime = (value: string | null): string =>
  value ? formatDateTime(value) : 'Not available yet';

export const formatRelativeUpdate = (value: string | null): string =>
  value ? formatDistanceToNow(new Date(value)) : 'No updates yet';

export const formatLiveImportEventTimestamp = (value: string | null): string =>
  value ? formatDistanceToNow(new Date(value)) : 'No live import event received yet';
