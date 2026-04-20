# Local Development

Use this guide for local setup, runtime scripts, Docker, Compose, Gradle wrapper usage, and MCP tooling.

## Recommended Flow

From the repo root:

```powershell
.\build.ps1
.\run.ps1
.\stop.ps1
```

Use `.\run-all.ps1` and `.\stop-all.ps1` when you want the Docker-backed full stack instead of a locally started backend.

## Runtime Modes

### Fast Dev Mode

`.\run.ps1`:

- stops tracked frontend and backend processes first
- starts PostgreSQL in Docker
- runs the backend locally
- runs the frontend locally
- auto-matches backend relaxed auth to frontend `VITE_DEV_AUTH_BYPASS` for local-only debugging
- waits for both services to be ready
- rolls back partial startup if a later stage fails

### Full-Stack Mode

`.\run-all.ps1`:

- stops tracked local processes first
- starts the backend and PostgreSQL in Docker
- runs the frontend locally
- auto-matches backend relaxed auth to frontend `VITE_DEV_AUTH_BYPASS` for local-only debugging
- waits for both services to be ready
- rolls back partial startup if startup fails

Important rule: never start a frontend or backend dev server until existing instances have been checked and stopped first.

## Common Commands

```powershell
.\build.ps1
.\run.ps1
.\stop.ps1
.\build-all.ps1
.\run-all.ps1
.\stop-all.ps1
.\security-scan.ps1
```

Useful variants:

```powershell
.\run.ps1 -DebugBackend
.\run.ps1 -DebugBackend -SuspendBackend
.\run-all.ps1 -DebugBackend
```

## Gradle Wrapper Notes

Run backend Gradle commands from `AlgotradingBot/` with `.\gradlew.bat`:

```powershell
cd AlgotradingBot
.\gradlew.bat javaMigrationAudit --no-daemon
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat bootRun
.\gradlew.bat migrateLegacyDatasets -PlegacyMigrationDryRun=true
.\gradlew.bat reconcileLegacyDatasets
```

Rules:

- use the wrapper, not a system `gradle`
- prefer the narrowest useful task first
- run `test` and `build` sequentially, not in parallel
- use `javaMigrationAudit` for JDK-sensitive or toolchain-sensitive backend work

## Ports And Paths

- Frontend: `5173`
- Backend: `8080`
- PostgreSQL: `5432`
- Logs and runtime state: `.runtime`
- PID tracking: `.pids`

The local JWT secret is stored in `.runtime/local-jwt-secret.txt` and reused across local restarts.
When `frontend/.env` enables `VITE_DEV_AUTH_BYPASS=true`, the PowerShell run scripts also set `ALGOTRADING_RELAXED_AUTH=true` so the local backend accepts the same debug posture. If you start the backend manually with `.\gradlew.bat bootRun`, set that env var yourself or turn frontend bypass off.

## Docker And Compose

- Compose project name: `algotradingbot`
- Named PostgreSQL volume: `algotradingbot_postgres_data`
- Compose file: `AlgotradingBot/compose.yaml`

Direct Compose example:

```powershell
$env:JWT_SECRET = (Get-Content .\.runtime\local-jwt-secret.txt -Raw).Trim()
docker compose --project-name algotradingbot -f .\AlgotradingBot\compose.yaml build algotrading-app
```

## MCP Tooling

Preferred Docker MCP set for this repo:

- `playwright`
- `context7`
- `database-server`
- `openapi-schema`
- `semgrep`
- `hoverfly-mcp-server`

Useful checks:

```powershell
docker mcp server ls
docker mcp config dump
docker mcp tools ls --format list
```

Conventions:

- Docker-visible workspace paths use `/C/Git/algotradingbot/...`
- Host callbacks use `host.docker.internal`
- Hoverfly state belongs under `/C/Git/algotradingbot/.runtime/hoverfly`
