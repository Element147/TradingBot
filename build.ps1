param()

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptPath "scripts/powershell/Common.ps1")

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building AlgoTrading Bot - Fast Dev Mode" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Set-Location $scriptPath
Refresh-UserPath
Write-JavaVersionSummary

Write-Host "[1/2] Building Backend (Spring Boot)..." -ForegroundColor Yellow
Push-Location AlgotradingBot
.\gradlew.bat clean build -x test
$backendResult = $LASTEXITCODE
Pop-Location

if ($backendResult -ne 0) {
    Write-Host "[X] Backend build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Backend build successful" -ForegroundColor Green

Write-Host ""
Write-Host "[2/2] Building Frontend (React)..." -ForegroundColor Yellow
if (-not (Test-NodeInstalled)) {
    Write-Host "[X] Node.js is not installed - cannot build frontend" -ForegroundColor Red
    exit 1
}

Push-Location frontend
cmd /c "npm run build"
$frontendResult = $LASTEXITCODE
Pop-Location

if ($frontendResult -ne 0) {
    Write-Host "[X] Frontend build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Frontend build successful" -ForegroundColor Green

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "[OK] Fast dev build completed" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
