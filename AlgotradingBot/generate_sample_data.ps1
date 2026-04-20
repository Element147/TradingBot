# Generate sample historical data for backtesting
$startDate = Get-Date "2024-01-01 00:00:00"
$btcPrice = 42150.50
$ethPrice = 2250.75
$rows = @()

# Add header
$rows += "timestamp,symbol,open,high,low,close,volume"

for ($day = 0; $day -lt 31; $day++) {
    for ($hour = 0; $hour -lt 24; $hour++) {
        $timestamp = $startDate.AddDays($day).AddHours($hour).ToString("yyyy-MM-ddTHH:mm:ss")
        
        # BTC data with realistic volatility
        $btcChange = (Get-Random -Minimum -1.5 -Maximum 1.5) / 100
        $btcPrice = $btcPrice * (1 + $btcChange)
        $btcOpen = $btcPrice
        $btcHigh = $btcPrice * (1 + (Get-Random -Minimum 0.1 -Maximum 0.8) / 100)
        $btcLow = $btcPrice * (1 - (Get-Random -Minimum 0.1 -Maximum 0.8) / 100)
        $btcClose = $btcPrice * (1 + (Get-Random -Minimum -0.5 -Maximum 0.5) / 100)
        $btcVolume = Get-Random -Minimum 800 -Maximum 2000
        
        $rows += "$timestamp,BTC/USDT,$($btcOpen.ToString('F2')),$($btcHigh.ToString('F2')),$($btcLow.ToString('F2')),$($btcClose.ToString('F2')),$($btcVolume.ToString('F2'))"
        
        # ETH data with realistic volatility
        $ethChange = (Get-Random -Minimum -2.0 -Maximum 2.0) / 100
        $ethPrice = $ethPrice * (1 + $ethChange)
        $ethOpen = $ethPrice
        $ethHigh = $ethPrice * (1 + (Get-Random -Minimum 0.1 -Maximum 1.0) / 100)
        $ethLow = $ethPrice * (1 - (Get-Random -Minimum 0.1 -Maximum 1.0) / 100)
        $ethClose = $ethPrice * (1 + (Get-Random -Minimum -0.6 -Maximum 0.6) / 100)
        $ethVolume = Get-Random -Minimum 5000 -Maximum 15000
        
        $rows += "$timestamp,ETH/USDT,$($ethOpen.ToString('F2')),$($ethHigh.ToString('F2')),$($ethLow.ToString('F2')),$($ethClose.ToString('F2')),$($ethVolume.ToString('F2'))"
    }
}

# Write to CSV
$rows | Out-File -FilePath "AlgotradingBot/src/main/resources/sample-btc-eth-data.csv" -Encoding UTF8

Write-Host "Generated $($rows.Count - 1) rows of data"
