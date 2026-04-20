# API Testing Script for Phase 5
Write-Host "=== Testing REST API Endpoints ===" -ForegroundColor Cyan

# Test 1: Start Strategy
Write-Host "`n[Test 1] POST /api/strategy/start" -ForegroundColor Yellow
$startBody = @{
    initialBalance = 100
    pairs = @("BTC/USDT", "ETH/USDT")
    riskPerTrade = 0.02
    maxDrawdown = 0.25
} | ConvertTo-Json

try {
    $startResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/strategy/start" -Method POST -Body $startBody -ContentType "application/json"
    Write-Host "SUCCESS: Strategy started" -ForegroundColor Green
    Write-Host ($startResponse | ConvertTo-Json -Depth 3)
    $accountId = $startResponse.accountId
} catch {
    Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host $_.Exception.Response
}

# Test 2: Get Status
Write-Host "`n[Test 2] GET /api/strategy/status" -ForegroundColor Yellow
try {
    $statusResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/strategy/status" -Method GET
    Write-Host "SUCCESS: Status retrieved" -ForegroundColor Green
    Write-Host ($statusResponse | ConvertTo-Json -Depth 3)
} catch {
    Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 3: Get Trade History
Write-Host "`n[Test 3] GET /api/trades/history?limit=10" -ForegroundColor Yellow
try {
    $historyResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/trades/history?limit=10" -Method GET
    Write-Host "SUCCESS: Trade history retrieved" -ForegroundColor Green
    Write-Host "Number of trades: $($historyResponse.Count)"
    if ($historyResponse.Count -gt 0) {
        Write-Host ($historyResponse[0] | ConvertTo-Json -Depth 3)
    }
} catch {
    Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 4: Get Backtest Results
Write-Host "`n[Test 4] GET /api/backtest/results" -ForegroundColor Yellow
try {
    $backtestResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/backtest/results" -Method GET
    Write-Host "SUCCESS: Backtest results retrieved" -ForegroundColor Green
    Write-Host "Number of results: $($backtestResponse.Count)"
    if ($backtestResponse.Count -gt 0) {
        Write-Host ($backtestResponse[0] | ConvertTo-Json -Depth 3)
    }
} catch {
    Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 5: Stop Strategy
Write-Host "`n[Test 5] POST /api/strategy/stop" -ForegroundColor Yellow
try {
    $stopResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/strategy/stop" -Method POST
    Write-Host "SUCCESS: Strategy stopped" -ForegroundColor Green
    Write-Host ($stopResponse | ConvertTo-Json -Depth 3)
} catch {
    Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
}

# Test 6: Test Error Handling - Invalid Request
Write-Host "`n[Test 6] POST /api/strategy/start (invalid data)" -ForegroundColor Yellow
$invalidBody = @{
    initialBalance = -100
    pairs = @()
} | ConvertTo-Json

try {
    $errorResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/strategy/start" -Method POST -Body $invalidBody -ContentType "application/json"
    Write-Host "FAILED: Should have returned error but did not" -ForegroundColor Red
} catch {
    Write-Host "SUCCESS: Error handling works correctly" -ForegroundColor Green
    Write-Host "Status Code: $($_.Exception.Response.StatusCode.value__)"
}

Write-Host "`n=== API Testing Complete ===" -ForegroundColor Cyan
