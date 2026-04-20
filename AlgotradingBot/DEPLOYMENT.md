# Deployment Guide

## Current Local Container Runbook

Use the repository compose file with the explicit project name:

```powershell
docker compose --project-name algotradingbot -f .\compose.yaml up -d
docker compose --project-name algotradingbot -f .\compose.yaml ps
docker compose --project-name algotradingbot -f .\compose.yaml logs -f
docker compose --project-name algotradingbot -f .\compose.yaml down
```

## Expected Runtime Resources

Containers:

- `algotrading-postgres`
- `algotrading-app`

Named volumes:

- `algotradingbot_postgres_data`

## Related Run Modes

- `.\run.ps1`: PostgreSQL in Docker, backend and frontend locally
- `.\run-all.ps1`: app and PostgreSQL in Docker with frontend locally
- `compose.debug.yaml`: opt-in backend JDWP overlay for Docker mode

Use `docs/guides/LOCAL_DEV_DOCKER_MCP.md` for runtime, cleanup, and troubleshooting details.
