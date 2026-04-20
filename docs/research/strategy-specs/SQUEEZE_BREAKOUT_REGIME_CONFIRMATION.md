# Strategy Spec: Squeeze Breakout Regime Confirmation v1

Related research references:

- `docs/research/STRATEGY_SPEC_TEMPLATE.md`
- `docs/research/STRATEGY_AUDIT_PROTOCOL.md`
- `TRADING_GUARDRAILS.md`

## 1. Strategy Identity

- Strategy name: Squeeze Breakout Regime Confirmation
- Version: v1
- Owner: Quant research
- Source posture: research candidate
- Status: implementation-ready

## 2. Hypothesis

- Volatility compression can precede meaningful directional expansion, but only a minority of compressions become durable breakouts after costs.
- Requiring a squeeze condition plus trend-aware regime filtering plus breakout momentum confirmation may reduce false positives enough to make compression breakouts worth continued research.
- The hypothesis is falsified if the strategy mostly produces failed expansions, if the higher-timeframe regime filter does not materially improve false-signal quality, or if realistic costs erase the edge.

## 3. Market Regime

- Allowed regimes: bullish or neutral higher-timeframe context with no explicit trend-down classification.
- Disabled regimes: bearish regime, broken long-trend floor, or non-compressed volatility states.
- Regime boundary: shared trend filter, ADX-based classifier, and squeeze-aware volatility filter.

## 4. Universe

- Approved asset class: liquid ETFs first, liquid crypto majors as secondary research only.
- Approved symbols or universe definition: `SPY`, `QQQ`, `IWM` first; crypto majors only when dataset notes make the venue and timeframe behavior explicit.
- Liquidity rules: liquid names only, no thin small caps, no wide-spread names.
- Session rules: multi-hour intraday or hourly use only in v1.
- Minimum-order and fractionality assumptions: reject any order that would violate the small-account risk cap or minimum-notional rules in `TRADING_GUARDRAILS.md`.

## 5. Timeframe

- Primary signal timeframe: `1h`
- Higher-timeframe context: `50` and `200` EMA proxy stack on the same feed
- Lower-timeframe confirmation: none in v1 beyond breakout momentum
- Minimum candle history before valid signals: `201` bars

## 6. Signal Stack

- Setup condition: Bollinger-width compression stays below the squeeze threshold while the slower EMA trend stack remains constructive.
- Entry trigger: price breaks above the recent `20`-bar high after the squeeze.
- Confirmation filters: short-horizon return expansion, ADX confirmation, no trend-down regime, and ATR percent below the cap.
- Stand-aside filters: no squeeze, no breakout, weak momentum, or bearish regime filter.
- Exit trigger: failed expansion back below the breakout level, protective-stop breach, or regime break.

## 7. Long Behavior

- Enter long only on confirmed squeeze breakouts.
- The strategy is single-entry only in v1. No pyramiding or scaling.
- The design aims to filter sideways false signals more aggressively than a plain breakout system.

## 8. Bearish Behavior

- Bearish state behavior: sell to cash and stand aside.
- Direct shorting is not required in v1.
- No short proxy is assumed in the first implementation.

## 9. Risk Model

- Max risk per trade: `1.0%` of account as the planning baseline.
- Position-sizing method: capped volatility-managed allocation with a `0.25` floor and `0.45` cap.
- Max concurrent positions: `1`
- Stop model: `1.25 ATR` fail-safe stop, tightened by the breakout level once expansion is active.
- Time stop: none in v1; this version exits on failed expansion or regime break.
- Kill-switch or cooldown behavior: skip entries when ATR percent breaches the cap.

## 10. Exit Model

- Planned profit-taking behavior: no fixed target in v1.
- Planned loss-cutting behavior: exit on failed expansion, protective-stop breach, or regime break.
- Regime-break exit behavior: bearish regime invalidation forces exit.
- End-of-session or end-of-day flattening behavior: not required in v1 because the first implementation is a multi-hour breakout path.

## 11. Telemetry And Explainability

- Required indicator values: `20`, `50`, and `200` EMA context, Bollinger squeeze width, breakout level, ADX, ATR.
- Required reason labels: squeeze active, breakout confirmed, momentum confirmed, failed expansion, regime break, protective stop.
- Required operator-facing notes: research-only status and explicit breakout-failure-rate tracking in reporting.

## 12. Validation Plan

- Datasets to use: liquid ETF hourly pack first, then crypto majors with clear regime notes.
- Fee and slippage assumptions: frozen audit baseline of `10` bps fees and `3` bps slippage unless a stricter scenario is documented.
- In-sample / holdout rule: follow `docs/research/STRATEGY_AUDIT_PROTOCOL.md` with warm-up-safe splits.
- Walk-forward expectation: anchored walk-forward bundle from the frozen audit method.
- Benchmark comparisons: `VOLATILITY_MANAGED_DONCHIAN_BREAKOUT`, `SMA_CROSSOVER`, and the new pullback strategies.
- Sensitivity tests: squeeze width threshold, breakout lookback, ADX threshold, and ATR stop should be reviewed conservatively.
- Automatic rejection conditions: high breakout failure rate, no out-of-sample edge after costs, or no reduction in sideways false signals relative to plainer breakouts.

## 13. Overfitting Risk Review

- Parameters likely to overfit: squeeze width threshold, breakout lookback, momentum-return threshold, ADX threshold, and ATR stop.
- Future-information leak risks: any use of future bars in squeeze or breakout computation.
- Simplifications for honesty: no pyramiding, no shorting, one breakout direction, and one breakout lookback.
- Parameters fixed before optimization: `20`-bar breakout lookback, `4.0%` squeeze width, `20` ADX confirmation, `0.8%` momentum return filter, and `1.25 ATR` stop multiple.

## 14. Minimum Evidence Threshold

Before the strategy can move beyond `research-only`:

- minimum trade count: at least `40` combined out-of-sample trades across the approved dataset pack
- minimum acceptable out-of-sample behavior: positive net return after costs with a breakout failure rate that is materially better than a naive breakout baseline
- maximum acceptable fee drag: fee plus slippage drag must stay materially below the median gross breakout winner
- minimum benchmark comparison bar: must show a credible false-signal reduction versus a plain breakout baseline in at least one approved holdout
- required paper-monitoring evidence: clear telemetry for squeeze state, breakout confirmation, and failed-expansion exits
