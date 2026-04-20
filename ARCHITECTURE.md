# Architecture

## System Overview

The repository has two main applications:

- `AlgotradingBot/`: Spring Boot backend on Java 25
- `frontend/`: React 19, TypeScript, and Vite SPA

The system is designed for local-first research, reproducible backtests, paper-trading workflows, and operator visibility. It is not designed as a low-latency execution stack.

## Backend Boundaries

Primary backend areas in `com.algotrader.bot`:

- `controller`: HTTP entrypoints and DTO contracts
- `service`: orchestration and business logic
- `service.marketdata`: provider, import, and dataset workflow logic
- `repository`: persistence access
- `entity`: runtime database models
- `backtest`: execution engine, validation, and analytics plumbing
- `backtest.strategy`: strategy registry and implementations
- `risk`, `security`, `config`, `websocket`, `validation`, `repair`: cross-cutting runtime support

Important ownership splits:

- `BacktestManagementService`: queue, replay, delete, and command validation
- `BacktestResultQueryService`: history, summary, compare, and detail reads
- `BacktestExecutionService`: async execution, dataset loading, scoping, and timeframe handling
- `BacktestExecutionLifecycleService`: transactional state changes and result persistence
- `BacktestProgressService`: progress publication
- `MarketDataImportService`: provider metadata, credentials, and job commands
- `MarketDataImportExecutionService`: async import work
- `MarketDataImportProgressService`: import telemetry publication
- `BacktestDatasetStorageService`: parsing, storage, and downloads
- `BacktestDatasetLifecycleService`: inventory, retention, archive, and restore

## Frontend Boundaries

The frontend is a BrowserRouter SPA with feature-based structure under `frontend/src/features/`.

Shared layers:

- `src/app`: Redux store and typed hooks
- `src/services`: API helpers, OpenAPI transport helpers, WebSocket manager
- `src/components`: layout shell, route guards, shared UI, loading, and error states
- `src/theme`: design tokens and MUI overrides

Main routes:

- `/dashboard`
- `/backtest`
- `/forward-testing`
- `/paper`
- `/live`
- `/strategies`
- `/market-data`
- `/trades`
- `/risk`
- `/settings`

The shell owns the main route context. Feature routes are expected to plug into that shell instead of rebuilding their own page chrome.

## Major Data Flows

### Authentication And Transport

- JWT auth protects `/api/**`, with only login and refresh public
- Token revocation is durable in PostgreSQL
- WebSocket upgrades use authenticated `/ws?token=...&env=...` handshakes
- Frontend uses WebSockets for progress and falls back to polling when needed

### Backtests

1. The frontend submits a backtest request.
2. The backend validates and queues the run.
3. Async execution loads the dataset, scopes symbols, and resamples to the requested timeframe when needed.
4. Results, equity series, trade series, and status are persisted.
5. The UI follows progress through WebSocket or polling, then loads detail panes on demand.

### Market Data

1. Operators upload CSV data or create provider import jobs.
2. Import jobs run asynchronously with retry-aware state.
3. New datasets hydrate the normalized market-data store during ingestion.
4. Backtests and telemetry reads use the normalized store when coverage is available.
5. Legacy CSV blobs remain a compatibility fallback only where needed.

## Runtime And Data Model

- Runtime database: PostgreSQL
- Test/build database: H2 `test` profile
- Schema management: Liquibase with `ddl-auto=validate`
- Tracked contracts: `contracts/openapi.json` and `frontend/src/generated/openapi.d.ts`
- Local runtime state: `.runtime/`
- Script-managed PIDs: `.pids/`

Normalized market-data runtime tables:

- `market_data_series`
- `market_data_candle_segments`
- `market_data_candles`

These tables support exact-timeframe reads, controlled rollups, provenance tracking, and gap reporting.

## Repo Structure

```text
C:\Git\algotradingbot\
  AlgotradingBot\   backend application
  frontend\         React/Vite SPA
  contracts\        tracked OpenAPI artifact
  docs\             product docs, guides, research, ADRs
  scripts\          automation and contract tooling
  .runtime\         local runtime state and logs
  .pids\            script-managed process ids
```

## Architecture Rules

1. Keep controller, service, repository, and persistence concerns separated.
2. Use DTOs at HTTP boundaries; do not leak JPA entities directly.
3. Keep money, fees, PnL, and risk calculations on `BigDecimal`.
4. Keep exchange-connected and live-connected behavior behind explicit services and environment gates.
5. Keep guardrails backend-owned; do not hide safety logic inside the UI.
6. Prefer current-state documentation over implementation-history narratives.
