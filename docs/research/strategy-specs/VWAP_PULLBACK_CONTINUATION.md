# Strategy Spec: VWAP Pullback Continuation v1

Related research references:

- `docs/research/STRATEGY_SPEC_TEMPLATE.md`
- `docs/research/STRATEGY_AUDIT_PROTOCOL.md`
- `TRADING_GUARDRAILS.md`

## 1. Strategy Identity

- Strategy name: VWAP Pullback Continuation
- Version: v1
- Owner: Quant research
- Source posture: research candidate
- Status: implementation-ready

## 2. Hypothesis

- Strong intraday trends often offer cleaner continuation entries after price resets toward session VWAP or a medium EMA instead of on the initial impulse.
- Requiring a higher-timeframe bias plus pullback contact plus momentum re-acceleration may preserve more edge after costs than blunt breakout chasing.
- The hypothesis is falsified if the pullback entry still overtrades noise, fails after realistic fees and slippage, or shows no consistent same-day follow-through.

## 3. Market Regime

- Allowed regimes: bullish intraday trend, orderly volatility, same-session continuation conditions.
- Disabled regimes: bearish or broken higher-timeframe trend, late-session signals, oversized ATR conditions, or continuation attempts without a real pullback.
- Regime boundary: shared trend-filter plus ATR-cap rules; v1 does not activate when the higher-timeframe bias disagrees.

## 4. Universe

- Approved asset class: liquid ETFs first, liquid crypto majors as secondary research only.
- Approved symbols or universe definition: `SPY`, `QQQ`, `IWM` first; crypto majors only when the session assumption is documented in the dataset notes.
- Liquidity rules: high-dollar-volume instruments only, no thin small caps, no wide-spread names.
- Session rules: regular-session intraday use, same-day flattening only, no overnight hold.
- Minimum-order and fractionality assumptions: skip orders that violate the small-account risk cap or minimum-notional rules in `TRADING_GUARDRAILS.md`.

## 5. Timeframe

- Primary signal timeframe: `15m`
- Higher-timeframe context: `20` and `50` bar EMA bias on the same intraday feed
- Lower-timeframe confirmation: none in v1
- Minimum candle history before valid signals: `51` bars

## 6. Signal Stack

- Setup condition: bullish higher-timeframe bias remains intact and the session is still in the allowed entry window.
- Entry trigger: price touches session VWAP or the pullback EMA, then reclaims the fast EMA or confirms with an RSI reset.
- Confirmation filters: close is back above session VWAP, the higher-timeframe trend filter still agrees, and ATR percent stays below the cap.
- Stand-aside filters: no support touch, late session, bearish higher-timeframe bias, or oversized ATR conditions.
- Exit trigger: VWAP loss, pullback EMA loss, protective stop breach, or mandatory session-cutoff flattening.

## 7. Long Behavior

- Enter long on the first confirmed continuation after the pullback-support touch.
- The strategy is single-entry only in v1. No pyramiding or scaling.
- Positions exit fully on invalidation or session cutoff.

## 8. Bearish Behavior

- Bearish state behavior: sell to cash and stand aside.
- Direct shorting is not required in v1.
- No short proxy is assumed in the first implementation.

## 9. Risk Model

- Max risk per trade: `1.0%` of account as the planning baseline.
- Position-sizing method: capped volatility-managed allocation with a `0.35` floor and `0.55` cap in the first implementation.
- Max concurrent positions: `1`
- Stop model: `1.10 ATR` fail-safe stop, tightened by the higher of session VWAP or pullback EMA support.
- Time stop: same-session mandatory flattening by the cutoff.
- Kill-switch or cooldown behavior: skip entries after the last-entry cutoff and skip trades when ATR percent breaches the cap.

## 10. Exit Model

- Planned profit-taking behavior: no fixed profit target in v1.
- Planned loss-cutting behavior: exit on session VWAP loss, pullback EMA failure, or protective-stop breach.
- Regime-break exit behavior: higher-timeframe bias loss forces exit.
- End-of-session or end-of-day flattening behavior: mandatory flatten at or after the session cutoff, with rollover protection on the next session's first bar.

## 11. Telemetry And Explainability

- Required indicator values: session VWAP, `5` EMA, `20` EMA, `50` EMA, RSI, ATR.
- Required reason labels: pullback resumed, higher-timeframe bias disagreed, VWAP support lost, EMA support lost, protective stop hit, session cutoff flatten.
- Required operator-facing notes: research-only status, same-day flatten rule, and warning that the strategy has not yet passed the frozen audit.

## 12. Validation Plan

- Datasets to use: liquid ETF intraday pack first, then crypto majors with explicit session assumptions.
- Fee and slippage assumptions: frozen audit baseline of `10` bps fees and `3` bps slippage unless a stricter scenario is documented.
- In-sample / holdout rule: follow `docs/research/STRATEGY_AUDIT_PROTOCOL.md` with warm-up-safe splits.
- Walk-forward expectation: anchored walk-forward bundle from the frozen audit method.
- Benchmark comparisons: `BUY_AND_HOLD`, `SMA_CROSSOVER`, and the other Phase 3 intraday strategies once implemented.
- Sensitivity tests: pullback EMA period, RSI thresholds, ATR cap, stop multiple, and entry cutoff should be reviewed conservatively.
- Automatic rejection conditions: no out-of-sample edge after costs, too-few trades, heavy sensitivity to one threshold, or failure to flatten reliably by session close.

## 13. Overfitting Risk Review

- Parameters likely to overfit: EMA periods, RSI reset thresholds, ATR cap, stop multiple, and session cutoffs.
- Future-information leak risks: using future bars in VWAP, same-bar fills, or reclaim logic that looks beyond the current candle.
- Simplifications for honesty: no pyramiding, one timeframe, one support zone definition, no shorting, and explicit same-day flattening.
- Parameters fixed before optimization: `5` EMA resume trigger, `20` EMA pullback support, `50` EMA trend bias, `45/50` RSI reset thresholds, `1.10 ATR` stop multiple, and the session cutoffs.

## 14. Minimum Evidence Threshold

Before the strategy can move beyond `research-only`:

- minimum trade count: at least `40` combined out-of-sample trades across the approved dataset pack
- minimum acceptable out-of-sample behavior: positive net return after costs without dependence on one narrow month or event cluster
- maximum acceptable fee drag: fee plus slippage drag must stay materially below the median gross winner
- minimum benchmark comparison bar: must beat `BUY_AND_HOLD` on drawdown-aware terms in at least one approved holdout without undercutting realism
- required paper-monitoring evidence: repeatable same-session flattening, no unsupported short behavior, and clear telemetry for stand-aside, entry, and exit reasons
