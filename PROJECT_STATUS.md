# Project Status

## Current Maturity

The repository is a usable local-first MVP for strategy research, market-data preparation, and paper-trading workflows.

- Backend and frontend are integrated and usable end to end.
- Default posture is still `test` first.
- Paper workflows are available.
- Live execution is still out of the default product path.

## What Works Now

### Research And Backtesting

- Registry-driven strategy catalog with canonical strategy IDs
- Backtests for `SINGLE_SYMBOL` and `DATASET_UNIVERSE` modes
- Async backtest queueing with progress updates, replay, compare, and export
- Backtest review with lightweight summary reads and on-demand equity, trade, and telemetry queries
- Shared compact table UX across backtest history, dataset inventory, comparison, and paper orders, with sticky headers, inline filters, quick search, persisted column widths, and higher-contrast status pills
- Server-side paged backtest history queries with safe sort whitelisting and filter/range support on `/api/backtests`
- Strategy explainability surfaces reused across Backtest, Forward Testing, Paper, and Live

### Market Data

- CSV dataset uploads and provider imports
- Persistent import jobs with retry-aware state and polling/WebSocket monitoring
- Normalized market-data store for runtime query paths
- Startup backfill for older datasets that still need normalized candle segments
- Dataset provenance, retention, download, archive, and restore flows
- Kraken public OHLC imports now fail fast when the requested range is older than its rolling 720-candle provider limit, instead of exhausting retries against an impossible window

### Paper Trading And Operations

- Strategy configuration and template review
- Forward-testing workspace, paper-trading desk, and live-monitoring route
- Risk controls, circuit-breaker visibility, override context, and audit history
- Exchange/profile management and provider credential storage
- JWT auth, durable token revocation, and authenticated WebSocket subscriptions

### Local Workflow

- Fast local mode with backend and frontend running locally
- Docker-backed full-stack mode
- Contract tracking via OpenAPI artifacts
- Frontend and backend verification flows aligned with local runbooks

## Experimental Or Incomplete Areas

- Live trading is not enabled by default.
- Some live reads are capability-gated and must fail closed when unsupported.
- Strategy evidence is still narrow; most strategies remain research-only.
- Provider coverage is intentionally limited to the currently supported free/public sources.
- Legacy CSV compatibility still exists for some dataset download and fallback paths.

## Latest Verified Baseline

Verified on March 19, 2026, March 20, 2026, March 30, 2026, April 7, 2026, April 8, 2026, and April 21, 2026:

- `.\gradlew.bat javaMigrationAudit --no-daemon`: passed
- `.\gradlew.bat test`: passed (full suite re-run confirmed April 21, 2026)
- `.\gradlew.bat build`: passed
- `.\gradlew.bat test --tests com.algotrader.bot.controller.BacktestManagementControllerIntegrationTest`: passed
- `npm run contract:check`: passed
- `npm run contract:generate`: passed
- `npm run lint`: passed
- `npm run test -- --watch=false`: passed
- `npm run build`: passed
- `.\security-scan.ps1 -FailOnFindings`: passed
- `.\run.ps1` and `.\run-all.ps1` smoke paths completed successfully

## Known Incident: Postgres Volume Migration Drift (April 21, 2026) — CLOSED

### Root Cause

The Postgres Docker volume (`algotradingbot_postgres_data`) contained database migrations **0019 through 0022** that were applied by a prior Codex session but **never committed to source control**. The critical migration dropped the `csv_data` column from `backtest_datasets` and `staged_csv_data` from `market_data_import_jobs`. However, the committed Java code still actively uses both columns:

- `BacktestDatasetCandleCache.java` reads `csv_data` for the legacy CSV fallback path
- `BacktestDatasetStorageService.java` reads `csv_data` for dataset download fallback
- `LegacyMarketDataMigrationService.java` reads `csv_data` to parse candles during migration
- `MarketDataImportExecutionService.java` reads and writes `staged_csv_data` during async import

This means the out-of-tree migrations were **erroneous** — they removed columns the application still requires. When the application started against that stale volume, Hibernate schema validation failed with `Schema validation: missing column [csv_data]`, blocking all backtest executions.

### Resolution

The stale volume was reset with `docker compose down -v`. The fresh volume running migrations 0000-0018 is the correct authoritative state:

- All JPA entities are consistent with the schema
- All tests pass (`gradlew test` BUILD SUCCESSFUL, full suite, April 21 2026)
- No new migration files are needed — the old 0019-0022 must not be reconstructed as-written because they would re-introduce the same mismatch
- No entity changes are needed — `csv_data` and `staged_csv_data` remain valid mapped columns

### Outstanding Data-Only Action

The audit dataset (`id=12`, `Binance BTC/USDT +2 15m 2024-03-12 to 2026-03-12`, checksum `b93c95da97c05f4edf4d706b80d33fcfab752f4f4d6f11f003fa3aca2fe2d326`) must be re-uploaded via the dataset management API before the `strategyCatalogAudit` and `phaseThreeStrategyAudit` Gradle tasks can succeed. This is a data-only action, not a code change. Import jobs for the relational market-data store will also need to be re-run after re-upload.

## Current Research Posture

Frozen audit posture as of March 27, 2026:

- `SMA_CROSSOVER`: `paper-monitor candidate`
- `BUY_AND_HOLD`: `baseline only`
- `DUAL_MOMENTUM_ROTATION`, `VOLATILITY_MANAGED_DONCHIAN_BREAKOUT`, `TREND_FIRST_ADAPTIVE_ENSEMBLE`, `ICHIMOKU_TREND`: `research only`
- `TREND_PULLBACK_CONTINUATION`, `REGIME_FILTERED_MEAN_REVERSION`, `BOLLINGER_BANDS`: `archive candidate`
- Newer Phase 3 strategies remain conservative; `VWAP_PULLBACK_CONTINUATION` is rejected under the current BTC-anchor evidence, and none of the Phase 3 set is promoted

Use [`docs/research/STRATEGY_CATALOG_AUDIT_REPORT.md`](docs/research/STRATEGY_CATALOG_AUDIT_REPORT.md) for the detailed evidence summary.

## Current Constraints

- Backtests and paper trading remain simulation workflows.
- Direct short exposure is limited to research and paper contexts when explicitly enabled.
- Live direct shorting, leverage, and margin remain out of scope by default.
- Strict auth is the normal posture; relaxed auth is for local debugging only.
- Backend test and build tasks should be run sequentially to avoid Gradle temp-file races.

## Current Priorities

- Keep documentation aligned with verified reality.
- Preserve conservative safety defaults while improving operator workflows.
- Strengthen research evidence before expanding paper-follow-up posture.
- Keep the market-data pipeline reliable before adding more provider scope.
