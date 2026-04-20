# Strategy Catalog Audit Report

This file consolidates the current frozen strategy-audit posture. It summarizes the main catalog results and the newer Phase 3 strategy results using the March 27, 2026 BTC-anchor reruns.

## Frozen Inputs

- Dataset: `#12` (`Binance BTC/USDT +2 15m 2024-03-12 to 2026-03-12`)
- Dataset checksum: `b93c95da97c05f4edf4d706b80d33fcfab752f4f4d6f11f003fa3aca2fe2d326`
- Fees: `10` bps
- Slippage: `3` bps
- Fill model: next-bar-open
- Holdout split: `2024-03-12` to `2025-07-01` in-sample, `2025-07-01` to `2026-03-12` out-of-sample
- Important limitation: the checked-in ETF audit pack is daily-only, so it cannot yet clear `15m` or `1h` intraday hypotheses honestly

## Current Summary

- `SMA_CROSSOVER` is the only current `paper-monitor candidate`.
- `BUY_AND_HOLD` remains the passive baseline.
- Several catalog strategies remain `research only` because evidence is sparse or mixed.
- Several weaker strategies are retained only as comparison baselines or archive candidates.
- None of the newer intraday-oriented Phase 3 strategies is promoted under the current frozen evidence.

## Main Catalog Dispositions

| Strategy | Disposition | Notes |
| --- | --- | --- |
| `BUY_AND_HOLD` | `baseline only` | Passive benchmark, not a promoted active path |
| `SMA_CROSSOVER` | `paper-monitor candidate` | Best current out-of-sample signal, still not production-ready |
| `DUAL_MOMENTUM_ROTATION` | `research only` | Stronger full sample than holdout evidence |
| `VOLATILITY_MANAGED_DONCHIAN_BREAKOUT` | `research only` | Strong full sample, but holdout evidence is sparse |
| `TREND_FIRST_ADAPTIVE_ENSEMBLE` | `research only` | Needs stronger follow-up evidence |
| `ICHIMOKU_TREND` | `research only` | Honest implementation, but evidence remains limited |
| `TREND_PULLBACK_CONTINUATION` | `archive candidate` | Weak under the frozen audit |
| `REGIME_FILTERED_MEAN_REVERSION` | `archive candidate` | Weak and sparse |
| `BOLLINGER_BANDS` | `archive candidate` | Improved technically, still weak in the audit |

## Catalog Highlights

- `SMA_CROSSOVER` stayed positive out of sample at `7.08%`.
- `VOLATILITY_MANAGED_DONCHIAN_BREAKOUT` and `DUAL_MOMENTUM_ROTATION` had strong full-sample returns but no out-of-sample trades in the frozen window.
- `BUY_AND_HOLD` lost `-40.06%` out of sample on the BTC anchor and remains a benchmark only.
- `TREND_PULLBACK_CONTINUATION`, `REGIME_FILTERED_MEAN_REVERSION`, and `BOLLINGER_BANDS` remained too weak for active follow-up.

## Phase 3 Strategy Dispositions

| Strategy | Disposition | Notes |
| --- | --- | --- |
| `OPENING_RANGE_VWAP_BREAKOUT` | `research only` | Informative trade count, but negative after costs |
| `VWAP_PULLBACK_CONTINUATION` | `reject` | Severe fee drag and weak full-sample and holdout results |
| `EXHAUSTION_REVERSAL_FADE` | `research only` | Mechanically valid, still negative after costs |
| `MULTI_TIMEFRAME_EMA_ADX_PULLBACK` | `research only` | Cleaner drawdown profile, but still not strong enough |
| `SQUEEZE_BREAKOUT_REGIME_CONFIRMATION` | `research only` | Better than some weaker paths, still not promotable |
| `RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER` | `research only` | Timing filter did not rescue the concept on the current pack |

## Why The Posture Is Still Conservative

- The frozen BTC anchor alone is not enough to justify broader claims.
- The intraday Phase 3 set still lacks an approved intraday ETF audit anchor.
- Positive full-sample results without strong out-of-sample evidence do not justify promotion.
- The audit protocol fails closed on sparse trade counts, weak holdout windows, and unsupported claims.

## Reproducible Artifacts

Generated reports:

- `AlgotradingBot/build/reports/strategy-catalog-audit/report.md`
- `AlgotradingBot/build/reports/phase-three-strategy-audit/report.md`

Use this summary together with:

- [`STRATEGY_AUDIT_PROTOCOL.md`](STRATEGY_AUDIT_PROTOCOL.md)
- [`STRATEGY_AUDIT_DATASET_PACK.md`](STRATEGY_AUDIT_DATASET_PACK.md)
