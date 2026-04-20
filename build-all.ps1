# ============================================
# BUILD ALL - Backend + Frontend
# ============================================
# This script builds both the backend and frontend

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptPath "scripts/powershell/Common.ps1")

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building AlgoTrading Bot - Full Stack" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Set-Location $scriptPath

Refresh-UserPath
Write-JavaVersionSummary

# Build Backend
Write-Host "[1/2] Building Backend (Spring Boot)..." -ForegroundColor Yellow
Push-Location AlgotradingBot
.\gradlew.bat clean build -x test
$backendResult = $LASTEXITCODE
Pop-Location

if ($backendResult -ne 0) {
    Write-Host "[X] Backend build failed!" -ForegroundColor Red
    exit 1
}
Write-Host "[OK] Backend build successful!" -ForegroundColor Green

# Build Frontend
Write-Host ""
Write-Host "[2/2] Building Frontend (React)..." -ForegroundColor Yellow

if (-not (Test-NodeInstalled)) {
    Write-Host "[SKIP] Node.js not installed - skipping frontend build" -ForegroundColor Yellow
    Write-Host "       Install Node.js from https://nodejs.org/ to build frontend" -ForegroundColor Gray
} else {
    Push-Location frontend
    cmd /c "npm run build"
    $frontendResult = $LASTEXITCODE
    Pop-Location

    if ($frontendResult -ne 0) {
        Write-Host "[X] Frontend build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "[OK] Frontend build successful!" -ForegroundColor Green
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "[OK] All builds completed successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Run: .\run.ps1" -ForegroundColor White
Write-Host "  2. Access frontend: http://localhost:5173" -ForegroundColor White
Write-Host "  3. Access backend API: http://localhost:8080" -ForegroundColor White
