# Strategy Spec: Exhaustion Reversal Fade v1

Related research references:

- `docs/research/STRATEGY_SPEC_TEMPLATE.md`
- `docs/research/STRATEGY_AUDIT_PROTOCOL.md`
- `TRADING_GUARDRAILS.md`

## 1. Strategy Identity

- Strategy name: Exhaustion Reversal Fade
- Version: v1
- Owner: Quant research
- Source posture: research candidate
- Status: implementation-ready

## 2. Hypothesis

- Sharp same-session downside extensions can produce tradeable mean-reversion bounces when volatility has clearly expanded, the move is visibly stretched versus both VWAP and Bollinger support, and a bullish reversal candle confirms that the selloff is losing control.
- The setup should be used sparingly. It is intended to fade exhaustion, not to buy every dip inside a live downtrend.
- The hypothesis is falsified if realistic fees and slippage erase the bounce edge, if the logic mostly catches normal trend continuation instead of exhaustion, or if time-stop and hard-stop exits dominate the outcome distribution.

## 3. Market Regime

- Allowed regimes: range-bound intraday conditions, neutral-to-bullish higher-timeframe context, or explicit climactic downside exhaustion that meets the override conditions.
- Disabled regimes: late session, low-volatility drift, ordinary downtrend continuation, or any move that is stretched but not reversing.
- Regime boundary: shared `StrategyFeatureLibrary` trend filter, range classifier, volume confirmation, and ATR-cap filter.

## 4. Universe

- Approved asset class: liquid ETFs first, liquid crypto majors as secondary research only.
- Approved symbols or universe definition: `SPY`, `QQQ`, `IWM` first; crypto majors only when the session assumption is documented in the dataset notes.
- Liquidity rules: high-dollar-volume names only, no thin small caps, no wide-spread names, and no instruments where same-session mean reversion is structurally unreliable.
- Session rules: regular-session intraday use, same-day flattening only, no overnight hold.
- Minimum-order and fractionality assumptions: reject any order that would violate the small-account risk cap or minimum-notional rules in `TRADING_GUARDRAILS.md`.

## 5. Timeframe

- Primary signal timeframe: `15m`
- Higher-timeframe context: `20` and `50` bar EMA bias on the same intraday feed
- Lower-timeframe confirmation: none in v1
- Minimum candle history before valid signals: `51` bars

## 6. Signal Stack

- Setup condition: session is still in the allowed entry window, price is below session VWAP, and the move is stretched versus the lower Bollinger band or by at least `0.80 ATR` below session VWAP.
- Entry trigger: a bullish reversal candle closes back through the candle midpoint after the extension, with current range at least `1.10 ATR`.
- Confirmation filters: RSI stays in an oversold zone, ATR percent remains below the cap, and the regime is either range-friendly or a strong-downtrend exhaustion override is active.
- Stand-aside filters: weak volume in a strong downtrend, no real reversal candle, low-volatility drift, or any late-session signal.
- Exit trigger: explicit profit target, explicit time stop, explicit hard-stop or ATR-stop breach, or mandatory session-cutoff flattening.

## 7. Long Behavior

- Enter long only after downside exhaustion is confirmed by stretch plus reversal.
- The strategy is single-entry only in v1. No pyramiding, scaling, or averaging down.
- Positions are expected to mean revert quickly; if they do not, the time stop forces exit.

## 8. Bearish Behavior

- Bearish state behavior: sell to cash and stand aside.
- Direct shorting is not required in v1.
- Strong downtrends are only faded when the explicit exhaustion override is active.

## 9. Risk Model

- Max risk per trade: `1.0%` of account as the planning baseline.
- Position-sizing method: capped volatility-managed allocation with a `0.20` floor and `0.35` cap in the first implementation.
- Max concurrent positions: `1`
- Stop model: `1.00 ATR` fail-safe stop with an explicit `0.985x` hard-stop floor versus entry price.
- Time stop: exit after `6` holding bars if the bounce does not mean revert quickly enough.
- Kill-switch or cooldown behavior: skip entries after the last-entry cutoff and skip trades when ATR percent breaches the cap.

## 10. Exit Model

- Planned profit-taking behavior: take profits at the earlier of session VWAP reclaim or `1.00 ATR` above entry.
- Planned loss-cutting behavior: exit immediately on hard-stop or ATR-stop breach.
- Regime-break exit behavior: no regime-scale hold is allowed; the strategy is a short-horizon bounce only.
- End-of-session or end-of-day flattening behavior: mandatory flatten at or after the session cutoff, with rollover protection on the next session's first bar.

## 11. Telemetry And Explainability

- Required indicator values: session VWAP, `20` EMA, `50` EMA, lower Bollinger band, RSI, ADX, ATR, volume ratio.
- Required reason labels: exhaustion fade confirmed, climactic override required, profit target hit, time stop hit, hard stop hit, session cutoff flatten.
- Required operator-facing notes: research-only status, same-day flatten rule, and warning that the strategy is not audit-cleared.

## 12. Validation Plan

- Datasets to use: liquid ETF intraday pack first, then crypto majors with explicit session assumptions.
- Fee and slippage assumptions: frozen audit baseline of `10` bps fees and `3` bps slippage unless a stricter scenario is documented.
- In-sample / holdout rule: follow `docs/research/STRATEGY_AUDIT_PROTOCOL.md` with warm-up-safe splits.
- Walk-forward expectation: anchored walk-forward bundle from the frozen audit method.
- Benchmark comparisons: `BUY_AND_HOLD`, `SMA_CROSSOVER`, and the other Phase 3 same-day strategies once implemented.
- Sensitivity tests: Bollinger length, extension threshold, climax-volume threshold, hard-stop distance, time-stop length, and entry cutoff should be reviewed conservatively.
- Automatic rejection conditions: fee drag erases the gross bounce, strong-downtrend overrides dominate results, time stops trigger too often, or the strategy fails to flatten reliably by session close.

## 13. Overfitting Risk Review

- Parameters likely to overfit: extension threshold, climax-volume ratio, RSI extreme, time-stop length, and hard-stop distance.
- Future-information leak risks: using future bars in session VWAP, same-bar fill assumptions, or allowing the reversal confirmation to look past the current candle.
- Simplifications for honesty: long-only, one primary timeframe, no scaling, explicit same-day flattening, and one override path for strong downtrends.
- Parameters fixed before optimization: `20`-bar Bollinger band, `0.80 ATR` extension threshold, `1.50x` climax-volume ratio, `30/25` RSI oversold thresholds, `1.00 ATR` profit and stop distances, and the `6`-bar time stop.

## 14. Minimum Evidence Threshold

Before the strategy can move beyond `research-only`:

- minimum trade count: at least `40` combined out-of-sample trades across the approved dataset pack
- minimum acceptable out-of-sample behavior: positive net return after costs without depending on a single crash or panic episode
- maximum acceptable fee drag: fee plus slippage drag must stay materially below the median gross bounce
- minimum benchmark comparison bar: must beat `BUY_AND_HOLD` on drawdown-aware terms in at least one approved holdout without undercutting realism
- required paper-monitoring evidence: repeatable same-session flattening, no unsupported short behavior, and clear telemetry for override, target, time-stop, and hard-stop decisions
