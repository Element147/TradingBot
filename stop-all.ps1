# ============================================
# STOP ALL - Backend + Frontend
# ============================================
# This script stops all running services

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptPath "scripts/powershell/Common.ps1")

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Stopping AlgoTrading Bot Services" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Set-Location $scriptPath
$repoPaths = Get-RepoPaths -ScriptPath $scriptPath
Initialize-RepoRuntime -RepoPaths $repoPaths
$null = Set-LocalDockerComposeEnvironment -RepoPaths $repoPaths
$backendPidFile = Get-PidFilePath -RepoPaths $repoPaths -Name "backend"
$frontendPidFile = Get-PidFilePath -RepoPaths $repoPaths -Name "frontend"
$composeArgs = Get-ComposeArgs -RepoPaths $repoPaths -IncludeDebug

Refresh-UserPath

# Stop Backend Docker Containers
Write-Host "Stopping Backend services..." -ForegroundColor Yellow
if (Test-DockerRunning) {
    docker compose @($composeArgs) down
    Write-Host "[OK] Backend services stopped" -ForegroundColor Green
} else {
    Write-Host "[SKIP] Docker not running - backend containers already stopped" -ForegroundColor DarkYellow
}

# Stop Frontend (kill npm processes)
Write-Host ""
Write-Host "Stopping Frontend dev server..." -ForegroundColor Yellow
Stop-FromPidFile -PidFile $frontendPidFile -Label "frontend"
Stop-FromPidFile -PidFile $backendPidFile -Label "backend"
Stop-ListeningProcess -Port 5173 -Label "frontend"
Stop-ListeningProcess -Port 8080 -Label "backend"
Remove-PidFile -PidFile $frontendPidFile
Remove-PidFile -PidFile $backendPidFile

Write-Host "[OK] Frontend dev server stopped" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "[OK] All services stopped!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
