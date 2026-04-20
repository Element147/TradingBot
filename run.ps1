param(
    [switch]$DebugBackend,
    [int]$DebugPort = 5005,
    [switch]$SuspendBackend,
    [int]$BackendInitialHeapMb = 0,
    [int]$BackendMaxHeapMb = 0,
    [int]$BackendMaxMetaspaceMb = 0
)

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptPath "scripts/powershell/Common.ps1")

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting AlgoTrading Bot - Fast Dev Mode" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Set-Location $scriptPath
$repoPaths = Get-RepoPaths -ScriptPath $scriptPath
Initialize-RepoRuntime -RepoPaths $repoPaths
$authRuntimeConfig = Set-LocalDockerComposeEnvironment -RepoPaths $repoPaths
Refresh-UserPath
Write-JavaVersionSummary
Write-LocalAuthModeSummary -AuthRuntimeConfig $authRuntimeConfig
$composeArgs = Get-ComposeArgs -RepoPaths $repoPaths

$backendPidFile = Get-PidFilePath -RepoPaths $repoPaths -Name "backend"
$frontendPidFile = Get-PidFilePath -RepoPaths $repoPaths -Name "frontend"

function Cleanup-FastModeStartup {
    Write-Host ""
    Write-Host "[CLEANUP] Rolling back partial fast-mode startup..." -ForegroundColor Yellow

    Stop-FromPidFile -PidFile $frontendPidFile -Label "frontend"
    Stop-FromPidFile -PidFile $backendPidFile -Label "backend"
    Stop-ListeningProcess -Port 5173 -Label "frontend"
    Stop-ListeningProcess -Port 8080 -Label "backend"

    if (Test-DockerRunning) {
        docker compose @($composeArgs) stop postgres > $null 2>&1
        Write-Host "[OK] PostgreSQL container stopped after startup failure" -ForegroundColor Green
    }

    Remove-PidFile -PidFile $backendPidFile
    Remove-PidFile -PidFile $frontendPidFile
}

Write-Host "Checking Docker..." -ForegroundColor Yellow
if (-not (Test-DockerRunning)) {
    Write-Host "[X] Docker is not running!" -ForegroundColor Red
    Write-Host "Please start Docker Desktop and try again." -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] Docker is running" -ForegroundColor Green

Stop-FromPidFile -PidFile $frontendPidFile -Label "frontend"
Remove-PidFile -PidFile $frontendPidFile

Stop-FromPidFile -PidFile $backendPidFile -Label "backend"
Remove-PidFile -PidFile $backendPidFile

if (-not (Assert-PortAvailable -Port 8080 -Label "Backend")) {
    Write-Host "Run .\stop.ps1 or free port 8080 before starting fast mode." -ForegroundColor Yellow
    exit 1
}

