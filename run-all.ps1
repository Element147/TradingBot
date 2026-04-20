param(
    [switch]$DebugBackend
)

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptPath "scripts/powershell/Common.ps1")

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting AlgoTrading Bot - Full Stack" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Set-Location $scriptPath
$repoPaths = Get-RepoPaths -ScriptPath $scriptPath
Initialize-RepoRuntime -RepoPaths $repoPaths
$authRuntimeConfig = Set-LocalDockerComposeEnvironment -RepoPaths $repoPaths
$backendPidFile = Get-PidFilePath -RepoPaths $repoPaths -Name "backend"
$frontendPidFile = Get-PidFilePath -RepoPaths $repoPaths -Name "frontend"

Refresh-UserPath
Write-JavaVersionSummary
Write-LocalAuthModeSummary -AuthRuntimeConfig $authRuntimeConfig
$composeArgs = Get-ComposeArgs -RepoPaths $repoPaths -IncludeDebug:$DebugBackend

function Cleanup-FullStackStartup {
    Write-Host ""
    Write-Host "[CLEANUP] Rolling back partial full-stack startup..." -ForegroundColor Yellow

    Stop-FromPidFile -PidFile $frontendPidFile -Label "frontend"
    Stop-FromPidFile -PidFile $backendPidFile -Label "backend"
    Stop-ListeningProcess -Port 5173 -Label "frontend"
    Stop-ListeningProcess -Port 8080 -Label "backend"

    if (Test-DockerRunning) {
        docker compose @($composeArgs) down > $null 2>&1
        Write-Host "[OK] Docker services stopped after startup failure" -ForegroundColor Green
    }

    Remove-PidFile -PidFile $frontendPidFile
    Remove-PidFile -PidFile $backendPidFile
}

# Check if Docker is running
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

if (-not (Assert-PortAvailable -Port 5173 -Label "Frontend")) {
    Write-Host "Run .\stop-all.ps1 or free port 5173 before starting the full stack." -ForegroundColor Yellow
    exit 1
}

if (-not (Assert-PortAvailable -Port 8080 -Label "Backend")) {
    Write-Host "Run .\stop-all.ps1 or .\stop.ps1 or free port 8080 before starting the full stack." -ForegroundColor Yellow
    exit 1
}

if (-not (Assert-PortAvailable -Port 5432 -Label "PostgreSQL")) {
    Write-Host "Run .\stop-all.ps1 or .\stop.ps1 or free port 5432 before starting the full stack." -ForegroundColor Yellow
    exit 1
}

# Start Backend with Docker Compose (includes PostgreSQL)
Write-Host ""
Write-Host "[1/2] Starting Backend + Database..." -ForegroundColor Yellow
docker compose @($composeArgs) up -d
$dockerResult = $LASTEXITCODE

if ($dockerResult -ne 0) {
    Write-Host "[X] Failed to start backend services!" -ForegroundColor Red
    Cleanup-FullStackStartup
    exit 1
}
Write-Host "[OK] Backend services started!" -ForegroundColor Green

# Wait for backend to be healthy
Write-Host ""
Write-Host "Waiting for backend to be ready..." -ForegroundColor Yellow
if (-not (Wait-HttpOk -Uri "http://localhost:8080/actuator/info" -MaxAttempts 30 -DelaySeconds 2)) {
    Write-Host "[X] Backend failed to start within timeout" -ForegroundColor Red
    Write-Host "Check logs with: docker compose --project-name algotradingbot -f AlgotradingBot/compose.yaml logs" -ForegroundColor Yellow
    Cleanup-FullStackStartup
    exit 1
}
Write-Host "[OK] Backend is ready!" -ForegroundColor Green

# Start Frontend Dev Server
Write-Host ""
Write-Host "[2/2] Starting Frontend Dev Server..." -ForegroundColor Yellow
if (-not (Test-NodeInstalled)) {
    Write-Host "[X] Node.js is not installed - cannot start the frontend" -ForegroundColor Red
    Cleanup-FullStackStartup
    exit 1
}

$frontendProc = Start-DetachedPowerShellProcess `
    -WorkingDirectory (Join-Path $scriptPath "frontend") `
    -Command "npm run dev" `
    -PidFile $frontendPidFile
Write-Host "[OK] Frontend dev server starting..." -ForegroundColor Green

Write-Host ""
Write-Host "Waiting for frontend to be ready..." -ForegroundColor Yellow
if (-not (Wait-HttpOk -Uri "http://localhost:5173" -MaxAttempts 30 -DelaySeconds 2)) {
    Write-Host "[X] Frontend failed to start within timeout" -ForegroundColor Red
    Write-Host "Inspect the frontend PowerShell window and retry after fixing the dev-server error." -ForegroundColor Yellow
    Cleanup-FullStackStartup
    exit 1
}
Write-Host "[OK] Frontend is ready!" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "[OK] All services started!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Access your application:" -ForegroundColor Cyan
Write-Host "  Frontend:  http://localhost:5173" -ForegroundColor White
Write-Host "  Backend:   http://localhost:8080" -ForegroundColor White
Write-Host "  API Docs:  http://localhost:8080/swagger-ui.html" -ForegroundColor White
if ($DebugBackend) {
    Write-Host "  Backend JDWP: localhost:5005" -ForegroundColor White
}
Write-Host ""
Write-Host "To stop all services, run: .\stop-all.ps1" -ForegroundColor Yellow
