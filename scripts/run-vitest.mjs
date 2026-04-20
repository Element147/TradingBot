import { mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDir, '..');
const frontendDir = join(repoRoot, 'frontend');
const runtimeDir = join(repoRoot, '.runtime');
const localStorageFile = join(runtimeDir, 'node-localstorage.sqlite');

mkdirSync(runtimeDir, { recursive: true });

const existingNodeOptions = process.env.NODE_OPTIONS?.trim();
const localStorageOption = `--localstorage-file=${localStorageFile}`;
const nodeOptions = existingNodeOptions
  ? `${existingNodeOptions} ${localStorageOption}`
  : localStorageOption;

const vitestCli = join(frontendDir, 'node_modules', 'vitest', 'vitest.mjs');
const vitestArgs = process.argv.slice(2);

const child = spawn(process.execPath, [vitestCli, ...vitestArgs], {
  cwd: frontendDir,
  stdio: 'inherit',
  env: {
    ...process.env,
    NODE_OPTIONS: nodeOptions,
  },
});

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }

  process.exit(code ?? 0);
});
