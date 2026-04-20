# Frontend Setup

## Recommended Workflow

Use the repo-root scripts for normal development:

```powershell
.\build.ps1
.\run.ps1
.\stop.ps1
```

This keeps PostgreSQL, backend, frontend, ports, logs, and PID cleanup aligned with the rest of the project.

## Frontend-Only Commands

Run these from `frontend/` when you only need package-local work:

```powershell
npm install
npm run dev
npm run lint
npm run test -- --watch=false
npm run build
```

## Related Docs

- `frontend/README.md`
- `docs/guides/FRONTEND.md`
- `docs/guides/TESTING_AND_CONTRACTS.md`
