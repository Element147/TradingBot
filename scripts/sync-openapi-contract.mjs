import { chmodSync, copyFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const repoRoot = dirname(__dirname);
const backendDir = join(repoRoot, 'AlgotradingBot');
const frontendDir = join(repoRoot, 'frontend');
const contractsDir = join(repoRoot, 'contracts');
const generatedDir = join(frontendDir, 'src', 'generated');
const buildSpec = join(backendDir, 'build', 'openapi', 'openapi.json');
const repoSpec = join(contractsDir, 'openapi.json');
const generatedTypes = join(generatedDir, 'openapi.d.ts');
const checkMode = process.argv.includes('--check');
const gradleWrapper = join(backendDir, 'gradlew');

const execCommand = (command, args, cwd) => {
  execFileSync(command, args, {
    cwd,
    stdio: 'inherit',
  });
};

mkdirSync(contractsDir, { recursive: true });
mkdirSync(generatedDir, { recursive: true });

const runGradleTask = (taskName) => {
  if (process.platform === 'win32') {
    execCommand('cmd.exe', ['/c', 'gradlew.bat', taskName, '--no-daemon'], backendDir);
    return;
  }

  try {
    chmodSync(gradleWrapper, 0o755);
  } catch {
    // Best-effort only; some environments may not allow mode changes.
  }

  try {
    execCommand('./gradlew', [taskName, '--no-daemon'], backendDir);
  } catch (error) {
    if (error?.code && error.code !== 'EACCES' && error.code !== 'ENOENT') {
      throw error;
    }
    execCommand('sh', ['./gradlew', taskName, '--no-daemon'], backendDir);
  }
};

const runOpenApiTypes = (inputSpec, outputTypes) => {
  if (process.platform === 'win32') {
    execCommand('cmd.exe', ['/c', 'npx', 'openapi-typescript', inputSpec, '-o', outputTypes], frontendDir);
  } else {
    execCommand('npx', ['openapi-typescript', inputSpec, '-o', outputTypes], frontendDir);
  }
};

runGradleTask('exportOpenApiContract');

if (!existsSync(buildSpec)) {
  throw new Error(`Expected OpenAPI contract at ${buildSpec}`);
}

if (checkMode) {
  const tempDir = mkdtempSync(join(tmpdir(), 'algotradingbot-openapi-'));

  try {
    const tempSpec = join(tempDir, 'openapi.json');
    const tempTypes = join(tempDir, 'openapi.d.ts');

    copyFileSync(buildSpec, tempSpec);
    runOpenApiTypes(tempSpec, tempTypes);

    const repoSpecMatches =
      existsSync(repoSpec) && readFileSync(repoSpec, 'utf8') === readFileSync(tempSpec, 'utf8');
    const generatedTypesMatch =
      existsSync(generatedTypes) && readFileSync(generatedTypes, 'utf8') === readFileSync(tempTypes, 'utf8');

    if (!repoSpecMatches || !generatedTypesMatch) {
      throw new Error('OpenAPI contract artifacts are out of date. Run `npm run contract:generate` in frontend.');
    }
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
} else {
  copyFileSync(buildSpec, repoSpec);
  runOpenApiTypes(repoSpec, generatedTypes);
}
