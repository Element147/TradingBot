# Strategy Spec: Opening Range VWAP Breakout v1

Related research references:

- `docs/research/STRATEGY_SPEC_TEMPLATE.md`
- `docs/research/STRATEGY_AUDIT_PROTOCOL.md`
- `TRADING_GUARDRAILS.md`

## 1. Strategy Identity

- Strategy name: Opening Range VWAP Breakout
- Version: v1
- Owner: Quant research
- Source posture: research candidate
- Status: implementation-ready

## 2. Hypothesis

- The opening range can capture the first meaningful price-discovery zone of the session, and breakouts that also stay above session VWAP with elevated participation may continue far enough to survive realistic costs.
- The edge is only plausible in liquid symbols where the opening impulse is tradeable and the first post-breakout pullback does not immediately negate the setup.
- The hypothesis is falsified if realistic fees, slippage, and session-close flattening erase the continuation edge or if the signal overtrades noisy opens without net expectancy.

## 3. Market Regime

- Allowed regimes: bullish or neutral intraday bias, non-bearish higher-timeframe regime, orderly volatility, same-session breakout conditions.
- Disabled regimes: explicit downtrend regime, already-late session, outsized ATR conditions, incomplete opening range, or weak participation after the breakout.
- Regime boundary: shared `StrategyFeatureLibrary` trend filter plus regime classifier and ATR-cap filter.

## 4. Universe

- Approved asset class: liquid ETFs first, liquid crypto majors as secondary research only.
- Approved symbols or universe definition: `SPY`, `QQQ`, `IWM` first; crypto majors like `BTC/USDT` and `ETH/USDT` only when the session assumption is explicitly documented in the dataset notes.
- Liquidity rules: high-dollar-volume names only, avoid thin small caps, avoid wide-spread instruments, require elevated breakout volume.
- Session rules: regular-session intraday use, same-day flattening only, no overnight hold.
- Minimum-order and fractionality assumptions: skip any order that would violate the small-account risk cap or minimum-notional rules in `TRADING_GUARDRAILS.md`.

## 5. Timeframe

- Primary signal timeframe: `15m`
- Higher-timeframe context: `20` and `50` bar EMA bias on the same intraday feed
- Lower-timeframe confirmation: none in v1
- Minimum candle history before valid signals: `51` bars

## 6. Signal Stack

- Setup condition: opening range from the first `4` session bars is complete, session is still in the allowed entry window, and the broader bias is not bearish.
- Entry trigger: close breaks above the opening-range high by at least `0.10 ATR`.
- Confirmation filters: price stays above session VWAP, breakout volume is at least `1.20x` the rolling `20`-bar average, trend filter is still healthy, and ATR percent stays below the cap.
- Stand-aside filters: after the last-entry cutoff, after the session cutoff, on bearish regime classification, or on oversized ATR conditions.
- Exit trigger: breakout fails back through the opening-range high, VWAP is lost, ATR or structural stop is hit, or the session cutoff forces flattening.

## 7. Long Behavior

- Enter long on the first confirmed breakout close that satisfies the full confirmation stack.
- The strategy is single-entry only in v1. No pyramiding, scaling in, or partial adds.
- Once in a position, it exits fully on any invalidation event rather than managing multiple partial states.

## 8. Bearish Behavior

- Bearish state behavior: sell to cash and stand aside.
- Direct shorting is not required in v1.
- No short proxy is assumed in the first implementation.

## 9. Risk Model

- Max risk per trade: `1.0%` of account as the planning baseline.
- Position-sizing method: capped volatility-managed allocation with a `0.35` floor and `0.60` cap in the first implementation.
- Max concurrent positions: `1`
- Stop model: `1.20 ATR` fail-safe stop, tightened by the higher of session VWAP or opening-range high once the breakout is active.
- Time stop: same-session mandatory flattening by the cutoff.
- Kill-switch or cooldown behavior: skip entries after the last-entry cutoff and skip trades when ATR percent breaches the cap.

Trades that would violate minimum-order size or small-account risk caps should be rejected by the broader execution and paper layers before promotion beyond research.

## 10. Exit Model

- Planned profit-taking behavior: no fixed target in v1; the strategy holds while the breakout remains valid.
- Planned loss-cutting behavior: exit immediately on breakout failure, VWAP loss, or protective-stop breach.
- Regime-break exit behavior: bearish regime or trend break invalidation routes to an exit.
- End-of-session or end-of-day flattening behavior: mandatory flatten at or after the session cutoff, with rollover protection on the next session's first bar.

## 11. Telemetry And Explainability

- Required indicator values: session VWAP, opening-range high and low, `20` EMA, `50` EMA, volume ratio, ATR, regime label.
- Required reason labels: breakout confirmed, breakout failed, VWAP lost, protective stop hit, session cutoff flatten.
- Required operator-facing notes: research-only status, liquid-session requirement, same-day flatten rule, and warning that the strategy has not passed the frozen audit yet.

## 12. Validation Plan

- Datasets to use: liquid ETF intraday pack first, then liquid crypto majors with documented session assumptions.
- Fee and slippage assumptions: default frozen protocol baseline of `10` bps fees and `3` bps slippage unless a stricter scenario is documented.
- In-sample / holdout rule: follow `docs/research/STRATEGY_AUDIT_PROTOCOL.md` with explicit warm-up-safe splits.
- Walk-forward expectation: anchored walk-forward bundle from the frozen audit method.
- Benchmark comparisons: `BUY_AND_HOLD`, `SMA_CROSSOVER`, and the strongest current research candidates where appropriate.
- Sensitivity tests: opening-range bar count, volume threshold, ATR cap, breakout buffer, and entry cutoff should be tested conservatively.
- Automatic rejection conditions: negative out-of-sample expectancy after costs, too-few trades, excessive false-breakout rate, or failure to flatten reliably by session close.

## 13. Overfitting Risk Review

- Parameters likely to overfit: opening-range length, breakout buffer, volume threshold, ATR cap, and cutoff times.
- Future-information leak risks: same-bar fill assumptions, session VWAP computed with future candles, or regime filters that peek beyond the current bar.
- Simplifications for honesty: no pyramiding, no shorting, one primary timeframe, one explicit entry path, and one mandatory session-flatten rule.
- Parameters fixed before optimization: `4` opening-range bars, `1.20x` volume ratio, `1.20 ATR` stop multiple, `0.10 ATR` breakout buffer, and the session cutoffs.

## 14. Minimum Evidence Threshold

Before the strategy can move beyond `research-only`:

- minimum trade count: at least `40` combined out-of-sample trades across the approved dataset pack
- minimum acceptable out-of-sample behavior: positive net return after costs with no catastrophic single-regime dependence
- maximum acceptable fee drag: fee plus slippage drag must stay materially below gross edge and not erase the median winner
- minimum benchmark comparison bar: must outperform `BUY_AND_HOLD` on risk-adjusted drawdown-aware terms in at least one approved holdout, without undercutting realism
- required paper-monitoring evidence: repeatable same-session flattening, no unsupported short behavior, and clear operator telemetry for entries, exits, and stand-aside decisions
