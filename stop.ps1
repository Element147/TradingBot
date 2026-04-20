param()

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptPath "scripts/powershell/Common.ps1")

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Stopping AlgoTrading Bot - Fast Dev Mode" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Set-Location $scriptPath
$repoPaths = Get-RepoPaths -ScriptPath $scriptPath
Initialize-RepoRuntime -RepoPaths $repoPaths
$null = Set-LocalDockerComposeEnvironment -RepoPaths $repoPaths
$backendPidFile = Get-PidFilePath -RepoPaths $repoPaths -Name "backend"
$frontendPidFile = Get-PidFilePath -RepoPaths $repoPaths -Name "frontend"
$composeArgs = Get-ComposeArgs -RepoPaths $repoPaths

Write-Host "Stopping frontend..." -ForegroundColor Yellow
Stop-FromPidFile -PidFile $frontendPidFile -Label "frontend"

Write-Host "Stopping backend..." -ForegroundColor Yellow
Stop-FromPidFile -PidFile $backendPidFile -Label "backend"

Stop-ListeningProcess -Port 5173 -Label "frontend"
Stop-ListeningProcess -Port 8080 -Label "backend"

Write-Host "Stopping PostgreSQL container..." -ForegroundColor Yellow
if (Test-DockerRunning) {
    docker compose @($composeArgs) stop postgres > $null 2>&1
    Write-Host "[OK] PostgreSQL container stopped" -ForegroundColor Green
} else {
    Write-Host "[SKIP] Docker not running - could not stop PostgreSQL container" -ForegroundColor DarkYellow
}

Remove-PidFile -PidFile $backendPidFile
Remove-PidFile -PidFile $frontendPidFile

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "[OK] Fast dev stack stopped" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
