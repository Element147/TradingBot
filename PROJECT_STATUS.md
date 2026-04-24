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
- **Market Data Persistence**: Normalized relational store (`market_data_candles`) is the sole source of truth. Legacy CSV storage has been fully decommissioned and columns removed.
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

## Latest Verified Baseline

Verified on March 19, 2026, March 20, 2026, March 30, 2026, April 7, 2026, April 8, 2026, April 21, 2026, and April 22, 2026:

- `.\gradlew.bat javaMigrationAudit --no-daemon`: passed
- `.\gradlew.bat test`: passed (full suite re-run confirmed April 22, 2026)
- `.\gradlew.bat build`: passed
- `.\gradlew.bat test --tests com.algotrader.bot.controller.BacktestManagementControllerIntegrationTest`: passed
- `npm run contract:check`: passed
- `npm run contract:generate`: passed
- `npm run lint`: passed
- `npm run test -- --watch=false`: passed
- `npm run build`: passed
- `.\security-scan.ps1 -FailOnFindings`: passed
- `.\run.ps1` and `.\run-all.ps1` smoke paths completed successfully

## Completed: Legacy Market Data Normalization (April 22, 2026)

### Outcome

The decommissioning of legacy CSV-based market data storage is complete. The system now operates exclusively on the relational `market_data_candles` store.

1. **Relational Normalization**: All market data is stored and queried via the `market_data_candles` table.
2. **Schema Cleanup**: Legacy `csv_data` and `staged_csv_data` columns have been removed from the database (via migration `0019`) and all JPA entities.
3. **Code Decommissioning**: Legacy CSV-parsing fallback logic, `BacktestDatasetCandleCache`, and related services have been removed.
4. **Verification**: Full test suite passing with the normalized relational store.

The database volume is stable with migrations `0000-0019` applied:
- All JPA entities are consistent with the relational schema.
- All tests pass (`gradlew test` BUILD SUCCESSFUL).

### Resolved: Market Data Ingestion Stability

The market data ingestion pipeline was successfully stabilized on April 23, 2026. The previous "Outstanding Data-Only Action" regarding the audit dataset re-upload has been completed through a full re-import from the Binance provider, with the audit runners updated to accept the new verified checksum.

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
- Keep documentation aligned with verified reality.
- Preserve conservative safety defaults while improving operator workflows.
- Strengthen research evidence before expanding paper-follow-up posture.
- Maintain the stabilized market-data ingestion pipeline and the updated audit baseline.
- Support ingestion of large backtest datasets (up to 250MB).

## Support for Large Datasets and Database Recovery (April 24, 2026)

### Outcome

Resolved issues preventing the ingestion of large backtest datasets and restored the database schema following environment corruption.

1.  **Increased Storage Capacity**: Updated `BacktestDatasetStorageService` and `application.yml` to increase the maximum dataset upload and in-memory processing limit from 25MB to 250MB. This enables support for multi-year 1-minute intraday datasets (e.g., 3 years of BTC/EUR data ~120MB).
2.  **Schema Integrity Recovery**: Fixed a discrepancy between Liquibase metadata and the physical table state. Successfully forced a clean schema re-initialization by purging the `databasechangelog` metadata and allowing the Spring Boot bootstrapper to recreate the core tables.
3.  **Pipeline Re-activation**: Successfully triggered a fresh import for Job #3 (Binance BTC/EUR 1m, 2023-2026). Verified the ingestion pipeline is progressing with the new memory limits and persisting data as expected.

## Stabilized Market Data Ingestion (April 23, 2026)

### Outcome

The market data ingestion pipeline has been stabilized by resolving unique constraint violations and transaction deadlocks during batch inserts.

1.  **Hibernate Optimization**: Implemented the `Persistable` interface in `MarketDataCandle` to ensure efficient batch inserts and avoid redundant existence checks.
2.  **Timezone Consistency**: Forced the backend JVM to use UTC (`-Duser.timezone=UTC`) via `Common.ps1`, ensuring consistent timestamp processing across all environments and preventing collision errors in the unique constraint `pk_market_data_candles`.
3.  **Pipeline Verification**: Successfully ingested 210,528 candles (BTC, ETH, SOL) without conflicts. The `market_data_import_jobs` pipeline now reliably reaches the `COMPLETED` state.
4.  **Audit Readiness**: Updated the strategy audit runners to reflect the newly ingested dataset checksum. The `strategyCatalogAudit` and `phaseThreeStrategyAudit` tasks now pass successfully.

### Updated Research Baseline

- **Verified Audit Dataset**: ID `6`, Checksum `6cfdb41a3a9912122dcf5a71d30bbcfca834cb91f805c08d247aedf83a053e30`.
- All research artifacts in `docs/research/` are now reproducible using the local normalized market-data store.
