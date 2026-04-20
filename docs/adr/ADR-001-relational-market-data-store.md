# ADR-001: Relational Market-Data Store

- Date: 2026-03-26
- Status: Accepted
- Deciders: architecture, backend, and data-model owners
- Related: `ARCHITECTURE.md`, `docs/ROADMAP.md`, `TRADING_GUARDRAILS.md`, `docs/research/STRATEGY_AUDIT_PROTOCOL.md`

## Context

The historical data model originally depended on whole CSV blobs for uploads, imports, execution warm-up, and telemetry reconstruction. That approach became expensive as datasets grew and made range-aware reads, provenance tracking, and realistic query behavior harder than they needed to be.

## Decision

Adopt a normalized relational market-data model with three core tables:

1. `market_data_series`
2. `market_data_candle_segments`
3. `market_data_candles`

Keep the existing backtest dataset catalog as provenance-oriented metadata while moving execution and telemetry reads onto the normalized store.

## Why

- Range queries are cheaper and clearer than reparsing whole CSV blobs
- Coverage, overlap, and provenance can be tracked directly
- Backtest execution and telemetry can request only the data they need
- The model supports exact-timeframe reads and controlled rollups

## Consequences

- New uploads and completed imports should hydrate the normalized store during ingestion
- Runtime backtest and telemetry paths should prefer the normalized store
- Legacy CSV blobs can remain as compatibility data until the remaining fallback paths are retired
