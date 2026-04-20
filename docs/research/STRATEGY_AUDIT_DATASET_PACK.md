# Strategy Audit Dataset Pack

Research reference for the frozen dataset bundle used by the strategy-audit workflow. This document defines the representative dataset pack that later catalog reruns must use unless the methodology itself changes.

Use this together with [`STRATEGY_AUDIT_PROTOCOL.md`](STRATEGY_AUDIT_PROTOCOL.md). The protocol defines how strategies are judged. This file defines which market samples are allowed in the current audit round.

## Purpose

Freeze one reproducible audit pack that covers both:

- liquid crypto-major research
- small-account-friendly equity or ETF research

This avoids cherry-picking one market family and keeps later strategy claims anchored to the same windows, costs, and holdout boundaries.

## Current Pack

### Dataset A: Crypto Majors 15m Audit Anchor

- dataset ID: `#12`
- dataset name: `Binance BTC/USDT +2 15m 2024-03-12 to 2026-03-12`
- checksum: `b93c95da97c05f4edf4d706b80d33fcfab752f4f4d6f11f003fa3aca2fe2d326`
- schema identity: `ohlcv-v1`
- provider: `Binance`
- market: `Binance spot`
- timeframe: `15m`
- symbols: `BTC/USDT`, `ETH/USDT`, `SOL/USDT`
- date window: `2024-03-12` to `2026-03-12`
- holdout split:
  - in-sample: `2024-03-12` to `2025-07-01`
  - out-of-sample: `2025-07-01` to `2026-03-13`

### Dataset B: US ETF Daily Small-Account Audit Pack

- dataset name: `US ETF Daily Small-Account Audit Pack`
- dataset file: `docs/audit-datasets/us-etf-daily-small-account-pack-2024-03-12-to-2026-03-12.csv`
- manifest file: `docs/audit-datasets/us-etf-daily-small-account-pack-2024-03-12-to-2026-03-12.manifest.json`
- checksum: `edbceabab40fecde762158a542dc70678e8c315942e5e572f128e8d2c023fedc`
- schema identity: `ohlcv-v1`
- provider provenance: `Stooq daily history CSV`
- market: `US listed ETFs`
- session template: `US_EQUITIES`
- timeframe: `1d`
- symbols: `SPY`, `QQQ`, `VTI`, `VT`
- date window: `2024-03-12` to `2026-03-12`
- row count: `2008`
- holdout split:
  - in-sample: `2024-03-12` to `2025-07-01`
  - out-of-sample: `2025-07-01` to `2026-03-12`

## Rebuild Procedure

The ETF pack can be rebuilt with:

```powershell
.\scripts\build-strategy-audit-equity-dataset.ps1 -Force
```

## Known Limits

- The ETF pack is daily-only and cannot honestly clear intraday ETF hypotheses.
- U.S. equities do not trade `24/7`, so session assumptions matter.
- Bearish equity research defaults to `sell to cash` unless a short-proxy path is stated explicitly.
- This pack is representative, not exhaustive.

## Change Control

If the audit pack changes, update:

- this document
- [`STRATEGY_AUDIT_PROTOCOL.md`](STRATEGY_AUDIT_PROTOCOL.md) if the methodology changes
- `PROJECT_STATUS.md` if the current strategy-audit posture changes
- `docs/ROADMAP.md` if the future scope changes
