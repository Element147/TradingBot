function Refresh-UserPath {
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")
}

function Get-RepoPaths {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScriptPath
    )

    $composeFile = Join-Path $ScriptPath "AlgotradingBot/compose.yaml"
    $composeDebugFile = Join-Path $ScriptPath "AlgotradingBot/compose.debug.yaml"
    $pidDir = Join-Path $ScriptPath ".pids"
    $runtimeDir = Join-Path $ScriptPath ".runtime"
    $runtimeLogDir = Join-Path $runtimeDir "logs"
    $hoverflyDataDir = Join-Path $runtimeDir "hoverfly"

    return @{
        ScriptPath = $ScriptPath
        ComposeFile = $composeFile
        ComposeDebugFile = $composeDebugFile
        PidDir = $pidDir
        RuntimeDir = $runtimeDir
        RuntimeLogDir = $runtimeLogDir
        HoverflyDataDir = $hoverflyDataDir
    }
}

function Get-ComposeArgs {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$RepoPaths,
        [switch]$IncludeDebug
    )

    $composeArgs = @("--project-name", "algotradingbot", "-f", $RepoPaths.ComposeFile)
    if ($IncludeDebug -and (Test-Path $RepoPaths.ComposeDebugFile)) {
        $composeArgs += @("-f", $RepoPaths.ComposeDebugFile)
    }

    return $composeArgs
}

function Ensure-Directory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    if (-not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
}

function Initialize-RepoRuntime {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$RepoPaths
    )

    Ensure-Directory $RepoPaths.PidDir
    Ensure-Directory $RepoPaths.RuntimeDir
    Ensure-Directory $RepoPaths.RuntimeLogDir
    Ensure-Directory $RepoPaths.HoverflyDataDir
}

function Get-PidFilePath {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$RepoPaths,
        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    return (Join-Path $RepoPaths.PidDir "$Name.pid")
}

function Test-DockerRunning {
    docker info > $null 2>&1
    return ($LASTEXITCODE -eq 0)
}

function Test-NodeInstalled {
    return ($null -ne (Get-Command node -ErrorAction SilentlyContinue))
}

function Get-JavaVersionSummary {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($null -eq $javaCommand) {
        return $null
    }

    $versionLine = (& java -version 2>&1 | Select-Object -First 1)
    if (-not $versionLine) {
        return "java detected but version could not be resolved"
    }

    return $versionLine.ToString().Trim()
}

function Write-JavaVersionSummary {
    $summary = Get-JavaVersionSummary
    if ($null -eq $summary) {
        Write-Host "[WARN] Java is not on PATH. Gradle toolchain resolution may still work if JAVA_HOME is set." -ForegroundColor DarkYellow
        return
    }

    Write-Host "[OK] Java detected: $summary" -ForegroundColor Green
}

function Assert-PortAvailable {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    try {
        $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($connection) {
            Write-Host "[X] $Label port $Port is already in use by PID $($connection.OwningProcess)." -ForegroundColor Red
            return $false
        }
    } catch {}

    return $true
}

function ConvertTo-SingleQuotedPowerShellString {
    param(
        [AllowNull()]
        [string]$Value
    )

    if ($null -eq $Value) {
        return "''"
    }

    return "'" + $Value.Replace("'", "''") + "'"
}

function Get-BackendLogEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$RepoPaths
    )

    $logPath = $RepoPaths.RuntimeLogDir
    return @{
        APP_LOG_PATH = $logPath
        APP_LOG_FILE = (Join-Path $logPath "algotrading-bot.log")
    }
}

function New-Base64Secret {
    param(
        [int]$ByteLength = 48
    )

    $bytes = New-Object byte[] $ByteLength
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function Get-LocalJwtSecret {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$RepoPaths
    )

    $secretPath = Join-Path $RepoPaths.RuntimeDir "local-jwt-secret.txt"
    if (Test-Path $secretPath) {
        $existingSecret = (Get-Content -Path $secretPath -Raw).Trim()
        if ($existingSecret) {
            return $existingSecret
        }
    }

    Ensure-Directory $RepoPaths.RuntimeDir
    $generatedSecret = New-Base64Secret
    Set-Content -Path $secretPath -Value $generatedSecret -NoNewline
    return $generatedSecret
}

