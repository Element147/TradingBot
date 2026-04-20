# AlgoTrading Bot

AlgoTrading Bot is a local-first research workstation for building and reviewing trading ideas safely. It combines a Spring Boot backend, a React/Vite dashboard, a normalized market-data store, reproducible backtests, paper-trading workflows, and operator controls in one repository.

## What It Includes

- Historical backtesting with saved runs, trade history, equity curves, replay, compare, and export
- Strategy catalog and strategy configuration flows for research and paper-safe monitoring
- Market-data uploads and provider imports with persistent job tracking
- Paper-trading desk, forward-testing workspace, and live-monitoring surfaces
- Risk controls, circuit-breaker visibility, audit history, and exchange/profile settings
- Local scripts for fast development mode and Docker-backed full-stack mode

## Quick Start

Recommended local flow from the repo root:

```powershell
.\build.ps1
.\run.ps1
.\stop.ps1
```

Useful variants:

```powershell
.\run.ps1 -DebugBackend
.\run-all.ps1
.\security-scan.ps1
```

Local URLs:

- Frontend: `http://localhost:5173`
- Backend health: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

For detailed local setup, Docker, MCP, and command guidance, use [`docs/guides/LOCAL_DEVELOPMENT.md`](docs/guides/LOCAL_DEVELOPMENT.md).

## Safety

- Default mode is `test`.
- Paper workflows stay simulated.
- Live trading is not enabled by default.
- Backtests and paper results are research evidence, not proof of profitability.

Use [`TRADING_GUARDRAILS.md`](TRADING_GUARDRAILS.md) for the full safety baseline.

## Documentation Map

Start here for the current product and system shape:

- [`PRODUCT.md`](PRODUCT.md): what the software does, features, and operator workflows
- [`PROJECT_STATUS.md`](PROJECT_STATUS.md): current maturity, verified baseline, and known limits
- [`ARCHITECTURE.md`](ARCHITECTURE.md): backend, frontend, runtime, and data boundaries
- [`TRADING_GUARDRAILS.md`](TRADING_GUARDRAILS.md): safety defaults and research honesty rules
- [`docs/README.md`](docs/README.md): full documentation hub

Developer guides:

- [`docs/guides/LOCAL_DEVELOPMENT.md`](docs/guides/LOCAL_DEVELOPMENT.md)
- [`docs/guides/BACKEND.md`](docs/guides/BACKEND.md)
- [`docs/guides/FRONTEND.md`](docs/guides/FRONTEND.md)
- [`docs/guides/TESTING_AND_CONTRACTS.md`](docs/guides/TESTING_AND_CONTRACTS.md)
- [`docs/guides/MARKET_DATA.md`](docs/guides/MARKET_DATA.md)

Deeper references:

- [`docs/ROADMAP.md`](docs/ROADMAP.md): future-facing scope
- [`docs/research/`](docs/research): strategy audit methodology, datasets, audit summaries, and strategy specs
- [`docs/adr/`](docs/adr): durable architecture decisions
- [`.codex/agents/README.md`](.codex/agents/README.md): project-local Codex agents
