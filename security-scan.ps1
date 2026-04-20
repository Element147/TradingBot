param(
    [switch]$FailOnFindings
)

$scriptPath = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptPath "scripts/powershell/Common.ps1")

Set-Location $scriptPath
Refresh-UserPath

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Semgrep Security Scan" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-DockerRunning)) {
    Write-Host "[X] Docker is not running!" -ForegroundColor Red
    Write-Host "Please start Docker Desktop and try again." -ForegroundColor Yellow
    exit 1
}

$semgrepArgs = @(
    "run",
    "--rm",
    "-v", "${scriptPath}:/src",
    "-w", "/src",
    "semgrep/semgrep",
    "semgrep",
    "scan",
    "--config", "p/default",
    "--metrics=off",
    "--exclude", ".git",
    "--exclude", ".runtime",
    "--exclude", ".idea",
    "--exclude", ".gradle",
    "--exclude", "frontend/dist",
    "--exclude", "frontend/node_modules",
    "--exclude", "frontend/coverage",
    "--exclude", "frontend/src/generated",
    "--exclude", "AlgotradingBot/build",
    "--exclude", "AlgotradingBot/.gradle"
)

if ($FailOnFindings) {
    $semgrepArgs += "--error"
}

Write-Host "Running Semgrep Community Edition via Docker..." -ForegroundColor Yellow
if (-not $FailOnFindings) {
    Write-Host "Findings are reported but do not fail the command unless -FailOnFindings is used." -ForegroundColor DarkYellow
}
docker @semgrepArgs
$scanResult = $LASTEXITCODE

if ($scanResult -ne 0) {
    Write-Host ""
    Write-Host "[X] Semgrep reported findings or scan errors." -ForegroundColor Red
    exit $scanResult
}

Write-Host ""
Write-Host "[OK] Semgrep scan completed." -ForegroundColor Green
