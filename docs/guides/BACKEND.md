# Backend Guide

Use this guide when the task is mainly in `AlgotradingBot/`.

## Backend Scope

The backend owns:

- JWT auth and request boundaries
- strategy configuration and lifecycle APIs
- backtest execution, history, compare, replay, export, and telemetry reads
- dataset inventory, provenance, downloads, and retention
- market-data imports and provider credential handling
- paper-trading state, risk controls, exchange profiles, and audit events
- WebSocket event publishing and runtime health surfaces

## Package Boundaries

- `controller`: HTTP entrypoints and DTOs
- `service`: orchestration and business logic
- `service.marketdata`: provider and dataset workflow logic
- `repository`: persistence access
- `entity`: runtime database models
- `backtest`: execution and analytics model
- `backtest.strategy`: strategy registry and implementations
- `risk`, `security`, `config`, `validation`, `repair`, `websocket`: cross-cutting support

## Service Ownership

Backtest path:

- `BacktestManagementService`: command validation, queueing, replay, delete
- `BacktestResultQueryService`: history, summary, detail, compare
- `BacktestTelemetryService`: read-side telemetry reconstruction for completed runs
- `BacktestExecutionService`: async execution and dataset handling
- `BacktestExecutionLifecycleService`: transactional state and result persistence
- `BacktestProgressService`: progress publication

Market-data path:

- `MarketDataImportService`: provider metadata, credentials, job commands
- `MarketDataImportExecutionService`: async import execution
- `MarketDataImportProgressService`: import progress publication
- `BacktestDatasetStorageService`: parsing, storage, downloads
- `BacktestDatasetLifecycleService`: inventory, retention, archive, restore

## Backend Rules

1. Keep controller, service, repository, and entity concerns separated.
2. Use `BigDecimal` for money, fees, price-sensitive paths, and risk.
3. Keep DTOs at the HTTP boundary; do not leak JPA entities.
4. Keep exchange-connected and live-connected behavior behind explicit services and environment gates.
5. Keep critical operator actions auditable.
6. Keep PostgreSQL runtime behavior and H2 test behavior intentionally separate.
7. When a rule depends on strategy mode or capability flags, validate it in services instead of inventing placeholder values on the client.

## Auth And WebSocket Boundaries

- Only `/api/auth/login` and `/api/auth/refresh` are public
- `/api/auth/me`, `/api/auth/logout`, and the rest of `/api/**` require authentication
- JWT secret configuration must be valid at startup
- Token revocation must remain durable in PostgreSQL
- WebSocket upgrades must stay authenticated and environment-scoped
- CORS and WebSocket origins must stay explicitly allowlisted

## Data And Runtime Notes

- Runtime DB: PostgreSQL
- Test/build DB: H2 `test` profile
- Schema ownership: Liquibase with `ddl-auto=validate`
- New uploads and completed imports hydrate the normalized market-data store during ingestion
- Legacy CSV blobs are compatibility data, not the preferred runtime path

## Verification

Start with the narrowest relevant backend check:

```powershell
cd AlgotradingBot
.\gradlew.bat test
.\gradlew.bat build
```

Add:

```powershell
.\gradlew.bat javaMigrationAudit --no-daemon
```

when the change is JDK-sensitive, migration-sensitive, or toolchain-sensitive.