function ConvertTo-BooleanFlag {
    param(
        [AllowNull()]
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    switch ($Value.Trim().ToLowerInvariant()) {
        "1" { return $true }
        "true" { return $true }
        "yes" { return $true }
        "on" { return $true }
        default { return $false }
    }
}

function Get-FrontendEnvironmentFiles {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$RepoPaths,
        [string]$Mode = "development"
    )

    $frontendDir = Join-Path $RepoPaths.ScriptPath "frontend"
    return @(
        (Join-Path $frontendDir ".env"),
        (Join-Path $frontendDir ".env.local"),
        (Join-Path $frontendDir ".env.$Mode"),
        (Join-Path $frontendDir ".env.$Mode.local")
    )
}

function Get-FrontendEnvironmentVariable {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$RepoPaths,
        [Parameter(Mandatory = $true)]
        [string]$Name,
        [string]$Mode = "development"
    )

    $resolvedValue = $null
    foreach ($envFile in Get-FrontendEnvironmentFiles -RepoPaths $RepoPaths -Mode $Mode) {
        if (-not (Test-Path $envFile)) {
            continue
        }

        foreach ($line in Get-Content $envFile) {
            $trimmedLine = $line.Trim()
            if (-not $trimmedLine -or $trimmedLine.StartsWith("#")) {
                continue
            }

            if ($trimmedLine -notmatch "^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$") {
                continue
            }

            $key = $matches[1]
            $value = $matches[2].Trim()
            if ($key -ne $Name) {
                continue
            }

            if ($value.Length -ge 2) {
                $startsWithDoubleQuote = $value.StartsWith('"')
                $endsWithDoubleQuote = $value.EndsWith('"')
                $startsWithSingleQuote = $value.StartsWith("'")
                $endsWithSingleQuote = $value.EndsWith("'")
                if (($startsWithDoubleQuote -and $endsWithDoubleQuote) -or ($startsWithSingleQuote -and $endsWithSingleQuote)) {
                    $value = $value.Substring(1, $value.Length - 2)
                }
            }

            $resolvedValue = $value
        }
    }

    return $resolvedValue
}

function Get-LocalAuthRuntimeConfig {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$RepoPaths
    )

    $frontendDevAuthBypassValue = Get-FrontendEnvironmentVariable `
        -RepoPaths $RepoPaths `
        -Name "VITE_DEV_AUTH_BYPASS"
    $frontendDevAuthBypassEnabled = ConvertTo-BooleanFlag $frontendDevAuthBypassValue

    $explicitRelaxedAuthValue = $env:ALGOTRADING_RELAXED_AUTH
    if ([string]::IsNullOrWhiteSpace($explicitRelaxedAuthValue)) {
        $relaxedAuthEnabled = $frontendDevAuthBypassEnabled
        $relaxedAuthSource = if ($frontendDevAuthBypassEnabled) {
            "auto-matched VITE_DEV_AUTH_BYPASS"
        } else {
            "default strict auth"
        }
    } else {
        $relaxedAuthEnabled = ConvertTo-BooleanFlag $explicitRelaxedAuthValue
        $relaxedAuthSource = "ALGOTRADING_RELAXED_AUTH override"
    }

    return @{
        FrontendDevAuthBypassEnabled = $frontendDevAuthBypassEnabled
        FrontendDevAuthBypassSource = if ($frontendDevAuthBypassValue -ne $null) {
            "frontend env"
        } else {
            "frontend env default"
        }
        RelaxedAuthEnabled = $relaxedAuthEnabled
        RelaxedAuthSource = $relaxedAuthSource
    }
}

function Set-LocalDockerComposeEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$RepoPaths
    )

    $env:JWT_SECRET = Get-LocalJwtSecret -RepoPaths $RepoPaths
    $authRuntimeConfig = Get-LocalAuthRuntimeConfig -RepoPaths $RepoPaths
    $env:ALGOTRADING_RELAXED_AUTH = if ($authRuntimeConfig.RelaxedAuthEnabled) { "true" } else { "false" }
    return $authRuntimeConfig
}

