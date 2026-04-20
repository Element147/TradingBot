# Product

## What It Is For

This software is built for strategy research, market-data preparation, backtesting, paper trading, and operator oversight. It is meant to help you test ideas honestly, review evidence, and manage simulated workflows from one workstation.

It is not a production trading platform and it does not assume any strategy is profitable.

## Main Features

- Strategy catalog with reusable strategy IDs and paper-safe configuration templates
- Backtest workflow with dataset selection, run history, replay, compare, trade review, and export
- Market-data workflow with uploads, provider imports, retry-aware jobs, and dataset provenance
- Forward-testing, paper-trading, and live-monitoring surfaces with environment-aware behavior
- Risk and operations tools such as circuit-breaker review, override visibility, audit history, and exchange/profile settings
- Local-first developer workflow with PowerShell scripts, Docker Compose, tracked contracts, and verification guidance

## Product Surfaces

- `Dashboard`: overall health, environment posture, paper state, and alerts
- `Backtest`: run and inspect historical experiments
- `Forward Testing`: monitor strategy behavior in a paper-safe observation workspace
- `Paper`: simulated order workflows and paper execution review
- `Live`: capability-gated monitoring for live-connected contexts
- `Strategies`: strategy templates, settings, and version history
- `Market Data`: provider imports, job monitoring, and dataset readiness
- `Trades`: trade history and detail review
- `Risk`: limits, circuit breakers, alerts, and override context
- `Settings`: exchange profiles, provider credentials, audit review, and utilities

## How It Works

Typical operator flow:

1. Prepare data by uploading a dataset or importing it from a provider.
2. Configure or review a strategy from the catalog.
3. Run a backtest and inspect the evidence, not just the headline return.
4. If the research is strong enough, follow it in paper-safe monitoring first.
5. Use risk, audit, and settings surfaces to manage the environment around that workflow.

The product is designed to keep those steps connected. Datasets, strategy IDs, run results, and operator actions are meant to stay traceable.

## Environment Model

- `test`: default local and research mode
- `paper`: simulated execution mode
- `live`: explicit live-connected context for monitoring or future gated capabilities

Important rules:

- `test` is the default.
- `paper` is still simulated.
- `live` does not mean live trading is enabled.
- Unsupported live capabilities must fail clearly instead of silently falling back.

## Strategy Posture

The strategy catalog is research-first.

- On March 27, 2026, the frozen strategy audit kept `SMA_CROSSOVER` as the only `paper-monitor candidate`.
- `BUY_AND_HOLD` remains the passive baseline.
- Several catalog and newer Phase 3 strategies remain `research only`.
- `TREND_PULLBACK_CONTINUATION`, `REGIME_FILTERED_MEAN_REVERSION`, `BOLLINGER_BANDS`, and `VWAP_PULLBACK_CONTINUATION` are not active candidates under the current frozen evidence.

Use [`docs/research/STRATEGY_CATALOG_AUDIT_REPORT.md`](docs/research/STRATEGY_CATALOG_AUDIT_REPORT.md) for the detailed research posture.

## Operator Expectations

- Treat backtests and paper results as hypotheses, not promises.
- Keep fees, slippage, and out-of-sample evidence in view.
- Prefer long-or-cash defaults for small-account research.
- Use paper workflows before discussing any live-readiness path.
- Keep risk controls, overrides, and environment state visible and auditable.
