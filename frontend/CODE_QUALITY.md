# Frontend Code Quality

This file summarizes the frontend quality toolchain as it exists now.

## Tooling

- ESLint 9 for linting
- Prettier 3 for formatting
- Vitest 4 with React Testing Library and jsdom
- Husky plus lint-staged for pre-commit checks

## Common Commands

```powershell
npm run lint
npm run lint:fix
npm run format
npm run format:check
npm run test
npm run test:coverage
npm run build
```

## Current Expectations

- TypeScript and React code should pass ESLint.
- Formatting should stay consistent through Prettier.
- Tests run in jsdom with shared setup and browser API mocks.
- Pre-commit hooks help keep staged files linted and formatted before commit.

## Related Docs

- `frontend/README.md`
- `docs/guides/FRONTEND.md`
- `docs/guides/TESTING_AND_CONTRACTS.md`