function Get-HostMemoryMb {
    try {
        $os = Get-CimInstance Win32_OperatingSystem -ErrorAction Stop
        if ($os -and $os.TotalVisibleMemorySize) {
            return [int][math]::Floor($os.TotalVisibleMemorySize / 1024)
        }
    } catch {}

    try {
        $computerInfo = Get-ComputerInfo -Property "CsTotalPhysicalMemory" -ErrorAction Stop
        if ($computerInfo.CsTotalPhysicalMemory) {
            return [int][math]::Floor($computerInfo.CsTotalPhysicalMemory / 1MB)
        }
    } catch {}

    return 8192
}

function Get-RecommendedBackendJvmSettings {
    param(
        [int]$HostMemoryMb = (Get-HostMemoryMb)
    )

    $maxHeapMb = if ($HostMemoryMb -ge 24576) {
        4096
    } elseif ($HostMemoryMb -ge 15360) {
        3072
    } elseif ($HostMemoryMb -ge 11264) {
        2560
    } else {
        2048
    }

    $initialHeapMb = if ($maxHeapMb -ge 3072) {
        1024
    } elseif ($maxHeapMb -ge 2560) {
        768
    } else {
        512
    }

    $maxMetaspaceMb = if ($maxHeapMb -ge 3072) {
        512
    } else {
        384
    }

    return @{
        HostMemoryMb = $HostMemoryMb
        InitialHeapMb = $initialHeapMb
        MaxHeapMb = $maxHeapMb
        MaxMetaspaceMb = $maxMetaspaceMb
    }
}

function Resolve-BackendJvmSettings {
    param(
        [int]$InitialHeapMb = 0,
        [int]$MaxHeapMb = 0,
        [int]$MaxMetaspaceMb = 0
    )

    $recommended = Get-RecommendedBackendJvmSettings

    if ($InitialHeapMb -le 0) {
        $InitialHeapMb = $recommended.InitialHeapMb
    }

    if ($MaxHeapMb -le 0) {
        $MaxHeapMb = $recommended.MaxHeapMb
    }

    if ($MaxMetaspaceMb -le 0) {
        $MaxMetaspaceMb = $recommended.MaxMetaspaceMb
    }

    if ($InitialHeapMb -gt $MaxHeapMb) {
        throw "BackendInitialHeapMb ($InitialHeapMb) cannot be greater than BackendMaxHeapMb ($MaxHeapMb)."
    }

    return @{
        HostMemoryMb = $recommended.HostMemoryMb
        InitialHeapMb = $InitialHeapMb
        MaxHeapMb = $MaxHeapMb
        MaxMetaspaceMb = $MaxMetaspaceMb
        JavaToolOptions = "-Xms${InitialHeapMb}m -Xmx${MaxHeapMb}m -XX:+UseZGC -XX:MaxMetaspaceSize=${MaxMetaspaceMb}m"
    }
}

