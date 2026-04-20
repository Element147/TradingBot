# ADR-002: Market-Data Overlap, Deduplication, And Retention Policy

- Date: 2026-03-26
- Status: Accepted
- Deciders: architecture and data-model owners
- Related: `docs/adr/ADR-001-relational-market-data-store.md`, `ARCHITECTURE.md`, `TRADING_GUARDRAILS.md`

## Context

The relational market-data model defines where candles live, but it still needs explicit rules for overlapping imports, duplicate buckets, exact-timeframe reads, and long-term retention.

## Decision

Adopt an explicit segment-resolution policy with three tiers:

1. `EXACT_RAW`
2. `DERIVED_ROLLUP`
3. `BEST_AVAILABLE`

This policy keeps exact-timeframe queries possible while still allowing deterministic best-available reads for runtime use.

## Why

- Execution needs a clear best-available path
- Audits and replay need exact-timeframe reproducibility
- Duplicates and overlapping segments must be resolved without silent data loss
- Retention work must not destroy provenance

## Consequences

- Segment metadata must explain where returned candles came from
- Rollups must be explicit and deterministic
- Archived or duplicate data can be compressed or retired only when replay and provenance stay intact
