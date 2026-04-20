# Relative Strength Rotation With Intraday Entry Filter

## 1. Strategy Identity

- Strategy name: `Relative Strength Rotation With Intraday Entry Filter`
- Canonical ID: `RELATIVE_STRENGTH_ROTATION_INTRADAY_ENTRY_FILTER`
- Source posture: research candidate
- Status: `research only`

## 2. Hypothesis

Ranking a very small basket of liquid symbols can keep the low-turnover strength-allocation behavior that fits a small account, but entries should wait for a cleaner lower-timeframe confirmation instead of buying the leader immediately on rank alone.

## 3. Market Regime

- Preferred regime: bullish or neutral-to-bullish momentum phases
- Avoided regime: broad bearish tapes where absolute momentum fails across the basket
- Regime gate: absolute momentum must stay positive before any long exposure is allowed

## 4. Universe

- Approved liquid basket only:
- `SPY`
- `QQQ`
- `VTI`
- `VT`
- `BTC/USDT`
- `ETH/USDT`
- The strategy ranks only symbols from that approved list that are actually present in the dataset.
- If fewer than two approved symbols are present, the strategy stays inactive.

## 5. Timeframe

- Primary execution timeframe: `1h`
- Ranking horizon: multi-day relative momentum using `21` and `63` bar returns
- Timing horizon: fast `5` and `20` EMA plus short-horizon RSI confirmation

## 6. Signal Stack

### Ranking layer

- Compute a weighted relative-strength score from `21`-bar and `63`-bar returns.
- Select the top approved symbol only.
- Rotation requires the new leader to beat the active symbol by a small explicit score buffer.

### Absolute momentum gate

- Long exposure is allowed only when the candidate close is above the `200`-bar SMA and the `63`-bar return stays positive.
- If no approved symbol passes that gate, the strategy holds cash.

### Intraday entry filter

- The candidate must also trade above the `20` EMA.
- Entry trigger requires either:
- a reclaim above the `5` EMA after a brief weakness bar, or
- a breakout above the recent `5`-bar high.
- RSI (`5`) must confirm with bullish momentum.

## 7. Long Behavior

- When flat, buy only the top approved leader if both the absolute-momentum gate and intraday timing filter pass.
- When already long, stay with the current leader unless a new leader clears the same timing filter and beats the active score by the explicit buffer.

## 8. Bearish Behavior

- Default bearish action: `sell to cash`
- Direct shorting: not used
- Inverse proxy: not used in this baseline implementation
- If the active leader loses absolute momentum, the strategy exits to cash instead of implying a direct short.

## 9. Risk Model

- Volatility-adjusted allocation from recent realized volatility
- Minimum allocation floor to avoid tiny untradeable positions in research
- One active allocation at a time
- No leverage-first behavior

## 10. Exit Model

- Exit to cash when the active symbol loses the absolute-momentum gate.
- Exit to cash when the intraday timing structure breaks decisively below the `20` EMA with weak RSI.
- Rotate only when a new approved leader clears both the ranking and timing requirements.

## 11. Telemetry And Explainability

- Required overlays:
- `sma_200`
- `ema_20`
- `ema_5`
- `return_21`
- `return_63`
- `breakout_high_5`
- `rsi_5`
- Required reason labels: approved leader selected, absolute momentum positive, trigger EMA reclaimed or breakout confirmed, absolute momentum broke, intraday support failed, new leader confirmed, leader outside approved basket, timing filter not confirmed.
- Required operator-facing notes: research-only status, cash fallback instead of direct shorting, and warning that the strategy is not audit-cleared.

## 12. Validation Plan

- Verify the approved-universe filter rejects undefined broad-basket ranking.
- Test the ranking layer, absolute-momentum gate, intraday timing filter, and cash fallback separately.
- Use realistic fees and slippage.
- Review turnover so the timing filter reduces blunt rank-chasing rather than increasing churn.

## 13. Overfitting Review

- Keep the basket fixed and explicit.
- Avoid tuning dozens of symbols or unsupported bearish branches to make the backtest look better.
- Treat any strong result as a hypothesis until broader out-of-sample evidence exists.

## 14. Failure Conditions

- It over-rotates on small score changes.
- It only works because unsupported symbols or direct shorts are implied.
- The timing filter does not materially improve entry quality over the plain rotation baseline.