function Get-BackendRuntimeEnvironment {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$RepoPaths,
        [int]$InitialHeapMb = 0,
        [int]$MaxHeapMb = 0,
        [int]$MaxMetaspaceMb = 0
    )

    $jvmSettings = Resolve-BackendJvmSettings `
        -InitialHeapMb $InitialHeapMb `
        -MaxHeapMb $MaxHeapMb `
        -MaxMetaspaceMb $MaxMetaspaceMb

    $authRuntimeConfig = Get-LocalAuthRuntimeConfig -RepoPaths $RepoPaths
    $environment = Get-BackendLogEnvironment $RepoPaths
    $environment["JWT_SECRET"] = Get-LocalJwtSecret -RepoPaths $RepoPaths
    $environment["ALGOTRADING_RELAXED_AUTH"] = if ($authRuntimeConfig.RelaxedAuthEnabled) { "true" } else { "false" }
    $environment["JAVA_TOOL_OPTIONS"] = $jvmSettings.JavaToolOptions

    return $environment
}

function Write-LocalAuthModeSummary {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$AuthRuntimeConfig
    )

    $frontendBypassStatus = if ($AuthRuntimeConfig.FrontendDevAuthBypassEnabled) { "enabled" } else { "disabled" }
    Write-Host "[OK] Frontend dev auth bypass: $frontendBypassStatus ($($AuthRuntimeConfig.FrontendDevAuthBypassSource))" -ForegroundColor Green

    if ($AuthRuntimeConfig.RelaxedAuthEnabled) {
        Write-Host "[WARN] Backend relaxed auth: enabled for local debugging ($($AuthRuntimeConfig.RelaxedAuthSource))" -ForegroundColor DarkYellow
    } else {
        Write-Host "[OK] Backend relaxed auth: disabled ($($AuthRuntimeConfig.RelaxedAuthSource))" -ForegroundColor Green
    }
}

function Wait-ContainerHealthy {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ContainerName,
        [int]$MaxAttempts = 30,
        [int]$DelaySeconds = 2
    )

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $health = docker inspect --format='{{.State.Health.Status}}' $ContainerName 2>$null
            if ($health -eq "healthy") {
                return $true
            }
        } catch {}

        Write-Host "  Attempt $attempt/$MaxAttempts..." -ForegroundColor Gray
        Start-Sleep -Seconds $DelaySeconds
    }

    return $false
}

function Wait-HttpOk {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [int]$MaxAttempts = 30,
        [int]$DelaySeconds = 2
    )

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
            if ($response.StatusCode -eq 200) {
                return $true
            }
        } catch {}

        Write-Host "  Attempt $attempt/$MaxAttempts..." -ForegroundColor Gray
        Start-Sleep -Seconds $DelaySeconds
    }

    return $false
}

function Build-EnvironmentPrefix {
    param(
        [hashtable]$Environment
    )

    if (-not $Environment -or $Environment.Count -eq 0) {
        return ""
    }

    $assignments = foreach ($key in $Environment.Keys) {
        "`$env:$key=" + (ConvertTo-SingleQuotedPowerShellString $Environment[$key])
    }

    return ($assignments -join "; ")
}

function Start-DetachedPowerShellProcess {
    param(
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory,
        [Parameter(Mandatory = $true)]
        [string]$Command,
        [string]$PidFile,
        [hashtable]$Environment
    )

    $fullCommandParts = @(
        "Set-Location " + (ConvertTo-SingleQuotedPowerShellString $WorkingDirectory)
    )

    $environmentPrefix = Build-EnvironmentPrefix $Environment
    if ($environmentPrefix) {
        $fullCommandParts += $environmentPrefix
    }

    $fullCommandParts += $Command
    $fullCommand = $fullCommandParts -join "; "

    $process = Start-Process powershell -ArgumentList @(
        "-NoProfile",
        "-NoExit",
        "-Command",
        $fullCommand
    ) -PassThru

    if ($PidFile) {
        Set-Content -Path $PidFile -Value $process.Id
    }

    return $process
}

function Stop-FromPidFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PidFile,
        [Parameter(Mandatory = $true)]
        [string]$Label
    )

    if (-not (Test-Path $PidFile)) {
        Write-Host "[SKIP] $Label PID file not found" -ForegroundColor DarkYellow
        return
    }

    $pidValue = Get-Content $PidFile -ErrorAction SilentlyContinue
    if (-not $pidValue) {
        Write-Host "[SKIP] $Label PID file is empty" -ForegroundColor DarkYellow
        return
    }

    try {
        $process = Get-Process -Id ([int]$pidValue) -ErrorAction Stop
        Stop-Process -Id $process.Id -Force
        Write-Host "[OK] Stopped $Label process (PID $($process.Id))" -ForegroundColor Green
    } catch {
        Write-Host "[SKIP] $Label process already stopped" -ForegroundColor DarkYellow
    }
}

function Remove-PidFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$PidFile
    )

    if (Test-Path $PidFile) {
        Remove-Item $PidFile -Force -ErrorAction SilentlyContinue
    }
}

function Stop-ListeningProcess {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [string]$Label = "process"
    )

    try {
        $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($connection) {
            Stop-Process -Id $connection.OwningProcess -Force -ErrorAction SilentlyContinue
            Write-Host "[OK] Stopped leftover $Label process on port $Port" -ForegroundColor Green
        }
    } catch {}
}
