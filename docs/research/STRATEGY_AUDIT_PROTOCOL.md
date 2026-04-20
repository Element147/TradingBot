# Strategy Audit Protocol

Research reference for the frozen strategy-evaluation methodology. This document defines how the repository audits strategy evidence before any strategy is described as a watchlist item or a paper-monitor candidate.

It does not override the current product posture in `README.md`, `PROJECT_STATUS.md`, `PRODUCT.md`, `ARCHITECTURE.md`, or `TRADING_GUARDRAILS.md`.

## Purpose

Freeze one repeatable audit method before rerunning or reclassifying strategies. The goal is to judge later results by the same rules instead of changing standards after seeing outcomes.

## Core Rules

1. Treat every strategy as a research hypothesis, not as a profitability claim.
2. Use explicit fees, slippage, timeframe handling, and fill assumptions in every run.
3. Prefer long-or-cash behavior by default; direct short exposure remains opt-in for research and paper only.
4. Fail closed on unrealistic assumptions, sparse evidence, or insufficient out-of-sample coverage.
5. Record enough metadata that another operator can reproduce the audit.

## Required Audit Record

Every audited run set must record:

- strategy ID and parameter version
- dataset ID or name
- dataset checksum and schema identity
- symbol scope and requested timeframe
- selection mode
- date range
- fee assumption
- slippage assumption
- execution model notes
- environment posture
- benchmark used for comparison

If any of that metadata is missing, the audit is incomplete.

## Realism Baseline

Minimum realism bundle:

- fees included
- slippage included
- requested timeframe honored through exact data or explicit resampling
- next-bar-open fills or another explicit fill model
- no look-ahead use of future candles or indicators
- minimum-order, lot-size, and notional constraints called out when they matter

Current default cost baseline:

- fees: `10` bps
- slippage: `3` bps

## Dataset Policy

The approved audit bundle is documented in [`STRATEGY_AUDIT_DATASET_PACK.md`](STRATEGY_AUDIT_DATASET_PACK.md).

The current policy requires:

- at least one liquid crypto-major dataset
- at least one small-account-friendly equity or ETF dataset
- reproducible provenance for every dataset
- no strategy-specific cherry-picked windows
- the same pack for all strategies in one audit round

## Evaluation Bundle

Each audit must include:

1. Full-sample run
2. Frozen holdout split
3. Anchored walk-forward review
4. Benchmark comparison

The full-sample run is useful for inspection, but it is never enough by itself for promotion.

## Disposition Labels

- `reject`: not suitable for active follow-up under the current evidence
- `research only`: worth retaining as a hypothesis, but not ready for paper follow-up
- `watchlist`: stronger than research-only, but still not paper-ready
- `paper-monitor candidate`: allowed into cautious paper follow-up with explicit caveats

## Reporting Rules

Every audit writeup should state:

- dataset and date window
- requested timeframe
- costs and execution assumptions
- in-sample and out-of-sample split
- trade count, drawdown, Sharpe, profit factor, and win rate
- known limitations and failure regimes

Never describe the results as guaranteed returns.
