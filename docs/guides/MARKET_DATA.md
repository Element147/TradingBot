# Market Data Guide

Use this guide for provider coverage, dataset imports, credentials, and the dataset workflow.

## Provider Coverage

Public providers without API keys:

- `Binance`
- `Kraken` for recent crypto history only. Its public OHLC endpoint exposes a rolling window of the latest `720` candles per timeframe.

Free-tier providers with API keys:

- `Twelve Data`
- `Finnhub`
- `Alpha Vantage`

Supported import timeframes:

- `1m`
- `5m`
- `15m`
- `30m`
- `1h`
- `4h`
- `1d`

Kraken lookback examples:

- `1m`: about the last 12 hours
- `5m`: about the last 60 hours
- `15m`: about the last 7.5 days
- `30m`: about the last 15 days
- `1h`: about the last 30 days
- `4h`: about the last 120 days
- `1d`: about the last 720 days

## Workflow

1. Configure provider credentials in `Settings` or via environment variables.
2. Create an import job from `Market Data`.
3. Monitor the job until it completes, retries, or fails.
4. Use the resulting dataset from the backtest catalog.

If an operator needs deeper Kraken history than that rolling window allows, use `Binance` where coverage fits or upload a CSV dataset instead. The backend now rejects impossible Kraken ranges up front instead of running a long retry cycle.

## Dataset Model

New uploads and completed provider imports hydrate the normalized market-data store during ingestion.

Runtime ownership includes:

- `market_data_series`
- `market_data_candle_segments`
- `market_data_candles`

Legacy CSV blobs remain compatibility data where required for fallback or download behavior.

## Service Ownership

- `MarketDataImportService`: provider metadata, credentials, job commands
- `MarketDataImportExecutionService`: async provider fetch and job execution
- `MarketDataImportProgressService`: import telemetry publication
- `BacktestDatasetStorageService`: parsing, persistence, downloads
- `BacktestDatasetLifecycleService`: inventory, retention, archive, restore

## Import Job States

- `QUEUED`
- `RUNNING`
- `WAITING_RETRY`
- `COMPLETED`
- `FAILED`
- `CANCELLED`

Provider-enforced wait windows move jobs to `WAITING_RETRY`; the scheduler resumes them automatically.

## Credentials

- UI-saved provider secrets are encrypted in PostgreSQL
- Environment-variable fallback is supported where applicable
- Operators can attach notes to stored provider credentials

Relevant environment variables:

- `ALGOTRADING_MARKET_DATA_CREDENTIALS_MASTER_KEY`
- `ALGOTRADING_MARKET_DATA_TWELVEDATA_API_KEY`
- `ALGOTRADING_MARKET_DATA_FINNHUB_API_KEY`
- `ALGOTRADING_MARKET_DATA_ALPHAVANTAGE_API_KEY`

## Rules

1. Normalize provider data into the same dataset catalog used by uploads.
2. Keep provider-specific retry and wait behavior visible in job state.
3. Add providers only when they solve a real coverage gap.
4. Keep research guardrails intact: the presence of more data does not weaken the safety posture.
