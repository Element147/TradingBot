# Generate sample BTC and ETH historical data
$outputFile = "src/main/resources/sample-btc-eth-data.csv"

# Initialize CSV
"timestamp,symbol,open,high,low,close,volume" | Out-File -FilePath $outputFile -Encoding UTF8

# Generate BTC data (30 days * 24 hours = 720 rows)
$startDate = Get-Date "2024-01-01 00:00:00"
$btcPrice = 42000.0
$random = New-Object System.Random

for ($i = 0; $i -lt 720; $i++) {
    $timestamp = $startDate.AddHours($i).ToString("yyyy-MM-ddTHH:mm:ss")
    
    # Add trend and volatility
    $trend = $i * 2.5
    $volatility = ($random.NextDouble() - 0.5) * 300
    $price = $btcPrice + $trend + $volatility
    
    $open = [math]::Round($price, 2)
    $high = [math]::Round($price + ($random.NextDouble() * 80 + 20), 2)
    $low = [math]::Round($price - ($random.NextDouble() * 80 + 20), 2)
    $close = [math]::Round($price + ($random.NextDouble() - 0.5) * 80, 2)
    
    # Ensure high is highest and low is lowest
    $high = [math]::Max($high, [math]::Max($open, $close))
    $low = [math]::Min($low, [math]::Min($open, $close))
    
    $volume = [math]::Round(1500 + ($random.NextDouble() - 0.5) * 1000, 1)
    
    "$timestamp,BTC/USDT,$open,$high,$low,$close,$volume" | Out-File -FilePath $outputFile -Append -Encoding UTF8
}

# Generate ETH data (30 days * 24 hours = 720 rows)
$ethPrice = 2250.0

for ($i = 0; $i -lt 720; $i++) {
    $timestamp = $startDate.AddHours($i).ToString("yyyy-MM-ddTHH:mm:ss")
    
    # Add trend and volatility
    $trend = $i * 0.15
    $volatility = ($random.NextDouble() - 0.5) * 20
    $price = $ethPrice + $trend + $volatility
    
    $open = [math]::Round($price, 2)
    $high = [math]::Round($price + ($random.NextDouble() * 5 + 2), 2)
    $low = [math]::Round($price - ($random.NextDouble() * 5 + 2), 2)
    $close = [math]::Round($price + ($random.NextDouble() - 0.5) * 6, 2)
    
    # Ensure high is highest and low is lowest
    $high = [math]::Max($high, [math]::Max($open, $close))
    $low = [math]::Min($low, [math]::Min($open, $close))
    
    $volume = [math]::Round(15000 + ($random.NextDouble() - 0.5) * 8000, 1)
    
    "$timestamp,ETH/USDT,$open,$high,$low,$close,$volume" | Out-File -FilePath $outputFile -Append -Encoding UTF8
}

Write-Host "Generated 720 BTC rows and 720 ETH rows"
Write-Host "Total: 1440 data points + 1 header = 1441 lines"
