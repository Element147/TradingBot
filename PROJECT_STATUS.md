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
