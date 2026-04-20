# Trading Guardrails

## Purpose

These rules define the repository's safety baseline for research, backtesting, paper trading, and any later live-readiness work. They take precedence over convenience.

## Safety Defaults

- No real-money trading by default
- No profitability claims without reproducible evidence
- No promotion of backtest or paper results as live outcomes
- No weakening of environment separation, risk controls, auditability, or operator override paths

## Environment Separation

Treat these modes as distinct:

- `test`: local development and research
- `paper`: simulated execution
- `live`: explicit live-connected context

Required behavior:

- Safe default to `test`
- Clear UI and API visibility of the active mode
- Separate config and credentials by mode
- Explicit confirmation before any live-sensitive action
- Unsupported live reads or writes must fail clearly instead of silently falling back
- Dev-only relaxed auth is for local debugging only and must not be part of the verified baseline

## Risk Baseline

Default operating posture:

- Max risk per trade: `2%` of equity
- Max aggregate open risk: `6%`
- Max drawdown stop: `25%`
- Cash buffer target: `20-30%`
- No leverage by default

If a control is not automated yet, it still remains an operator rule.

## Small-Account Posture

The default research posture is conservative:

- Prefer `long` or `sell to cash`
- Default bearish behavior is flat exposure, not direct shorting
- Direct short exposure in backtest or paper is opt-in and must stay explicit
- Live direct shorting, margin, and futures are outside the default product path
- Favor liquid instruments, low turnover, and no-latency assumptions
- Skip trades that require unrealistic lot sizes, minimum notionals, or unsupported venue behavior

## Guardrails And Overrides

The system should preserve:

- Strategy-level stop controls
- Account-level stop controls
- Environment-level kill-switch behavior
- Manual override with durable audit trail
- Clear UI visibility of breaker and override state

## Evidence Required Before Any Live Consideration

Any live-readiness discussion must be backed by reproducible evidence:

- versioned strategy parameters
- identifiable datasets with checksum or schema metadata
- explicit fees, slippage, timeframe handling, and fill assumptions
- out-of-sample or walk-forward validation
- enough trade count to interpret results honestly
- paper-trading soak time
- failure-mode testing for disconnects, restarts, and stale data
- rollback and replay paths

The frozen audit methodology lives in [`docs/research/STRATEGY_AUDIT_PROTOCOL.md`](docs/research/STRATEGY_AUDIT_PROTOCOL.md).

## Honest Reporting

Every strategy report should make these items explicit:

- dataset and date window
- instrument scope
- requested timeframe
- fee and slippage assumptions
- in-sample and out-of-sample split
- trade count, drawdown, Sharpe, profit factor, and win rate
- known failure modes and limitations

Never state or imply guaranteed returns.
