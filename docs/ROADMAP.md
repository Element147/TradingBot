# Roadmap

## Principles

- Stay local-first
- Keep safety defaults conservative
- Prefer small, reversible changes
- Strengthen evidence before expanding capability
- Keep docs current and focused on present behavior or future scope

## Now

- Keep the documentation set lean and canonical
- Improve research review, comparison, and governance around backtests
- Extend operator alerting beyond the current in-app baseline where justified
- Keep the market-data import workflow reliable before expanding provider coverage

## Next

- Multi-channel notifications for paper-trading and operational incidents
- Additional contract hardening where backend surfaces need stronger generated frontend coverage
- More runtime validation and smoke automation around local and Docker entrypoints
- Incremental improvements to research reporting and comparison workflows

## Later

- Production-readiness automation for container health, startup order, and longer-running stability
- Optional live-readiness evaluation only after paper evidence, rollback paths, and guardrails are strong enough
- Opt-in experiments with preview-only Java features behind explicit non-default profiles
- Portfolio-level research tools once strategy evidence is broad enough to justify them
- Experiment scheduling and governance automation for bounded batch research
- Data-quality and venue-constraint intelligence for fail-closed realism checks

## Follow-On Scope

### Portfolio Research

- Simulate multi-strategy portfolios using the normalized market-data model
- Use only strategies with frozen, reproducible evidence as candidate sleeves
- Keep the first version research-only with no hidden execution path

### Experiment Scheduling

- Batch backtests, walk-forward studies, and bounded parameter sweeps
- Keep automated research visible, auditable, and reproducible
- Prevent silent parameter fishing through explicit sweep definitions and approval points

### Data Quality And Venue Constraints

- Add anomaly detection for candle coverage, price integrity, and session mismatches
- Add venue metadata such as lot size, tick size, minimum notional, and session rules
- Fail closed when the system cannot honestly decide whether a simulated trade is placeable

### Incident Automation

- Extend the current in-app incident baseline into routed notifications and acknowledgements
- Keep operators in the loop
- Reuse existing audit, telemetry, and async-monitor seams instead of inventing a separate subsystem