if (-not (Assert-PortAvailable -Port 5173 -Label "Frontend")) {
    Write-Host "Run .\stop.ps1 or free port 5173 before starting fast mode." -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "[1/3] Starting PostgreSQL container..." -ForegroundColor Yellow
docker compose @($composeArgs) up -d postgres
if ($LASTEXITCODE -ne 0) {
    Write-Host "[X] Failed to start PostgreSQL container!" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] PostgreSQL container started" -ForegroundColor Green

Write-Host ""
Write-Host "Waiting for PostgreSQL to be healthy..." -ForegroundColor Yellow
if (-not (Wait-ContainerHealthy -ContainerName "algotrading-postgres" -MaxAttempts 30 -DelaySeconds 2)) {
    Write-Host "[X] PostgreSQL did not become healthy in time" -ForegroundColor Red
    Write-Host "Check logs with: docker compose --project-name algotradingbot -f AlgotradingBot/compose.yaml logs postgres" -ForegroundColor Yellow
    Cleanup-FastModeStartup
    exit 1
}
Write-Host "[OK] PostgreSQL is healthy" -ForegroundColor Green

Write-Host ""
Write-Host "[2/3] Starting Backend (local bootRun, reclaim-friendly mode)..." -ForegroundColor Yellow
$backendJvmSettings = Resolve-BackendJvmSettings `
    -InitialHeapMb $BackendInitialHeapMb `
    -MaxHeapMb $BackendMaxHeapMb `
    -MaxMetaspaceMb $BackendMaxMetaspaceMb
$backendEnvironment = Get-BackendRuntimeEnvironment `
    -RepoPaths $repoPaths `
    -InitialHeapMb $backendJvmSettings.InitialHeapMb `
    -MaxHeapMb $backendJvmSettings.MaxHeapMb `
    -MaxMetaspaceMb $backendJvmSettings.MaxMetaspaceMb
Write-Host ("      Backend JVM: {0} (host RAM detected: {1:N1} GB)" -f `
    $backendJvmSettings.JavaToolOptions, `
    ($backendJvmSettings.HostMemoryMb / 1024.0)) -ForegroundColor Gray
$backendCommand = ".\gradlew.bat --no-daemon bootRun"
if ($DebugBackend) {
    $debugSuspend = if ($SuspendBackend) { "y" } else { "n" }
    $backendCommand = ".\gradlew.bat --no-daemon bootRun -PbackendDebug=true -PbackendDebugPort=$DebugPort -PbackendDebugSuspend=$debugSuspend"
}
$backendProc = Start-DetachedPowerShellProcess `
    -WorkingDirectory (Join-Path $scriptPath "AlgotradingBot") `
    -Command $backendCommand `
    -PidFile $backendPidFile `
    -Environment $backendEnvironment
Write-Host "[OK] Backend process started (PID $($backendProc.Id))" -ForegroundColor Green
if ($DebugBackend) {
    Write-Host "      JVM debug port: $DebugPort" -ForegroundColor Gray
}

Write-Host ""
Write-Host "Waiting for backend to be ready..." -ForegroundColor Yellow
if (-not (Wait-HttpOk -Uri "http://localhost:8080/actuator/info" -MaxAttempts 45 -DelaySeconds 2)) {
    Write-Host "[X] Backend did not become healthy in time" -ForegroundColor Red
    Write-Host "Inspect the backend PowerShell window or logs in $($repoPaths.RuntimeLogDir)." -ForegroundColor Yellow
    Cleanup-FastModeStartup
    exit 1
}
Write-Host "[OK] Backend is ready" -ForegroundColor Green

Write-Host ""
Write-Host "[3/3] Starting Frontend dev server..." -ForegroundColor Yellow
if (-not (Test-NodeInstalled)) {
    Write-Host "[X] Node.js is not installed - cannot start the frontend" -ForegroundColor Red
    Cleanup-FastModeStartup
    exit 1
}

$frontendProc = Start-DetachedPowerShellProcess `
    -WorkingDirectory (Join-Path $scriptPath "frontend") `
    -Command "npm run dev" `
    -PidFile $frontendPidFile
Write-Host "[OK] Frontend process started (PID $($frontendProc.Id))" -ForegroundColor Green

Write-Host ""
Write-Host "Waiting for frontend to be ready..." -ForegroundColor Yellow
if (-not (Wait-HttpOk -Uri "http://localhost:5173" -MaxAttempts 30 -DelaySeconds 2)) {
    Write-Host "[X] Frontend did not become healthy in time" -ForegroundColor Red
    Write-Host "Inspect the frontend PowerShell window and retry after fixing the dev-server error." -ForegroundColor Yellow
    Cleanup-FastModeStartup
    exit 1
}
Write-Host "[OK] Frontend is ready" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "[OK] Fast dev stack started!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Access your application:" -ForegroundColor Cyan
Write-Host "  Frontend:  http://localhost:5173" -ForegroundColor White
Write-Host "  Backend:   http://localhost:8080" -ForegroundColor White
Write-Host "  API Docs:  http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host "  Backend logs: $($repoPaths.RuntimeLogDir)" -ForegroundColor White
if ($DebugBackend) {
    Write-Host "  Backend JDWP: localhost:$DebugPort" -ForegroundColor White
}
Write-Host ""
Write-Host "To stop all services, run: .\stop.ps1" -ForegroundColor Yellow
