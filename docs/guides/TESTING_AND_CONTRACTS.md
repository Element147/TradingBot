# Testing And Contracts

Use this guide for verification, CI parity, generated contracts, and backend/frontend boundary work.

## Verification Strategy

1. Start with the narrowest useful check.
2. Expand only as far as the change requires.
3. If unrelated failures already exist, report them instead of hiding them.

## Frontend Checks

```powershell
cd frontend
npm run lint
npm run test -- --watch=false
npm run build
```

Use targeted Vitest execution first when that is enough.

## Backend Checks

```powershell
cd AlgotradingBot
.\gradlew.bat test
.\gradlew.bat build
```

Add:

```powershell
.\gradlew.bat javaMigrationAudit --no-daemon
```

when the change is toolchain-sensitive, JDK-sensitive, or migration-sensitive.

## OpenAPI Workflow

Tracked artifacts:

- `contracts/openapi.json`
- `frontend/src/generated/openapi.d.ts`

Typical check:

```powershell
cd frontend
npm run contract:check
```

If the backend API changed intentionally:

```powershell
cd frontend
npm run contract:generate
```

Rules:

- treat generated OpenAPI artifacts as the transport source of truth
- normalize transport shapes in API slices or transport helpers before they reach components
- keep component-facing models stable even when generated schemas are optional or noisy

## CI Order

Mirror this order locally when a change crosses the contract boundary:

- backend: `javaMigrationAudit`, `test`, `build`
- frontend: `contract:check`, `lint`, `test`, `build`

## Runtime Smoke Checks

Use runtime smoke checks when orchestration, Docker, env routing, or long-running async flows change:

```powershell
.\run.ps1
.\stop.ps1
.\run-all.ps1
.\stop-all.ps1
```

Prefer `.\run.ps1` when you need current local backend source. Use `.\run-all.ps1` when you specifically need the Docker-backed backend image.

## Performance And Profiling

Use these when work touches known hot paths:

```powershell
cd AlgotradingBot
.\gradlew.bat backendWorkflowProfile

cd ..\frontend
npm run profile:backtest
```

Reports land in:

- `AlgotradingBot/build/reports/backend-workflow-profile/report.md`
- `frontend/build/reports/backtest-page-profile/report.md`

## Security Scan Triggers

Run Semgrep when changing:

- auth or session handling
- secret or credential storage
- process execution or shelling out
- Docker, PowerShell, or orchestration code
- WebSocket or HTTP request parsing

Commands:

```powershell
.\security-scan.ps1
.\security-scan.ps1 -FailOnFindings
```
