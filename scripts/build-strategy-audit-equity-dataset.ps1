param(
    [string]$StartDate = "2024-03-12",
    [string]$EndDate = "2026-03-12",
    [string]$OutputDirectory = "docs/audit-datasets",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedOutputDirectory = Join-Path $repoRoot $OutputDirectory
if (-not (Test-Path $resolvedOutputDirectory)) {
    New-Item -ItemType Directory -Path $resolvedOutputDirectory | Out-Null
}

$datasetStem = "us-etf-daily-small-account-pack-$StartDate-to-$EndDate"
$csvPath = Join-Path $resolvedOutputDirectory "$datasetStem.csv"
$manifestPath = Join-Path $resolvedOutputDirectory "$datasetStem.manifest.json"

if (((Test-Path $csvPath) -or (Test-Path $manifestPath)) -and -not $Force) {
    throw "Output files already exist. Re-run with -Force to overwrite."
}

$symbols = @(
    @{
        Symbol = "SPY"
        Source = "https://stooq.com/q/d/l/?s=spy.us&i=d"
        Venue = "NYSE Arca"
    },
    @{
        Symbol = "QQQ"
        Source = "https://stooq.com/q/d/l/?s=qqq.us&i=d"
        Venue = "NASDAQ"
    },
    @{
        Symbol = "VTI"
        Source = "https://stooq.com/q/d/l/?s=vti.us&i=d"
        Venue = "NYSE Arca"
    },
    @{
        Symbol = "VT"
        Source = "https://stooq.com/q/d/l/?s=vt.us&i=d"
        Venue = "NYSE Arca"
    }
)

$start = [DateTime]::ParseExact($StartDate, "yyyy-MM-dd", $null)
$end = [DateTime]::ParseExact($EndDate, "yyyy-MM-dd", $null)

if ($end -lt $start) {
    throw "EndDate must be on or after StartDate."
}

$datasetRows = [System.Collections.Generic.List[object]]::new()
$sourceSummaries = [System.Collections.Generic.List[object]]::new()
$totalRows = 0

foreach ($entry in $symbols) {
    $response = Invoke-WebRequest -UseBasicParsing $entry.Source
    $rows = $response.Content | ConvertFrom-Csv
    $filteredRows = $rows |
        Where-Object {
            $rowDate = [DateTime]::ParseExact($_.Date, "yyyy-MM-dd", $null)
            $rowDate -ge $start -and $rowDate -le $end
        } |
        Sort-Object Date

    foreach ($row in $filteredRows) {
        $datasetRows.Add([PSCustomObject]@{
            timestamp = "{0}T00:00:00" -f $row.Date
            symbol = $entry.Symbol
            open = $row.Open
            high = $row.High
            low = $row.Low
            close = $row.Close
            volume = $row.Volume
        }) | Out-Null
    }

    $rowCount = @($filteredRows).Count
    $totalRows += $rowCount
    $sourceSummaries.Add([ordered]@{
        symbol = $entry.Symbol
        source = $entry.Source
        venue = $entry.Venue
        rowCount = $rowCount
        firstDate = if ($rowCount -gt 0) { $filteredRows[0].Date } else { $null }
        lastDate = if ($rowCount -gt 0) { $filteredRows[$rowCount - 1].Date } else { $null }
    }) | Out-Null
}

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)

$csvLines = [System.Collections.Generic.List[string]]::new()
$csvLines.Add("timestamp,symbol,open,high,low,close,volume")
foreach ($row in $datasetRows | Sort-Object timestamp, symbol) {
    $csvLines.Add((
        "{0},{1},{2},{3},{4},{5},{6}" -f
            $row.timestamp,
            $row.symbol,
            $row.open,
            $row.high,
            $row.low,
            $row.close,
            $row.volume
    ))
}

[System.IO.File]::WriteAllText($csvPath, ($csvLines -join [Environment]::NewLine), $utf8NoBom)
$checksum = (Get-FileHash -Path $csvPath -Algorithm SHA256).Hash.ToLowerInvariant()

$manifest = [ordered]@{
    datasetName = "US ETF Daily Small-Account Audit Pack"
    datasetFile = [IO.Path]::GetFileName($csvPath)
    checksumSha256 = $checksum
    schemaIdentity = "ohlcv-v1"
    assetClass = "ETF"
    timeframeCoverage = @("1d")
    provider = "Stooq daily history CSV"
    quoteCurrency = "USD"
    market = "US listed ETFs"
    sessionTemplate = "US_EQUITIES"
    timezone = "America/New_York"
    startDate = $StartDate
    endDate = $EndDate
    holdoutSplit = [ordered]@{
        inSampleStart = "2024-03-12"
        inSampleEnd = "2025-07-01"
        outOfSampleStart = "2025-07-01"
        outOfSampleEnd = "2026-03-12"
    }
    rowCount = $totalRows
    symbols = $symbols.Symbol
    sourceSeries = $sourceSummaries
    generation = [ordered]@{
        generatedAtUtc = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        generatedBy = "scripts/build-strategy-audit-equity-dataset.ps1"
        timestampConvention = "Daily bars are normalized to the session date at 00:00:00 in ohlcv-v1 CSV format."
    }
}

[System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 6), $utf8NoBom)

Write-Host "Wrote $csvPath"
Write-Host "Wrote $manifestPath"
Write-Host "Rows: $totalRows"
Write-Host "SHA256: $checksum"
