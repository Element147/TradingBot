# Strategy Spec: Multi-Timeframe EMA ADX Pullback v1

Related research references:

- `docs/research/STRATEGY_SPEC_TEMPLATE.md`
- `docs/research/STRATEGY_AUDIT_PROTOCOL.md`
- `TRADING_GUARDRAILS.md`

## 1. Strategy Identity

- Strategy name: Multi-Timeframe EMA ADX Pullback
- Version: v1
- Owner: Quant research
- Source posture: research candidate
- Status: implementation-ready

## 2. Hypothesis

- Moderate-turnover continuation entries may survive costs better than raw breakout chasing when a slower trend is already aligned and the lower-timeframe trigger waits for a controlled pullback to reset risk.
- A multi-hour trend stack can be approximated conservatively with nested EMAs on the same feed until explicit higher-timeframe resampling becomes part of the research harness.
- The hypothesis is falsified if the pullback logic simply reproduces the older trend strategy without cleaner drawdown behavior, or if ADX or volatility confirmation fails to filter noisy continuation attempts.

## 3. Market Regime

- Allowed regimes: aligned bullish EMA stack, orderly volatility, and continuation conditions after a contained pullback.
- Disabled regimes: broken slow-trend alignment, pullbacks that undercut the medium EMA, or weak continuation attempts without ADX or volatility confirmation.
- Regime boundary: shared `StrategyFeatureLibrary` trend and volatility filters.

## 4. Universe

- Approved asset class: liquid ETFs first, liquid crypto majors as secondary research only.
- Approved symbols or universe definition: `SPY`, `QQQ`, `IWM` first; crypto majors only when the timeframe assumption is documented in the dataset notes.
- Liquidity rules: liquid instruments only, no thin small caps, no wide-spread names.
- Session rules: intraday or multi-hour use only, with conservative research posture until audit evidence exists.
- Minimum-order and fractionality assumptions: reject any order that would violate the small-account risk cap or minimum-notional rules in `TRADING_GUARDRAILS.md`.

## 5. Timeframe

- Primary signal timeframe: `1h` in the first implementation
- Higher-timeframe context: `50` and `200` EMA alignment on the same feed as a conservative higher-timeframe proxy
- Lower-timeframe confirmation: `8` EMA reclaim after a `21` EMA pullback
- Minimum candle history before valid signals: `201` bars

## 6. Signal Stack

- Setup condition: `200`, `50`, and `21` EMAs are aligned bullish and still sloping constructively.
- Entry trigger: price pulls back into the `21` EMA zone without losing the `50` EMA, then closes back above the `8` EMA.
- Confirmation filters: ADX strength or a healthy ATR-cap volatility profile confirms the continuation attempt.
- Stand-aside filters: slow-trend misalignment, no actual pullback, no trigger reclaim, or oversized ATR conditions.
- Exit trigger: medium-EMA support fails or the ATR or structural stop is breached.

## 7. Long Behavior

- Enter long only when higher-timeframe alignment, pullback definition, and continuation trigger all agree.
- The strategy is single-entry only in v1. No pyramiding or scaling.
- The intent is cleaner multi-hour continuation, not frequent intraday churn.

## 8. Bearish Behavior

- Bearish state behavior: sell to cash and stand aside.
- Direct shorting is not required in v1.
- No short proxy is assumed in the first implementation.

## 9. Risk Model

- Max risk per trade: `1.0%` of account as the planning baseline.
- Position-sizing method: capped volatility-managed allocation with a `0.25` floor and `0.50` cap in the first implementation.
- Max concurrent positions: `1`
- Stop model: `1.20 ATR` fail-safe stop, tightened by the medium EMA structural support.
- Time stop: none in v1; the strategy exits on structure or stop failure instead.
- Kill-switch or cooldown behavior: skip new entries when ATR percent breaches the cap.

## 10. Exit Model

- Planned profit-taking behavior: no fixed target in v1.
- Planned loss-cutting behavior: exit on medium-EMA support loss or protective-stop breach.
- Regime-break exit behavior: slower EMA misalignment forces exit.
- End-of-session or end-of-day flattening behavior: not required in v1 because the first implementation is a multi-hour continuation path, not a same-session mandatory-flatten strategy.

## 11. Telemetry And Explainability

- Required indicator values: `8`, `21`, `50`, and `200` EMAs, ADX, ATR.
- Required reason labels: higher-timeframe trend aligned, pullback detected, continuation resumed, support failed, protective stop hit.
- Required operator-facing notes: research-only status and warning that the strategy is a same-feed higher-timeframe proxy until explicit resampling is added.

## 12. Validation Plan

- Datasets to use: liquid ETF hourly pack first, then crypto majors with clear notes on regime differences.
- Fee and slippage assumptions: frozen audit baseline of `10` bps fees and `3` bps slippage unless a stricter scenario is documented.
- In-sample / holdout rule: follow `docs/research/STRATEGY_AUDIT_PROTOCOL.md` with warm-up-safe splits.
- Walk-forward expectation: anchored walk-forward bundle from the frozen audit method.
- Benchmark comparisons: `SMA_CROSSOVER`, `TREND_PULLBACK_CONTINUATION`, and the other intraday continuation strategies once implemented.
- Sensitivity tests: EMA stack, ADX threshold, ATR cap, and stop multiple should be reviewed conservatively.
- Automatic rejection conditions: no clear drawdown improvement over simpler trend systems, no out-of-sample edge after costs, or excessive trigger churn.

## 13. Overfitting Risk Review

- Parameters likely to overfit: EMA periods, ADX threshold, ATR cap, and stop multiple.
- Future-information leak risks: any future-bar use in the trigger reclaim or later multi-timeframe resampling.
- Simplifications for honesty: no pyramiding, no shorting, one proxy higher-timeframe stack, and one explicit pullback trigger.
- Parameters fixed before optimization: `8/21/50/200` EMA stack, `18` ADX threshold, and `1.20 ATR` stop multiple.

## 14. Minimum Evidence Threshold

Before the strategy can move beyond `research-only`:

- minimum trade count: at least `40` combined out-of-sample trades across the approved dataset pack
- minimum acceptable out-of-sample behavior: positive net return after costs with no severe concentration in one regime
- maximum acceptable fee drag: fee plus slippage drag must stay materially below the median gross continuation winner
- minimum benchmark comparison bar: must show a credible drawdown-aware improvement over simpler trend systems in at least one approved holdout
- required paper-monitoring evidence: clear telemetry for trend alignment, pullback definition, continuation trigger, and exit reasons
